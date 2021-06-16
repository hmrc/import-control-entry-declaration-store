/*
 * Copyright 2021 HM Revenue & Customs
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

import akka.actor.{Actor, ActorRef, ActorRefFactory, Props, Status, Timers}
import akka.event.LoggingReceive
import akka.pattern.pipe
import play.api.Logging
import uk.gov.hmrc.entrydeclarationstore.models.TrafficSwitchState
import uk.gov.hmrc.entrydeclarationstore.services.TrafficSwitchService
import uk.gov.hmrc.entrydeclarationstore.trafficswitch.TrafficSwitchStateActor._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try}

object TrafficSwitchStateActor {
  type Factory = ActorRefFactory => ActorRef

  def factory(trafficSwitchService: TrafficSwitchService, trafficSwitchConfig: TrafficSwitchConfig): Factory =
    actorRefFactory => actorRefFactory.actorOf(props(trafficSwitchService, trafficSwitchConfig))

  def props(trafficSwitchService: TrafficSwitchService, trafficSwitchConfig: TrafficSwitchConfig): Props =
    Props(new TrafficSwitchStateActor(trafficSwitchService, trafficSwitchConfig))

  private case object GetStateFromDatabase

  case object UpdateDatabaseToNotFlowing

  private case object DatabaseUpdatedToNotFlowing

  private object GetStateTimerKey

  private case class GetStateResult(result: Try[TrafficSwitchState])
}

class TrafficSwitchStateActor(trafficSwitchService: TrafficSwitchService, trafficSwitchConfig: TrafficSwitchConfig)
    extends Actor
    with Timers with Logging {

  implicit val ec: ExecutionContext = context.dispatcher

  self ! TrafficSwitchStateActor.GetStateFromDatabase

  var lastNotifiedState = Option.empty[TrafficSwitchState]

  override def receive: Receive = running

  private def running: Receive = LoggingReceive.withLabel(s"running") {
    case UpdateDatabaseToNotFlowing =>
      trafficSwitchService
        .stopTrafficFlow
        .map(_ => DatabaseUpdatedToNotFlowing)
        .pipeTo(self)

      cancelStateRefresh()
      context.parent ! TrafficSwitchActor.AcknowledgeNotFlowing
      context.become(updatingDatabaseToNotFlowing)

    case GetStateFromDatabase =>
      trafficSwitchService.getTrafficSwitchState
        .map(state => GetStateResult(Success(state)))
        .recover { case e => GetStateResult(Failure(e)) }
        .pipeTo(self)

    case GetStateResult(Success(state)) =>
      notifyState(state)
    case GetStateResult(Failure(e)) =>
      logger.warn("Unable to get mongo traffic switch state", e)

      // Only update the state when parent is awaiting its initial state
      // otherwise try again
      lastNotifiedState match {
        case None        => notifyState(TrafficSwitchState.Flowing)
        case Some(state) => refreshAndNotifyStateAfter(refreshPeriodFor(state))
      }
  }

  private def updatingDatabaseToNotFlowing: Receive = LoggingReceive.withLabel("updatingDatabaseToNotFlowing") {
    case DatabaseUpdatedToNotFlowing =>
      refreshAndNotifyStateAfter(refreshPeriodFor(TrafficSwitchState.NotFlowing))
      context.become(running)

    case Status.Failure(e) =>
      logger.warn("Unable to update mongo traffic switch state to not flowing", e)
      refreshAndNotifyStateAfter(refreshPeriodFor(TrafficSwitchState.NotFlowing))
      context.become(running)
  }

  private def notifyState(state: TrafficSwitchState): Unit = {
    context.parent ! TrafficSwitchActor.SetState(state)
    refreshAndNotifyStateAfter(refreshPeriodFor(state))
    lastNotifiedState = Some(state)
  }

  private def refreshAndNotifyStateAfter(period: FiniteDuration): Unit =
    timers.startSingleTimer(GetStateTimerKey, TrafficSwitchStateActor.GetStateFromDatabase, period)

  private def refreshPeriodFor(state: TrafficSwitchState) =
    state match{
      case TrafficSwitchState.Flowing => trafficSwitchConfig.flowingStateRefreshPeriod
      case TrafficSwitchState.NotFlowing => trafficSwitchConfig.notFlowingStateRefreshPeriod
    }

  private def cancelStateRefresh(): Unit = timers.cancel(GetStateTimerKey)
}
