/*
 * Copyright 2020 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.entrydeclarationstore.trafficswitch

import akka.actor.{Actor, ActorRef, ActorRefFactory, Cancellable, Props, Stash, Status}
import akka.event.LoggingReceive
import akka.pattern.{CircuitBreakerOpenException, pipe}
import javax.inject.Inject
import play.api.libs.concurrent.InjectedActorSupport
import uk.gov.hmrc.entrydeclarationstore.models.TrafficSwitchState
import uk.gov.hmrc.entrydeclarationstore.services.TrafficSwitchService
import uk.gov.hmrc.entrydeclarationstore.trafficswitch.TrafficSwitchActor._

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future, Promise, TimeoutException}
import scala.util.control.{NoStackTrace, NonFatal}
import scala.util.{Failure, Success, Try}

object TrafficSwitchActor {
  trait Factory {
    def apply(actorRefFactory: ActorRefFactory): ActorRef
  }

  class FactoryImpl @Inject()(trafficSwitchService: TrafficSwitchService, trafficSwitchConfig: TrafficSwitchConfig)
      extends Factory {
    override def apply(actorRefFactory: ActorRefFactory): ActorRef = {
      val stateActorFactory =
        TrafficSwitchStateActor.factory(trafficSwitchService, trafficSwitchConfig)

      actorRefFactory.actorOf(props(trafficSwitchConfig, stateActorFactory))
    }
  }

  def props(trafficSwitchConfig: TrafficSwitchConfig, stateActorFactory: TrafficSwitchStateActor.Factory): Props =
    Props(new TrafficSwitchActor(trafficSwitchConfig, stateActorFactory))

  sealed trait Command

  case class SetState(trafficFlowing: TrafficSwitchState) extends Command

  case object GetInternalState extends Command

  sealed trait InternalState

  case class InternalTrafficFlowing(numFailures: Int) extends InternalState
  case class InternalTrafficNotFlowing(awaitingAck: Boolean) extends InternalState

  case object AcknowledgeNotFlowing

  case class MakeCall[T](body: Unit => Future[T], defineFailureFn: Try[T] => Boolean) extends Command

  private[TrafficSwitchActor] case class InternalCallResult[T](
    result: Try[T],
    defineFailureFn: Try[T] => Boolean,
    timer: Cancellable) {
    def isFailure: Boolean = defineFailureFn(result)
  }

  case class CallResult[T](value: T)

  private val timeoutEx = new TimeoutException("Traffic Switch Timed out.") with NoStackTrace
}

class TrafficSwitchActor(trafficSwitchConfig: TrafficSwitchConfig, stateActorFactory: TrafficSwitchStateActor.Factory)
    extends Actor
    with Stash
    with InjectedActorSupport {
  implicit val ec: ExecutionContext = context.dispatcher

  val stateActor: ActorRef = stateActorFactory(context)

  override def receive: Receive = initializing

  private def initializing: Receive = LoggingReceive.withLabel("initializing") {
    case TrafficSwitchActor.SetState(state) =>
      state match {
        case TrafficSwitchState.Flowing    => context.become(flowing(0))
        case TrafficSwitchState.NotFlowing => context.become(notFlowing(awaitingAck = false))
      }
      unstashAll()

    case _ => stash()
  }

  private def flowing(numFailures: Int): Receive =
    LoggingReceive.withLabel(s"traffic flowing numFailures $numFailures") {

      case TrafficSwitchActor.SetState(TrafficSwitchState.NotFlowing) => context.become(notFlowing(awaitingAck = false))

      case MakeCall(body, defineFailureFn) =>
        val (timeoutFuture, timer) = withTimeout(body)

        timeoutFuture
          .map(t => InternalCallResult(Success(t), defineFailureFn, timer))
          .recover { case e => InternalCallResult(Failure(e), defineFailureFn, timer) }
          .pipeTo(self)(sender())

      case internalCallResult @ InternalCallResult(result, _, timer) =>
        timer.cancel()

        if (internalCallResult.isFailure) {
          if (numFailures + 1 >= trafficSwitchConfig.maxFailures) {
            context.become(notFlowing(awaitingAck = true))
            stateActor ! TrafficSwitchStateActor.UpdateDatabaseToNotFlowing
          } else {
            context.become(flowing(numFailures + 1))
          }
        } else {
          context.become(flowing(0))
        }
        sendResult(result)

      case GetInternalState => sender() ! InternalTrafficFlowing(numFailures)
    }

  private def sendResult[T](result: Try[T]): Unit =
    result match {
      case Success(t) => sender() ! CallResult(t)
      case Failure(e) => sender() ! Status.Failure(e)
    }

  private def notFlowing(awaitingAck: Boolean): Receive =
    LoggingReceive.withLabel(s"traffic not flowing awaitingAck=$awaitingAck") {
      case TrafficSwitchActor.SetState(TrafficSwitchState.Flowing) =>
        if (!awaitingAck) context.become(flowing(0))

      case AcknowledgeNotFlowing => context.become(notFlowing(awaitingAck = false))

      case InternalCallResult(result, _, timer) =>
        timer.cancel()
        sendResult(result)

      case _: MakeCall[_] =>
        sender() ! Status.Failure(new CircuitBreakerOpenException(Duration.Zero))

      case GetInternalState => sender() ! InternalTrafficNotFlowing(awaitingAck)
    }

  private def withTimeout[U](body: Unit => Future[U]): (Future[U], Cancellable) = {
    val timeoutPromise = Promise[Nothing]
    val timer = context.system.scheduler
      .scheduleOnce(trafficSwitchConfig.callTimeout)(timeoutPromise.failure(timeoutEx))

    val timeoutFuture = Future
      .firstCompletedOf(Seq(materialize(body), timeoutPromise.future))

    (timeoutFuture, timer)
  }

  private def materialize[U](body: Unit => Future[U]): Future[U] =
    try body(())
    catch { case NonFatal(t) => Future.failed(t) }
}
