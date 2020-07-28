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

import akka.actor.{Actor, ActorRef, ActorRefFactory, Props, Status, Timers}
import akka.event.LoggingReceive
import akka.pattern.pipe
import play.api.Logger
import uk.gov.hmrc.entrydeclarationstore.circuitbreaker.CircuitBreakerStateActor._
import uk.gov.hmrc.entrydeclarationstore.models.CircuitBreakerState
import uk.gov.hmrc.entrydeclarationstore.repositories.CircuitBreakerRepo

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try}

object CircuitBreakerStateActor {
  type Factory = ActorRefFactory => ActorRef

  def factory(circuitBreakerRepo: CircuitBreakerRepo, circuitBreakerConfig: CircuitBreakerConfig): Factory =
    actorRefFactory => actorRefFactory.actorOf(props(circuitBreakerRepo, circuitBreakerConfig))

  def props(circuitBreakerRepo: CircuitBreakerRepo, circuitBreakerConfig: CircuitBreakerConfig): Props =
    Props(new CircuitBreakerStateActor(circuitBreakerRepo, circuitBreakerConfig))

  private case object GetStateFromDatabase

  case object UpdateDatabaseToOpen

  private case object DatabaseUpdatedToOpen

  private object GetStateTimerKey

  private case class GetStateResult(result: Try[CircuitBreakerState])
}

class CircuitBreakerStateActor(circuitBreakerRepo: CircuitBreakerRepo, circuitBreakerConfig: CircuitBreakerConfig)
    extends Actor
    with Timers {

  implicit val ec: ExecutionContext = context.dispatcher

  self ! CircuitBreakerStateActor.GetStateFromDatabase

  var lastNotifiedState = Option.empty[CircuitBreakerState]

  override def receive: Receive = running

  private def running: Receive = LoggingReceive.withLabel(s"running") {
    case UpdateDatabaseToOpen =>
      circuitBreakerRepo
        .setCircuitBreakerState(CircuitBreakerState.Open)
        .map(_ => DatabaseUpdatedToOpen)
        .pipeTo(self)

      cancelStateRefresh()
      context.parent ! CircuitBreakerActor.AcknowledgeOpen
      context.become(updatingDatabaseToOpen)

    case GetStateFromDatabase =>
      circuitBreakerRepo.getCircuitBreakerState
        .map(state => GetStateResult(Success(state)))
        .recover { case e => GetStateResult(Failure(e)) }
        .pipeTo(self)

    case GetStateResult(Success(state)) =>
      notifyState(state)
    case GetStateResult(Failure(e)) =>
      Logger.warn("Unable to get mongo circuit breaker state", e)

      // Only update the state when parent is awaiting its initial state
      // otherwise try again
      lastNotifiedState match {
        case None        => notifyState(CircuitBreakerState.Closed)
        case Some(state) => refreshAndNotifyStateAfter(refreshPeriodFor(state))
      }
  }

  private def updatingDatabaseToOpen: Receive = LoggingReceive.withLabel("updatingDatabaseToOpen") {
    case DatabaseUpdatedToOpen =>
      refreshAndNotifyStateAfter(refreshPeriodFor(CircuitBreakerState.Open))
      context.become(running)

    case Status.Failure(e) =>
      Logger.warn("Unable to update mongo circuit breaker state to open", e)
      refreshAndNotifyStateAfter(refreshPeriodFor(CircuitBreakerState.Open))
      context.become(running)
  }

  private def notifyState(state: CircuitBreakerState): Unit = {
    context.parent ! CircuitBreakerActor.SetState(state)
    refreshAndNotifyStateAfter(refreshPeriodFor(state))
    lastNotifiedState = Some(state)
  }

  private def refreshAndNotifyStateAfter(period: FiniteDuration): Unit =
    timers.startSingleTimer(GetStateTimerKey, CircuitBreakerStateActor.GetStateFromDatabase, period)

  private def refreshPeriodFor(state: CircuitBreakerState) =
    state match {
      case CircuitBreakerState.Open   => circuitBreakerConfig.openStateRefreshPeriod
      case CircuitBreakerState.Closed => circuitBreakerConfig.closedStateRefreshPeriod
    }

  private def cancelStateRefresh(): Unit = timers.cancel(GetStateTimerKey)
}
