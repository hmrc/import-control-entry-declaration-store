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

import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.ActorSystem
import akka.pattern.{AskTimeoutException, CircuitBreakerOpenException}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Injecting
import play.api.{Application, Environment, Mode}
import uk.gov.hmrc.entrydeclarationstore.models.CircuitBreakerState
import uk.gov.hmrc.entrydeclarationstore.repositories.MockCircuitBreakerRepo
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{Future, Promise, TimeoutException}
import scala.util.Try
import scala.util.control.NoStackTrace

class CircuitBreakerSpec
    extends UnitSpec
    with ScalaFutures
    with Eventually
    with MockCircuitBreakerRepo
    with GuiceOneAppPerSuite
    with Injecting {

  val unit: Unit = ()

  override lazy val app: Application = new GuiceApplicationBuilder()
    .in(Environment.simple(mode = Mode.Dev))
    .configure("metrics.enabled" -> "false")
    .build()

  implicit val actorSystem: ActorSystem = inject[ActorSystem]

  // Note: More detailed testing for different failure functions will be done against the underlying actor
  // here assume any exceptions are failures
  private val exceptionAsFailure: Try[_] => Boolean = _.isFailure

  val maxFailures                 = 5
  val callTimeout: FiniteDuration = 1.second

  val defaultConfig: CircuitBreakerConfig =
    CircuitBreakerConfig(5, 1.second, 1.second, 1.second)
  val shortTimeoutConfig: CircuitBreakerConfig =
    CircuitBreakerConfig(5, 10.millis, 1.second, 1.second)

  class Test {
    val e = new Exception with NoStackTrace

    def sayHi: Future[String]           = Future.successful("hi")
    def throwException: Future[Nothing] = Future.failed(e)

    def circuitBreaker(circuitBreakerConfig: CircuitBreakerConfig = defaultConfig): CircuitBreaker = {
      MockCircuitBreakerRepo.getCircuitBreakerState returns CircuitBreakerState.Closed noMoreThanOnce ()
      new CircuitBreaker(circuitBreakerActorFactory(circuitBreakerConfig), circuitBreakerConfig)
    }

    def circuitBreakerActorFactory(circuitBreakerConfig: CircuitBreakerConfig = defaultConfig) =
      new CircuitBreakerActor.FactoryImpl(mockCircuitBreakerRepo, circuitBreakerConfig)

    def openedCircuitBreaker(circuitBreakerConfig: CircuitBreakerConfig = defaultConfig): CircuitBreaker = {
      val cb: CircuitBreaker = circuitBreaker(circuitBreakerConfig)
      MockCircuitBreakerRepo.setCircuitBreakerState(CircuitBreakerState.Open) returns unit

      for (_ <- 1 to maxFailures) {
        cb.withCircuitBreaker(throwException, exceptionAsFailure).failed.futureValue shouldBe e
      }

      cb
    }
  }

  "CircuitBreaker" when {
    "closed" must {
      "allow calls through" in new Test {
        val cb: CircuitBreaker = circuitBreaker()

        cb.withCircuitBreaker(sayHi, exceptionAsFailure).futureValue shouldBe "hi"
      }

      "allow failed futures through" in new Test {
        val cb: CircuitBreaker = circuitBreaker()

        cb.withCircuitBreaker(throwException, exceptionAsFailure).failed.futureValue shouldBe e
      }

      "throw TimeoutException on call timeout" in new Test {
        val cb: CircuitBreaker = circuitBreaker(shortTimeoutConfig)

        val promise: Promise[String] = Promise[String]

        val result: Future[String] = cb.withCircuitBreaker(promise.future, exceptionAsFailure)

        result.failed.futureValue shouldBe a[TimeoutException]
      }

      "set state to open after max failures" in new Test {
        val cb: CircuitBreaker = circuitBreaker()

        MockCircuitBreakerRepo.setCircuitBreakerState(CircuitBreakerState.Open) returns unit

        for (_ <- 1 to maxFailures)
          cb.withCircuitBreaker(throwException, exceptionAsFailure).failed.futureValue shouldBe e

        cb.withCircuitBreaker(sayHi, exceptionAsFailure).failed.futureValue shouldBe a[CircuitBreakerOpenException]
      }

      "set state to open after max timeouts" in new Test {
        val cb: CircuitBreaker = circuitBreaker(shortTimeoutConfig)

        val promise: Promise[String] = Promise[String]

        MockCircuitBreakerRepo.setCircuitBreakerState(CircuitBreakerState.Open) returns unit

        val results: Seq[Throwable] = Future
          .sequence((1 to maxFailures)
            .map(_ => cb.withCircuitBreaker(promise.future, exceptionAsFailure).failed))
          .futureValue

        all(results) shouldBe a[TimeoutException]

        cb.withCircuitBreaker(sayHi, exceptionAsFailure).failed.futureValue shouldBe a[CircuitBreakerOpenException]
      }

      "failsafe timeout (ask timeout) if repo takes ages to initialize circuit breaker actor" in new Test {
        val promise: Promise[CircuitBreakerState] = Promise[CircuitBreakerState]
        MockCircuitBreakerRepo.getCircuitBreakerState returns promise.future anyNumberOfTimes ()

        val cb = new CircuitBreaker(circuitBreakerActorFactory(shortTimeoutConfig), shortTimeoutConfig)

        await(cb.withCircuitBreaker(sayHi, exceptionAsFailure).failed) shouldBe an[AskTimeoutException]
      }

      "continue to work when exception actually thrown by call" in new Test {
        MockCircuitBreakerRepo.getCircuitBreakerState returns CircuitBreakerState.Closed anyNumberOfTimes ()
        val cb = new CircuitBreaker(circuitBreakerActorFactory(shortTimeoutConfig), shortTimeoutConfig)

        // Will probably fail with a timeout - the crucial thing is that CB will continue to accept calls
        cb.withCircuitBreaker(throw e, exceptionAsFailure)

        cb.withCircuitBreaker(sayHi, exceptionAsFailure).futureValue shouldBe "hi"
      }
    }

    "open" must {
      "not perform call (or perform any side effects)" in new Test {
        val cb: CircuitBreaker = openedCircuitBreaker()

        val b = new AtomicBoolean(false)

        cb.withCircuitBreaker(b.set(true), exceptionAsFailure)
          .failed
          .futureValue shouldBe a[CircuitBreakerOpenException]

        b.get() shouldBe false
      }

      "close when the state is manually changed to closed" in new Test {
        val cb: CircuitBreaker = openedCircuitBreaker(defaultConfig.copy(openStateRefreshPeriod = 50.millis))

        MockCircuitBreakerRepo.getCircuitBreakerState returns CircuitBreakerState.Closed anyNumberOfTimes ()

        eventually {
          cb.withCircuitBreaker(sayHi, exceptionAsFailure).futureValue shouldBe "hi"
        }
      }
    }
  }
}
