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

import akka.actor.{ActorRef, ActorRefFactory, ActorSystem, Status}
import akka.pattern.CircuitBreakerOpenException
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, Inside}
import uk.gov.hmrc.entrydeclarationstore.models.CircuitBreakerState
import uk.gov.hmrc.entrydeclarationstore.repositories.MockCircuitBreakerRepo
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{Future, Promise, TimeoutException}
import scala.concurrent.duration._
import scala.util.Try
import scala.util.control.NoStackTrace

class CircuitBreakerActorSpec
    extends TestKit(ActorSystem("CircuitBreakerActorSpec"))
    with UnitSpec
    with BeforeAndAfterAll
    with MockCircuitBreakerRepo
    with ImplicitSender
    with Inside {
  override def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  val config: CircuitBreakerConfig =
    CircuitBreakerConfig(5, 200.millis, openStateRefreshPeriod = 1.minute, closedStateRefreshPeriod = 1.minute)

  private val exceptionAsFailure: Try[_] => Boolean = _.isFailure
  private val alwaysFail: Try[_] => Boolean         = _ => true
  private val neverFail: Try[_] => Boolean          = _ => false

  val e: Exception = new Exception with NoStackTrace

  class Test {
    val stateProbe: TestProbe = TestProbe()

    val circuitBreakerActor: ActorRef =
      system.actorOf(CircuitBreakerActor.props(config, new CircuitBreakerStateActor.Factory {
        override def apply(actorRefFactory: ActorRefFactory): ActorRef = stateProbe.ref
      }))

    def tripCircuitBreaker(): Unit = {
      for (_ <- 1 to config.maxFailures)
        circuitBreakerActor ! CircuitBreakerActor.MakeCall(_ => Future.failed(e), exceptionAsFailure)

      for (_ <- 1 to config.maxFailures)
        expectMsg(Status.Failure(e))

      // Should now trip...
      circuitBreakerActor ! CircuitBreakerActor.MakeCall(_ => Future.failed(e), exceptionAsFailure)
      inside(expectMsgType[Status.Failure]) {
        case Status.Failure(e) => e shouldBe a[CircuitBreakerOpenException]
      }

      stateProbe.expectMsg(CircuitBreakerStateActor.UpdateDatabaseToOpen)
    }
  }

  "CircuitBreakerActor" when {

    "closed" must {
      "allow calls through" in new Test {
        circuitBreakerActor ! CircuitBreakerActor.SetState(CircuitBreakerState.Closed)

        circuitBreakerActor ! CircuitBreakerActor.MakeCall(_ => Future.successful("hi"), exceptionAsFailure)
        expectMsg(CircuitBreakerActor.CallResult("hi"))
      }

      "increment failure count on exception" in new Test {
        circuitBreakerActor ! CircuitBreakerActor.SetState(CircuitBreakerState.Closed)

        circuitBreakerActor ! CircuitBreakerActor.MakeCall(_ => Future.failed(e), exceptionAsFailure)
        expectMsg(Status.Failure(e))

        circuitBreakerActor ! CircuitBreakerActor.GetInternalState
        expectMsg(CircuitBreakerActor.InternalClosed(1))
      }

      "increment failure count on failure result" in new Test {
        circuitBreakerActor ! CircuitBreakerActor.SetState(CircuitBreakerState.Closed)

        circuitBreakerActor ! CircuitBreakerActor.MakeCall(_ => Future.successful("hi"), alwaysFail)
        expectMsg(CircuitBreakerActor.CallResult("hi"))

        circuitBreakerActor ! CircuitBreakerActor.GetInternalState
        expectMsg(CircuitBreakerActor.InternalClosed(1))
      }

      "increment failure count on actual exception thrown" in new Test {
        circuitBreakerActor ! CircuitBreakerActor.SetState(CircuitBreakerState.Closed)

        circuitBreakerActor ! CircuitBreakerActor.MakeCall(_ => throw e, exceptionAsFailure)
        expectMsg(Status.Failure(e))

        circuitBreakerActor ! CircuitBreakerActor.GetInternalState
        expectMsg(CircuitBreakerActor.InternalClosed(1))
      }

      "not increment failure count on non-failure exception" in new Test {
        circuitBreakerActor ! CircuitBreakerActor.SetState(CircuitBreakerState.Closed)

        circuitBreakerActor ! CircuitBreakerActor.MakeCall(_ => Future.failed(e), neverFail)
        expectMsg(Status.Failure(e))

        circuitBreakerActor ! CircuitBreakerActor.GetInternalState
        expectMsg(CircuitBreakerActor.InternalClosed(0))
      }

      "reset failure count on success" in new Test {
        circuitBreakerActor ! CircuitBreakerActor.SetState(CircuitBreakerState.Closed)

        circuitBreakerActor ! CircuitBreakerActor.MakeCall(_ => Future.failed(e), exceptionAsFailure)
        expectMsg(Status.Failure(e))

        circuitBreakerActor ! CircuitBreakerActor.GetInternalState
        expectMsg(CircuitBreakerActor.InternalClosed(1))

        circuitBreakerActor ! CircuitBreakerActor.MakeCall(_ => Future.successful("hi"), exceptionAsFailure)
        expectMsg(CircuitBreakerActor.CallResult("hi"))

        circuitBreakerActor ! CircuitBreakerActor.GetInternalState
        expectMsg(CircuitBreakerActor.InternalClosed(0))
      }

      "reset failure count after non-failure exception" in new Test {
        circuitBreakerActor ! CircuitBreakerActor.SetState(CircuitBreakerState.Closed)

        circuitBreakerActor ! CircuitBreakerActor.MakeCall(_ => Future.failed(e), exceptionAsFailure)
        expectMsg(Status.Failure(e))

        circuitBreakerActor ! CircuitBreakerActor.GetInternalState
        expectMsg(CircuitBreakerActor.InternalClosed(1))

        circuitBreakerActor ! CircuitBreakerActor.MakeCall(_ => Future.failed(e), neverFail)
        expectMsg(Status.Failure(e))

        circuitBreakerActor ! CircuitBreakerActor.GetInternalState
        expectMsg(CircuitBreakerActor.InternalClosed(0))
      }

      "increment failure count on call timeout (before call completes)" in new Test {
        circuitBreakerActor ! CircuitBreakerActor.SetState(CircuitBreakerState.Closed)

        val promise: Promise[String] = Promise[String]
        circuitBreakerActor ! CircuitBreakerActor.MakeCall(_ => promise.future, exceptionAsFailure)

        expectMsgType[Status.Failure].cause shouldBe a[TimeoutException]

        circuitBreakerActor ! CircuitBreakerActor.GetInternalState
        expectMsg(CircuitBreakerActor.InternalClosed(1))
      }

      "set state to open and tell the state actor when failure count reaches maximum" in new Test {
        circuitBreakerActor ! CircuitBreakerActor.SetState(CircuitBreakerState.Closed)

        tripCircuitBreaker()

        circuitBreakerActor ! CircuitBreakerActor.GetInternalState
        expectMsg(CircuitBreakerActor.InternalOpen(awaitingAck = true))
      }

      // otherwise there is a race condition where it can be closed again from an in-flight SetState(Closed)
      // sent to us from the state before we told it to open
      "ignore close messages from state until UpdateDatabaseToOpen is acknowledged" in new Test {
        circuitBreakerActor ! CircuitBreakerActor.SetState(CircuitBreakerState.Closed)

        tripCircuitBreaker()

        // Should be ignored...
        circuitBreakerActor ! CircuitBreakerActor.SetState(CircuitBreakerState.Closed)
        circuitBreakerActor ! CircuitBreakerActor.GetInternalState
        expectMsg(CircuitBreakerActor.InternalOpen(awaitingAck = true))

        // ... until acknowleged...
        circuitBreakerActor ! CircuitBreakerActor.AcknowledgeOpen
        circuitBreakerActor ! CircuitBreakerActor.GetInternalState
        expectMsg(CircuitBreakerActor.InternalOpen(awaitingAck = false))

        circuitBreakerActor ! CircuitBreakerActor.SetState(CircuitBreakerState.Closed)
        circuitBreakerActor ! CircuitBreakerActor.GetInternalState
        expectMsg(CircuitBreakerActor.InternalClosed(0))
      }

      "become open when told by the state actor" in new Test {
        circuitBreakerActor ! CircuitBreakerActor.SetState(CircuitBreakerState.Closed)

        circuitBreakerActor ! CircuitBreakerActor.MakeCall(_ => Future.successful("hi"), exceptionAsFailure)
        expectMsg(CircuitBreakerActor.CallResult("hi"))

        circuitBreakerActor ! CircuitBreakerActor.SetState(CircuitBreakerState.Open)

        circuitBreakerActor ! CircuitBreakerActor.GetInternalState
        expectMsg(CircuitBreakerActor.InternalOpen(awaitingAck = false))
      }

      "ignore (not reset failure count) when told closed when told by the state actor" in new Test {
        circuitBreakerActor ! CircuitBreakerActor.SetState(CircuitBreakerState.Closed)

        circuitBreakerActor ! CircuitBreakerActor.MakeCall(_ => Future.failed(e), exceptionAsFailure)
        expectMsg(Status.Failure(e))

        circuitBreakerActor ! CircuitBreakerActor.SetState(CircuitBreakerState.Closed)

        circuitBreakerActor ! CircuitBreakerActor.GetInternalState
        expectMsg(CircuitBreakerActor.InternalClosed(1))
      }
    }

    "open" must {
      "not allow call and reply with CircuitBreakerOpenException failure" in new Test {
        circuitBreakerActor ! CircuitBreakerActor.SetState(CircuitBreakerState.Open)

        circuitBreakerActor ! CircuitBreakerActor.MakeCall(_ => Future.successful("hi"), exceptionAsFailure)

        inside(expectMsgType[Status.Failure]) {
          case Status.Failure(e) => e shouldBe a[CircuitBreakerOpenException]
        }

        circuitBreakerActor ! CircuitBreakerActor.GetInternalState
        expectMsg(CircuitBreakerActor.InternalOpen(awaitingAck = false))
      }

      "become closed with zero failure count when told by the state actor" in new Test {
        circuitBreakerActor ! CircuitBreakerActor.SetState(CircuitBreakerState.Open)

        circuitBreakerActor ! CircuitBreakerActor.MakeCall(_ => Future.successful("hi"), exceptionAsFailure)

        inside(expectMsgType[Status.Failure]) {
          case Status.Failure(e) => e shouldBe a[CircuitBreakerOpenException]
        }

        circuitBreakerActor ! CircuitBreakerActor.SetState(CircuitBreakerState.Closed)

        circuitBreakerActor ! CircuitBreakerActor.GetInternalState
        expectMsg(CircuitBreakerActor.InternalClosed(0))
      }

      "ignore when told open by the state actor" in new Test {
        circuitBreakerActor ! CircuitBreakerActor.SetState(CircuitBreakerState.Open)

        // Tell it again...
        circuitBreakerActor ! CircuitBreakerActor.SetState(CircuitBreakerState.Open)

        circuitBreakerActor ! CircuitBreakerActor.GetInternalState
        expectMsg(CircuitBreakerActor.InternalOpen(awaitingAck = false))
      }

      "allow calls started when closed to complete normally" in new Test {
        circuitBreakerActor ! CircuitBreakerActor.SetState(CircuitBreakerState.Closed)

        val promise: Promise[String] = Promise[String]
        circuitBreakerActor ! CircuitBreakerActor.MakeCall(_ => promise.future, exceptionAsFailure)

        circuitBreakerActor ! CircuitBreakerActor.SetState(CircuitBreakerState.Open)

        promise.success("hi")
        expectMsg(CircuitBreakerActor.CallResult("hi"))

        circuitBreakerActor ! CircuitBreakerActor.GetInternalState
        expectMsg(CircuitBreakerActor.InternalOpen(awaitingAck = false))
      }

      "allow calls for failed futures throwing exceptions started when closed to complete normally" in new Test {
        circuitBreakerActor ! CircuitBreakerActor.SetState(CircuitBreakerState.Closed)

        val promise: Promise[String] = Promise[String]
        circuitBreakerActor ! CircuitBreakerActor.MakeCall(_ => promise.future, exceptionAsFailure)

        circuitBreakerActor ! CircuitBreakerActor.SetState(CircuitBreakerState.Open)

        promise.failure(e)
        expectMsg(Status.Failure(e))

        circuitBreakerActor ! CircuitBreakerActor.GetInternalState
        expectMsg(CircuitBreakerActor.InternalOpen(awaitingAck = false))
      }
    }

    "new" must {
      "stash call messages until it is initialized" in new Test {
        circuitBreakerActor ! CircuitBreakerActor.MakeCall(_ => Future.successful("hi"), exceptionAsFailure)
        circuitBreakerActor ! CircuitBreakerActor.SetState(CircuitBreakerState.Closed)

        expectMsg(CircuitBreakerActor.CallResult("hi"))
      }
    }
  }
}
