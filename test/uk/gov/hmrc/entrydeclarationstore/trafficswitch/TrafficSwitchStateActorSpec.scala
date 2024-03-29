/*
 * Copyright 2023 HM Revenue & Customs
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

import org.apache.pekko.actor.{ActorRef, ActorSystem, PoisonPill}
import org.apache.pekko.testkit.{TestKit, TestProbe}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike
import uk.gov.hmrc.entrydeclarationstore.models.TrafficSwitchState
import uk.gov.hmrc.entrydeclarationstore.services.MockTrafficSwitchService

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.util.control.NoStackTrace

class TrafficSwitchStateActorSpec
    extends TestKit(ActorSystem("TrafficSwitchStateActorSpec"))
    with AnyWordSpecLike
    with BeforeAndAfterAll
    with MockTrafficSwitchService {
  override def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  val shortTime: FiniteDuration = 10.millis

  val config: TrafficSwitchConfig =
    TrafficSwitchConfig(5, 10.seconds, notFlowingStateRefreshPeriod = 1.minute, flowingStateRefreshPeriod = 1.minute)

  val dbException: Exception = new Exception("abc") with NoStackTrace

  // Used to avoid mocks getting called after a test has finished...
  def stop(actorRef: ActorRef): Unit = {
    val probe = TestProbe()
    probe.watch(actorRef)
    actorRef ! PoisonPill
    probe.expectTerminated(actorRef)
  }

  "TrafficSwitchActor" when {
    "new" when {
      "database available" must {
        "get the state from mongo and tell the traffic switch actor" in {
          val parentProbe = TestProbe()

          val state = TrafficSwitchState.Flowing
          MockTrafficSwitchService.getTrafficSwitchState returns Future.successful(state)

          parentProbe.childActorOf(TrafficSwitchStateActor.props(mockTrafficSwitchService, config))

          parentProbe.expectMsg(TrafficSwitchActor.SetState(state))
        }
      }

      "database not available" must {
        "assume the traffic switch is flowing" in {
          val parentProbe = TestProbe()

          MockTrafficSwitchService.getTrafficSwitchState returns Future.failed(dbException)

          val stateActor = parentProbe.childActorOf(TrafficSwitchStateActor.props(mockTrafficSwitchService, config))

          parentProbe.expectMsg(TrafficSwitchActor.SetState(TrafficSwitchState.Flowing))

          stop(stateActor)
        }

        "retry at next refresh period" in {
          val parentProbe = TestProbe()

          MockTrafficSwitchService.getTrafficSwitchState returns Future.failed(dbException)

          val state = TrafficSwitchState.NotFlowing
          MockTrafficSwitchService.getTrafficSwitchState returns Future.successful(state)

          val stateActor = parentProbe.childActorOf(
            TrafficSwitchStateActor.props(mockTrafficSwitchService, config.copy(flowingStateRefreshPeriod = shortTime)))

          parentProbe.expectMsg(TrafficSwitchActor.SetState(TrafficSwitchState.Flowing))
          parentProbe.expectMsg(TrafficSwitchActor.SetState(TrafficSwitchState.NotFlowing))

          stop(stateActor)
        }
      }
    }

    "initialized" when {
      "flowing" must {
        "get the state from mongo according to the flowing refresh period and tell the traffic switch actor" in {
          val parentProbe = TestProbe()

          val state = TrafficSwitchState.Flowing
          MockTrafficSwitchService.getTrafficSwitchState.returns(Future.successful(state)).anyNumberOfTimes()

          val stateActor = parentProbe.childActorOf(
            TrafficSwitchStateActor.props(mockTrafficSwitchService, config.copy(flowingStateRefreshPeriod = shortTime)))

          parentProbe.expectMsg(TrafficSwitchActor.SetState(state))
          parentProbe.expectMsg(TrafficSwitchActor.SetState(state))
          parentProbe.expectMsg(TrafficSwitchActor.SetState(state))

          stop(stateActor)
        }
      }

      "not flowing" must {
        "get the state from mongo according to the not flowing refresh period and tell the circuit breaker actor" in {
          val parentProbe = TestProbe()

          val state = TrafficSwitchState.NotFlowing
          MockTrafficSwitchService.getTrafficSwitchState.returns(Future.successful(state)).anyNumberOfTimes()

          val stateActor = parentProbe.childActorOf(
            TrafficSwitchStateActor.props(mockTrafficSwitchService, config.copy(notFlowingStateRefreshPeriod = shortTime)))

          parentProbe.expectMsg(TrafficSwitchActor.SetState(state))
          parentProbe.expectMsg(TrafficSwitchActor.SetState(state))
          parentProbe.expectMsg(TrafficSwitchActor.SetState(state))

          stop(stateActor)
        }
      }

      "UpdateDatabaseToFlowing is received" must {
        "update the state in mongo to be not flowing and use notFlowingStateRefreshPeriod" in {
          val parentProbe = TestProbe()

          MockTrafficSwitchService.getTrafficSwitchState returns Future.successful(TrafficSwitchState.Flowing)
          MockTrafficSwitchService.stopTrafficFlow returns Future.successful(())
          MockTrafficSwitchService.getTrafficSwitchState.returns(Future.successful(TrafficSwitchState.NotFlowing)).anyNumberOfTimes()

          val stateActor = parentProbe.childActorOf(
            TrafficSwitchStateActor.props(mockTrafficSwitchService, config.copy(notFlowingStateRefreshPeriod = shortTime)))

          parentProbe.expectMsg(TrafficSwitchActor.SetState(TrafficSwitchState.Flowing))

          stateActor ! TrafficSwitchStateActor.UpdateDatabaseToNotFlowing
          parentProbe.expectMsg(TrafficSwitchActor.AcknowledgeNotFlowing)

          parentProbe.expectMsg(TrafficSwitchActor.SetState(TrafficSwitchState.NotFlowing))
          parentProbe.expectMsg(TrafficSwitchActor.SetState(TrafficSwitchState.NotFlowing))

          stop(stateActor)
        }

        // otherwise could reset it to flowing again...
        "not send further state updates until mongo update has completed" in {
          val parentProbe = TestProbe()

          MockTrafficSwitchService.getTrafficSwitchState returns Future.successful(TrafficSwitchState.Flowing)

          val updatePromise = Promise[Unit]()
          MockTrafficSwitchService.stopTrafficFlow returns updatePromise.future
          MockTrafficSwitchService.getTrafficSwitchState.returns(Future.successful(TrafficSwitchState.NotFlowing)).anyNumberOfTimes ()

          val stateActor = parentProbe.childActorOf(
            TrafficSwitchStateActor.props(
              mockTrafficSwitchService,
              config.copy(notFlowingStateRefreshPeriod = shortTime, flowingStateRefreshPeriod = shortTime)))

          stateActor ! TrafficSwitchStateActor.UpdateDatabaseToNotFlowing

          // The parent will ignore any in-flight SetState updates until it receives AcknowledgeNotFlowing
          // but we must not send any more (even those relating to queries that are currently in progress)
          // until the DB has been updated
          parentProbe.fishForMessage() {
            case _: TrafficSwitchActor.SetState           => false
            case TrafficSwitchActor.AcknowledgeNotFlowing => true
          }

          parentProbe.expectNoMessage(100.millis)

          updatePromise.success(())

          // Because of the way that the mocks work, we get the initial TrafficSwitchState.Flowing
          // here if we didn't get the state at all before the UpdateDatabaseToNotFlowing was received
          parentProbe.expectMsgAnyOf(
            TrafficSwitchActor.SetState(TrafficSwitchState.NotFlowing),
            TrafficSwitchActor.SetState(TrafficSwitchState.Flowing))

          parentProbe.expectMsg(TrafficSwitchActor.SetState(TrafficSwitchState.NotFlowing))

          stop(stateActor)
        }

        // Otherwise will never be flowing
        "continue to periodically send database state as normal if mongo update fails" in {
          val parentProbe = TestProbe()

          MockTrafficSwitchService.getTrafficSwitchState.returns(Future.successful(TrafficSwitchState.Flowing)).anyNumberOfTimes()

          MockTrafficSwitchService.stopTrafficFlow returns Future.failed(dbException)

          val stateActor = parentProbe.childActorOf(
            TrafficSwitchStateActor.props(
              mockTrafficSwitchService,
              config.copy(flowingStateRefreshPeriod = shortTime, notFlowingStateRefreshPeriod = shortTime)))

          stateActor ! TrafficSwitchStateActor.UpdateDatabaseToNotFlowing

          parentProbe.fishForSpecificMessage() {
            case TrafficSwitchActor.AcknowledgeNotFlowing => true
          }

          parentProbe.expectMsg(TrafficSwitchActor.SetState(TrafficSwitchState.Flowing))
          parentProbe.expectMsg(TrafficSwitchActor.SetState(TrafficSwitchState.Flowing))

          stop(stateActor)
        }
      }

      "database is down" must {
        "ignore update and retry at next refresh period" in {
          val parentProbe = TestProbe()

          // To initialize..
          MockTrafficSwitchService.getTrafficSwitchState returns Future.successful(TrafficSwitchState.NotFlowing)

          // Subsequently fail then recover...
          MockTrafficSwitchService.getTrafficSwitchState returns Future.failed(dbException)
          MockTrafficSwitchService.getTrafficSwitchState returns Future.successful(TrafficSwitchState.Flowing)

          val stateActor = parentProbe.childActorOf(
            TrafficSwitchStateActor.props(mockTrafficSwitchService, config.copy(notFlowingStateRefreshPeriod = shortTime)))

          parentProbe.expectMsg(TrafficSwitchActor.SetState(TrafficSwitchState.NotFlowing))
          parentProbe.expectMsg(TrafficSwitchActor.SetState(TrafficSwitchState.Flowing))

          stop(stateActor)
        }
      }
    }
  }
}
