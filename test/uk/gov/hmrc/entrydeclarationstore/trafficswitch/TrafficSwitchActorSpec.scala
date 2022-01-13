/*
 * Copyright 2022 HM Revenue & Customs
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

import akka.actor.{ActorRef, ActorRefFactory, ActorSystem, Status}
import akka.pattern.CircuitBreakerOpenException
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.matchers.should.Matchers.{a, convertToAnyShouldWrapper}
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfterAll, Inside}
import uk.gov.hmrc.entrydeclarationstore.models.TrafficSwitchState

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise, TimeoutException}
import scala.util.Try
import scala.util.control.NoStackTrace

class TrafficSwitchActorSpec
    extends TestKit(ActorSystem("TrafficSwitchActorSpec"))
    with AnyWordSpecLike
    with BeforeAndAfterAll
    with ImplicitSender
    with Inside {
  override def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  val config: TrafficSwitchConfig =
    TrafficSwitchConfig(5, 200.millis, notFlowingStateRefreshPeriod = 1.minute, flowingStateRefreshPeriod = 1.minute)

  private val exceptionAsFailure: Try[_] => Boolean = _.isFailure
  private val alwaysFail: Try[_] => Boolean         = _ => true
  private val neverFail: Try[_] => Boolean          = _ => false

  class Test {
    val e: Exception                            = new Exception with NoStackTrace
    def sayHi: Unit => Future[String]           = _ => Future.successful("hi")
    def throwException: Unit => Future[Nothing] = _ => Future.failed(e)

    val stateProbe: TestProbe = TestProbe()

    val trafficSwitchActor: ActorRef =
      system.actorOf(TrafficSwitchActor.props(config, (_: ActorRefFactory) => stateProbe.ref))

    def tripTrafficSwitch(): Unit = {
      for (_ <- 1 to config.maxFailures)
        trafficSwitchActor ! TrafficSwitchActor.MakeCall(throwException, exceptionAsFailure)

      for (_ <- 1 to config.maxFailures)
        expectMsg(Status.Failure(e))

      // Should now trip...
      trafficSwitchActor ! TrafficSwitchActor.MakeCall(throwException, exceptionAsFailure)
      inside(expectMsgType[Status.Failure]) {
        case Status.Failure(e) => e shouldBe a[CircuitBreakerOpenException]
      }

      stateProbe.expectMsg(TrafficSwitchStateActor.UpdateDatabaseToNotFlowing)
    }
  }

  "TrafficSwitchActor" when {

    "flowing" must {
      "allow calls through" in new Test {
        trafficSwitchActor ! TrafficSwitchActor.SetState(TrafficSwitchState.Flowing)

        trafficSwitchActor ! TrafficSwitchActor.MakeCall(sayHi, exceptionAsFailure)
        expectMsg(TrafficSwitchActor.CallResult("hi"))
      }

      "increment failure count on exception" in new Test {
        trafficSwitchActor ! TrafficSwitchActor.SetState(TrafficSwitchState.Flowing)

        trafficSwitchActor ! TrafficSwitchActor.MakeCall(throwException, exceptionAsFailure)
        expectMsg(Status.Failure(e))

        trafficSwitchActor ! TrafficSwitchActor.GetInternalState
        expectMsg(TrafficSwitchActor.InternalTrafficFlowing(1))
      }

      "increment failure count on failure result" in new Test {
        trafficSwitchActor ! TrafficSwitchActor.SetState(TrafficSwitchState.Flowing)

        trafficSwitchActor ! TrafficSwitchActor.MakeCall(sayHi, alwaysFail)
        expectMsg(TrafficSwitchActor.CallResult("hi"))

        trafficSwitchActor ! TrafficSwitchActor.GetInternalState
        expectMsg(TrafficSwitchActor.InternalTrafficFlowing(1))
      }

      "increment failure count on actual exception thrown" in new Test {
        trafficSwitchActor ! TrafficSwitchActor.SetState(TrafficSwitchState.Flowing)

        trafficSwitchActor ! TrafficSwitchActor.MakeCall(_ => throw e, exceptionAsFailure)
        expectMsg(Status.Failure(e))

        trafficSwitchActor ! TrafficSwitchActor.GetInternalState
        expectMsg(TrafficSwitchActor.InternalTrafficFlowing(1))
      }

      "not increment failure count on non-failure exception" in new Test {
        trafficSwitchActor ! TrafficSwitchActor.SetState(TrafficSwitchState.Flowing)

        trafficSwitchActor ! TrafficSwitchActor.MakeCall(throwException, neverFail)
        expectMsg(Status.Failure(e))

        trafficSwitchActor ! TrafficSwitchActor.GetInternalState
        expectMsg(TrafficSwitchActor.InternalTrafficFlowing(0))
      }

      "reset failure count on success" in new Test {
        trafficSwitchActor ! TrafficSwitchActor.SetState(TrafficSwitchState.Flowing)

        trafficSwitchActor ! TrafficSwitchActor.MakeCall(throwException, exceptionAsFailure)
        expectMsg(Status.Failure(e))

        trafficSwitchActor ! TrafficSwitchActor.GetInternalState
        expectMsg(TrafficSwitchActor.InternalTrafficFlowing(1))

        trafficSwitchActor ! TrafficSwitchActor.MakeCall(sayHi, exceptionAsFailure)
        expectMsg(TrafficSwitchActor.CallResult("hi"))

        trafficSwitchActor ! TrafficSwitchActor.GetInternalState
        expectMsg(TrafficSwitchActor.InternalTrafficFlowing(0))
      }

      "reset failure count after non-failure exception" in new Test {
        trafficSwitchActor ! TrafficSwitchActor.SetState(TrafficSwitchState.Flowing)

        trafficSwitchActor ! TrafficSwitchActor.MakeCall(throwException, exceptionAsFailure)
        expectMsg(Status.Failure(e))

        trafficSwitchActor ! TrafficSwitchActor.GetInternalState
        expectMsg(TrafficSwitchActor.InternalTrafficFlowing(1))

        trafficSwitchActor ! TrafficSwitchActor.MakeCall(throwException, neverFail)
        expectMsg(Status.Failure(e))

        trafficSwitchActor ! TrafficSwitchActor.GetInternalState
        expectMsg(TrafficSwitchActor.InternalTrafficFlowing(0))
      }

      "increment failure count on call timeout (before call completes)" in new Test {
        trafficSwitchActor ! TrafficSwitchActor.SetState(TrafficSwitchState.Flowing)

        val promise: Promise[String] = Promise[String]
        trafficSwitchActor ! TrafficSwitchActor.MakeCall(_ => promise.future, exceptionAsFailure)

        expectMsgType[Status.Failure].cause shouldBe a[TimeoutException]

        trafficSwitchActor ! TrafficSwitchActor.GetInternalState
        expectMsg(TrafficSwitchActor.InternalTrafficFlowing(1))
      }

      "set state to not flowing and tell the state actor when failure count reaches maximum" in new Test {
        trafficSwitchActor ! TrafficSwitchActor.SetState(TrafficSwitchState.Flowing)

        tripTrafficSwitch()

        trafficSwitchActor ! TrafficSwitchActor.GetInternalState
        expectMsg(TrafficSwitchActor.InternalTrafficNotFlowing(awaitingAck = true))
      }

      // otherwise there is a race condition where it can be started again from an in-flight SetState(Flowing)
      // sent to us from the state before we told it to stop traffic
      "ignore start flow messages from state until UpdateDatabaseToNotFlowing is acknowledged" in new Test {
        trafficSwitchActor ! TrafficSwitchActor.SetState(TrafficSwitchState.Flowing)

        tripTrafficSwitch()

        // Should be ignored...
        trafficSwitchActor ! TrafficSwitchActor.SetState(TrafficSwitchState.Flowing)
        trafficSwitchActor ! TrafficSwitchActor.GetInternalState
        expectMsg(TrafficSwitchActor.InternalTrafficNotFlowing(awaitingAck = true))

        // ... until acknowleged...
        trafficSwitchActor ! TrafficSwitchActor.AcknowledgeNotFlowing
        trafficSwitchActor ! TrafficSwitchActor.GetInternalState
        expectMsg(TrafficSwitchActor.InternalTrafficNotFlowing(awaitingAck = false))

        trafficSwitchActor ! TrafficSwitchActor.SetState(TrafficSwitchState.Flowing)
        trafficSwitchActor ! TrafficSwitchActor.GetInternalState
        expectMsg(TrafficSwitchActor.InternalTrafficFlowing(0))
      }

      "become not flowing when told by the state actor" in new Test {
        trafficSwitchActor ! TrafficSwitchActor.SetState(TrafficSwitchState.Flowing)

        trafficSwitchActor ! TrafficSwitchActor.MakeCall(sayHi, exceptionAsFailure)
        expectMsg(TrafficSwitchActor.CallResult("hi"))

        trafficSwitchActor ! TrafficSwitchActor.SetState(TrafficSwitchState.NotFlowing)

        trafficSwitchActor ! TrafficSwitchActor.GetInternalState
        expectMsg(TrafficSwitchActor.InternalTrafficNotFlowing(awaitingAck = false))
      }

      "ignore (not reset failure count) when told start flow by the state actor" in new Test {
        trafficSwitchActor ! TrafficSwitchActor.SetState(TrafficSwitchState.Flowing)

        trafficSwitchActor ! TrafficSwitchActor.MakeCall(throwException, exceptionAsFailure)
        expectMsg(Status.Failure(e))

        trafficSwitchActor ! TrafficSwitchActor.SetState(TrafficSwitchState.Flowing)

        trafficSwitchActor ! TrafficSwitchActor.GetInternalState
        expectMsg(TrafficSwitchActor.InternalTrafficFlowing(1))
      }
    }

    "not flowing" must {
      "not allow call and reply with CircuitBreakerOpenException failure" in new Test {
        trafficSwitchActor ! TrafficSwitchActor.SetState(TrafficSwitchState.NotFlowing)

        trafficSwitchActor ! TrafficSwitchActor.MakeCall(sayHi, exceptionAsFailure)

        inside(expectMsgType[Status.Failure]) {
          case Status.Failure(e) => e shouldBe a[CircuitBreakerOpenException]
        }

        trafficSwitchActor ! TrafficSwitchActor.GetInternalState
        expectMsg(TrafficSwitchActor.InternalTrafficNotFlowing(awaitingAck = false))
      }

      "become flowing with zero failure count when told by the state actor" in new Test {
        trafficSwitchActor ! TrafficSwitchActor.SetState(TrafficSwitchState.NotFlowing)

        trafficSwitchActor ! TrafficSwitchActor.MakeCall(sayHi, exceptionAsFailure)

        inside(expectMsgType[Status.Failure]) {
          case Status.Failure(e) => e shouldBe a[CircuitBreakerOpenException]
        }

        trafficSwitchActor ! TrafficSwitchActor.SetState(TrafficSwitchState.Flowing)

        trafficSwitchActor ! TrafficSwitchActor.GetInternalState
        expectMsg(TrafficSwitchActor.InternalTrafficFlowing(0))
      }

      "ignore when told to stop flow by the state actor" in new Test {
        trafficSwitchActor ! TrafficSwitchActor.SetState(TrafficSwitchState.NotFlowing)

        // Tell it again...
        trafficSwitchActor ! TrafficSwitchActor.SetState(TrafficSwitchState.NotFlowing)

        trafficSwitchActor ! TrafficSwitchActor.GetInternalState
        expectMsg(TrafficSwitchActor.InternalTrafficNotFlowing(awaitingAck = false))
      }

      "allow calls started when flowing to complete normally" in new Test {
        trafficSwitchActor ! TrafficSwitchActor.SetState(TrafficSwitchState.Flowing)

        val promise: Promise[String] = Promise[String]
        trafficSwitchActor ! TrafficSwitchActor.MakeCall(_ => promise.future, exceptionAsFailure)

        trafficSwitchActor ! TrafficSwitchActor.SetState(TrafficSwitchState.NotFlowing)

        promise.success("hi")
        expectMsg(TrafficSwitchActor.CallResult("hi"))

        trafficSwitchActor ! TrafficSwitchActor.GetInternalState
        expectMsg(TrafficSwitchActor.InternalTrafficNotFlowing(awaitingAck = false))
      }

      "allow calls for failed futures throwing exceptions started when flowing to complete normally" in new Test {
        trafficSwitchActor ! TrafficSwitchActor.SetState(TrafficSwitchState.Flowing)

        val promise: Promise[String] = Promise[String]
        trafficSwitchActor ! TrafficSwitchActor.MakeCall(_ => promise.future, exceptionAsFailure)

        trafficSwitchActor ! TrafficSwitchActor.SetState(TrafficSwitchState.NotFlowing)

        promise.failure(e)
        expectMsg(Status.Failure(e))

        trafficSwitchActor ! TrafficSwitchActor.GetInternalState
        expectMsg(TrafficSwitchActor.InternalTrafficNotFlowing(awaitingAck = false))
      }
    }

    "new" must {
      "stash call messages until it is initialized" in new Test {
        trafficSwitchActor ! TrafficSwitchActor.MakeCall(sayHi, exceptionAsFailure)
        trafficSwitchActor ! TrafficSwitchActor.SetState(TrafficSwitchState.Flowing)

        expectMsg(TrafficSwitchActor.CallResult("hi"))
      }
    }
  }
}
