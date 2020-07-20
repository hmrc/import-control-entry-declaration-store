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

package uk.gov.hmrc.entrydeclarationstore.circuitbreaker

import akka.actor.{Actor, ActorRef, ActorRefFactory, Cancellable, Props, Stash, Status}
import akka.event.LoggingReceive
import akka.pattern.{CircuitBreakerOpenException, pipe}
import javax.inject.Inject
import play.api.libs.concurrent.InjectedActorSupport
import uk.gov.hmrc.entrydeclarationstore.circuitbreaker.CircuitBreakerActor._
import uk.gov.hmrc.entrydeclarationstore.models.CircuitBreakerState
import uk.gov.hmrc.entrydeclarationstore.repositories.CircuitBreakerRepo

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future, Promise, TimeoutException}
import scala.util.control.{NoStackTrace, NonFatal}
import scala.util.{Failure, Success, Try}

object CircuitBreakerActor {
  trait Factory {
    def apply(actorRefFactory: ActorRefFactory): ActorRef
  }

  class FactoryImpl @Inject()(circuitBreakerRepo: CircuitBreakerRepo, circuitBreakerConfig: CircuitBreakerConfig)
      extends Factory {
    override def apply(actorRefFactory: ActorRefFactory): ActorRef = {
      val stateActorFactory =
        CircuitBreakerStateActor.factory(circuitBreakerRepo, circuitBreakerConfig)

      actorRefFactory.actorOf(props(circuitBreakerConfig, stateActorFactory))
    }
  }

  def props(circuitBreakerConfig: CircuitBreakerConfig, stateActorFactory: CircuitBreakerStateActor.Factory): Props =
    Props(new CircuitBreakerActor(circuitBreakerConfig, stateActorFactory))

  sealed trait Command

  case class SetState(circuitBreakerState: CircuitBreakerState) extends Command

  case object GetInternalState extends Command

  sealed trait InternalState

  case class InternalClosed(numFailures: Int) extends InternalState
  case class InternalOpen(awaitingAck: Boolean) extends InternalState

  case object AcknowledgeOpen

  case class MakeCall[T](body: Unit => Future[T], defineFailureFn: Try[T] => Boolean) extends Command

  private[CircuitBreakerActor] case class InternalCallResult[T](
    result: Try[T],
    defineFailureFn: Try[T] => Boolean,
    timer: Cancellable) {
    def isFailure: Boolean = defineFailureFn(result)
  }

  case class CallResult[T](value: T)

  private val timeoutEx = new TimeoutException("Circuit Breaker Timed out.") with NoStackTrace
}

class CircuitBreakerActor(
  circuitBreakerConfig: CircuitBreakerConfig,
  stateActorFactory: CircuitBreakerStateActor.Factory)
    extends Actor
    with Stash
    with InjectedActorSupport {
  implicit val ec: ExecutionContext = context.dispatcher

  val stateActor: ActorRef = stateActorFactory(context)

  override def receive: Receive = initializing

  private def initializing: Receive = LoggingReceive.withLabel("initializing") {
    case CircuitBreakerActor.SetState(state) =>
      state match {
        case CircuitBreakerState.Closed => context.become(closed(0))
        case CircuitBreakerState.Open   => context.become(open(awaitingAck = false))
      }
      unstashAll()

    case _ => stash()
  }

  private def closed(numFailures: Int): Receive = LoggingReceive.withLabel(s"closed numFailures $numFailures") {

    case CircuitBreakerActor.SetState(CircuitBreakerState.Open) => context.become(open(awaitingAck = false))

    case MakeCall(body, defineFailureFn) =>
      val (timeoutFuture, timer) = withTimeout(body)

      timeoutFuture
        .map(t => InternalCallResult(Success(t), defineFailureFn, timer))
        .recover { case e => InternalCallResult(Failure(e), defineFailureFn, timer) }
        .pipeTo(self)(sender())

    case internalCallResult @ InternalCallResult(result, _, timer) =>
      timer.cancel()

      if (internalCallResult.isFailure) {
        if (numFailures + 1 >= circuitBreakerConfig.maxFailures) {
          context.become(open(awaitingAck = true))
          stateActor ! CircuitBreakerStateActor.UpdateDatabaseToOpen
        } else {
          context.become(closed(numFailures + 1))
        }
      } else {
        context.become(closed(0))
      }
      sendResult(result)

    case GetInternalState => sender() ! InternalClosed(numFailures)
  }

  private def sendResult[T](result: Try[T]): Unit =
    result match {
      case Success(t) => sender() ! CallResult(t)
      case Failure(e) => sender() ! Status.Failure(e)
    }

  private def open(awaitingAck: Boolean): Receive = LoggingReceive.withLabel(s"open awaitingAck=$awaitingAck") {
    case CircuitBreakerActor.SetState(CircuitBreakerState.Closed) =>
      if (!awaitingAck) context.become(closed(0))

    case AcknowledgeOpen => context.become(open(awaitingAck = false))

    case InternalCallResult(result, _, timer) =>
      timer.cancel()
      sendResult(result)

    case _: MakeCall[_] =>
      sender() ! Status.Failure(new CircuitBreakerOpenException(Duration.Zero))

    case GetInternalState => sender() ! InternalOpen(awaitingAck)
  }

  private def withTimeout[U](body: Unit => Future[U]): (Future[U], Cancellable) = {
    val timeoutPromise = Promise[Nothing]
    val timer = context.system.scheduler
      .scheduleOnce(circuitBreakerConfig.callTimeout)(timeoutPromise.failure(timeoutEx))

    val timeoutFuture = Future
      .firstCompletedOf(Seq(materialize(body), timeoutPromise.future))

    (timeoutFuture, timer)
  }

  private def materialize[U](body: Unit => Future[U]): Future[U] =
    try body(())
    catch { case NonFatal(t) => Future.failed(t) }
}
