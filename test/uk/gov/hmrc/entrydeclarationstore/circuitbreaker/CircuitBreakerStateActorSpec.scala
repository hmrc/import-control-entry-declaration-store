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

import akka.actor.{ActorRef, ActorSystem, PoisonPill}
import akka.testkit.{TestKit, TestProbe}
import org.scalatest.BeforeAndAfterAll
import reactivemongo.core.errors.GenericDatabaseException
import uk.gov.hmrc.entrydeclarationstore.models.CircuitBreakerState
import uk.gov.hmrc.entrydeclarationstore.repositories.MockCircuitBreakerRepo
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.util.control.NoStackTrace

class CircuitBreakerStateActorSpec
    extends TestKit(ActorSystem("CircuitBreakerStateActorSpec"))
    with UnitSpec
    with BeforeAndAfterAll
    with MockCircuitBreakerRepo {
  override def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  val shortTime: FiniteDuration = 10.millis

  val config: CircuitBreakerConfig =
    CircuitBreakerConfig(5, 10.seconds, openStateRefreshPeriod = 1.minute, closedStateRefreshPeriod = 1.minute)

  val dbException: GenericDatabaseException = new GenericDatabaseException("abc", None) with NoStackTrace

  // Used to avoid mocks getting called after a test has finished...
  def stop(actorRef: ActorRef): Unit = {
    val probe = TestProbe()
    probe.watch(actorRef)
    actorRef ! PoisonPill
    probe.expectTerminated(actorRef)
  }

  "CircuitBreakerStateActor" when {
    "new" when {
      "database available" must {
        "get the state from mongo and tell the circuit breaker actor" in {
          val parentProbe = TestProbe()

          val state = CircuitBreakerState.Closed
          MockCircuitBreakerRepo.getCircuitBreakerState returns Future.successful(state)

          parentProbe.childActorOf(CircuitBreakerStateActor.props(mockCircuitBreakerRepo, config))

          parentProbe.expectMsg(CircuitBreakerActor.SetState(state))
        }
      }

      "database not available" must {
        "assume the circuit breaker is closed" in {
          val parentProbe = TestProbe()

          MockCircuitBreakerRepo.getCircuitBreakerState returns Future.failed(dbException)

          val stateActor = parentProbe.childActorOf(CircuitBreakerStateActor.props(mockCircuitBreakerRepo, config))

          parentProbe.expectMsg(CircuitBreakerActor.SetState(CircuitBreakerState.Closed))

          stop(stateActor)
        }

        "retry at next refresh period" in {
          val parentProbe = TestProbe()

          MockCircuitBreakerRepo.getCircuitBreakerState returns Future.failed(dbException)

          val state = CircuitBreakerState.Open
          MockCircuitBreakerRepo.getCircuitBreakerState returns Future.successful(state)

          val stateActor = parentProbe.childActorOf(
            CircuitBreakerStateActor.props(mockCircuitBreakerRepo, config.copy(closedStateRefreshPeriod = shortTime)))

          parentProbe.expectMsg(CircuitBreakerActor.SetState(CircuitBreakerState.Closed))
          parentProbe.expectMsg(CircuitBreakerActor.SetState(CircuitBreakerState.Open))

          stop(stateActor)
        }
      }
    }

    "initialized" when {
      "closed" must {
        "get the state from mongo according to the closed refresh period and tell the circuit breaker actor" in {
          val parentProbe = TestProbe()

          val state = CircuitBreakerState.Closed
          MockCircuitBreakerRepo.getCircuitBreakerState returns Future.successful(state) anyNumberOfTimes ()

          val stateActor = parentProbe.childActorOf(
            CircuitBreakerStateActor.props(mockCircuitBreakerRepo, config.copy(closedStateRefreshPeriod = shortTime)))

          parentProbe.expectMsg(CircuitBreakerActor.SetState(state))
          parentProbe.expectMsg(CircuitBreakerActor.SetState(state))
          parentProbe.expectMsg(CircuitBreakerActor.SetState(state))

          stop(stateActor)
        }
      }

      "open" must {
        "get the state from mongo according to the open refresh period and tell the circuit breaker actor" in {
          val parentProbe = TestProbe()

          val state = CircuitBreakerState.Open
          MockCircuitBreakerRepo.getCircuitBreakerState returns Future.successful(state) anyNumberOfTimes ()

          val stateActor = parentProbe.childActorOf(
            CircuitBreakerStateActor.props(mockCircuitBreakerRepo, config.copy(openStateRefreshPeriod = shortTime)))

          parentProbe.expectMsg(CircuitBreakerActor.SetState(state))
          parentProbe.expectMsg(CircuitBreakerActor.SetState(state))
          parentProbe.expectMsg(CircuitBreakerActor.SetState(state))

          stop(stateActor)
        }
      }

      "UpdateDatabaseToOpen is received" must {
        "update the state in mongo to be Open and use open openStateRefreshPeriod" in {
          val parentProbe = TestProbe()

          MockCircuitBreakerRepo.getCircuitBreakerState returns Future.successful(CircuitBreakerState.Closed)
          MockCircuitBreakerRepo.setCircuitBreakerState(CircuitBreakerState.Open) returns Future.successful(())
          MockCircuitBreakerRepo.getCircuitBreakerState returns Future.successful(CircuitBreakerState.Open) anyNumberOfTimes ()

          val stateActor = parentProbe.childActorOf(
            CircuitBreakerStateActor.props(mockCircuitBreakerRepo, config.copy(openStateRefreshPeriod = shortTime)))

          parentProbe.expectMsg(CircuitBreakerActor.SetState(CircuitBreakerState.Closed))

          stateActor ! CircuitBreakerStateActor.UpdateDatabaseToOpen
          parentProbe.expectMsg(CircuitBreakerActor.AcknowledgeOpen)

          parentProbe.expectMsg(CircuitBreakerActor.SetState(CircuitBreakerState.Open))
          parentProbe.expectMsg(CircuitBreakerActor.SetState(CircuitBreakerState.Open))

          stop(stateActor)
        }

        // otherwise could reset it to closed again...
        "not send further state updates until mongo update has completed" in {
          val parentProbe = TestProbe()

          MockCircuitBreakerRepo.getCircuitBreakerState returns Future.successful(CircuitBreakerState.Closed)

          val updatePromise = Promise[Unit]
          MockCircuitBreakerRepo.setCircuitBreakerState(CircuitBreakerState.Open) returns updatePromise.future
          MockCircuitBreakerRepo.getCircuitBreakerState returns Future.successful(CircuitBreakerState.Open) anyNumberOfTimes ()

          val stateActor = parentProbe.childActorOf(
            CircuitBreakerStateActor.props(
              mockCircuitBreakerRepo,
              config.copy(openStateRefreshPeriod = shortTime, closedStateRefreshPeriod = shortTime)))

          stateActor ! CircuitBreakerStateActor.UpdateDatabaseToOpen

          // The parent will ignore any in-flight SetState updates until it receives AcknowledgeOpen
          // but we must not send any more (even those relating to queries that are currently in progress)
          // until the DB has been updated
          parentProbe.fishForMessage() {
            case _: CircuitBreakerActor.SetState     => false
            case CircuitBreakerActor.AcknowledgeOpen => true
          }

          parentProbe.expectNoMessage(100.millis)

          updatePromise.success(())

          // Because of the way that the mocks work, we get the initial Closed
          // here if we didn't get the state at all before the UpdateDatabaseToOpen was received
          parentProbe.expectMsgAnyOf(
            CircuitBreakerActor.SetState(CircuitBreakerState.Open),
            CircuitBreakerActor.SetState(CircuitBreakerState.Closed))

          parentProbe.expectMsg(CircuitBreakerActor.SetState(CircuitBreakerState.Open))

          stop(stateActor)
        }

        // Otherwise will never be closed
        "continue to periodically send database state as normal if mongo update fails" in {
          val parentProbe = TestProbe()

          MockCircuitBreakerRepo.getCircuitBreakerState returns Future.successful(CircuitBreakerState.Closed) anyNumberOfTimes ()

          MockCircuitBreakerRepo.setCircuitBreakerState(CircuitBreakerState.Open) returns Future.failed(dbException)

          val stateActor = parentProbe.childActorOf(
            CircuitBreakerStateActor.props(
              mockCircuitBreakerRepo,
              config.copy(closedStateRefreshPeriod = shortTime, openStateRefreshPeriod = shortTime)))

          stateActor ! CircuitBreakerStateActor.UpdateDatabaseToOpen
          parentProbe.expectMsg(CircuitBreakerActor.AcknowledgeOpen)

          parentProbe.expectMsg(CircuitBreakerActor.SetState(CircuitBreakerState.Closed))
          parentProbe.expectMsg(CircuitBreakerActor.SetState(CircuitBreakerState.Closed))

          stop(stateActor)
        }
      }

      "database is down" must {
        "ignore update and retry at next refresh period" in {
          val parentProbe = TestProbe()

          // To initialize..
          MockCircuitBreakerRepo.getCircuitBreakerState returns Future.successful(CircuitBreakerState.Open)

          // Subsequently fail then recover...
          MockCircuitBreakerRepo.getCircuitBreakerState returns Future.failed(dbException)
          MockCircuitBreakerRepo.getCircuitBreakerState returns Future.successful(CircuitBreakerState.Closed)

          val stateActor = parentProbe.childActorOf(
            CircuitBreakerStateActor.props(mockCircuitBreakerRepo, config.copy(openStateRefreshPeriod = shortTime)))

          parentProbe.expectMsg(CircuitBreakerActor.SetState(CircuitBreakerState.Open))
          parentProbe.expectMsg(CircuitBreakerActor.SetState(CircuitBreakerState.Closed))

          stop(stateActor)
        }
      }
    }
  }
}
