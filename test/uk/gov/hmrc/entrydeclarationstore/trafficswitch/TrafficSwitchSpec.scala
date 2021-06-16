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

import akka.actor.ActorSystem
import akka.pattern.{AskTimeoutException, CircuitBreakerOpenException}
import org.scalatest.Matchers.{a, all, an, convertToAnyShouldWrapper}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Injecting
import play.api.{Application, Environment, Mode}
import uk.gov.hmrc.entrydeclarationstore.housekeeping.HousekeepingScheduler
import uk.gov.hmrc.entrydeclarationstore.models.TrafficSwitchState
import uk.gov.hmrc.entrydeclarationstore.services.MockTrafficSwitchService
import org.scalatest.WordSpec
import play.api.test.Helpers.{await, defaultAwaitTimeout}

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{Future, Promise, TimeoutException}
import scala.util.Try
import scala.util.control.NoStackTrace

class TrafficSwitchSpec
    extends WordSpec
    with ScalaFutures
    with Eventually
    with MockTrafficSwitchService
    with GuiceOneAppPerSuite
    with Injecting {

  val unit: Unit = ()

  override lazy val app: Application = new GuiceApplicationBuilder()
    .in(Environment.simple(mode = Mode.Dev))
    .disable[HousekeepingScheduler]
    .configure("metrics.enabled" -> "false")
    .build()

  implicit val actorSystem: ActorSystem = inject[ActorSystem]

  // Note: More detailed testing for different failure functions will be done against the underlying actor
  // here assume any exceptions are failures
  private val exceptionAsFailure: Try[_] => Boolean = _.isFailure

  val maxFailures                 = 5
  val callTimeout: FiniteDuration = 1.second

  val defaultConfig: TrafficSwitchConfig =
    TrafficSwitchConfig(5, 1.second, 1.second, 1.second)
  val shortTimeoutConfig: TrafficSwitchConfig =
    TrafficSwitchConfig(5, 10.millis, 1.second, 1.second)

  class Test {
    val e = new Exception with NoStackTrace

    def sayHi: Future[String]           = Future.successful("hi")
    def throwException: Future[Nothing] = Future.failed(e)

    def trafficSwitch(trafficSwitchConfig: TrafficSwitchConfig = defaultConfig): TrafficSwitch = {
      MockTrafficSwitchService.getTrafficSwitchState returns Future.successful(TrafficSwitchState.Flowing) noMoreThanOnce ()
      new TrafficSwitch(trafficSwitchActorFactory(trafficSwitchConfig), trafficSwitchConfig)
    }

    def trafficSwitchActorFactory(trafficSwitchConfig: TrafficSwitchConfig = defaultConfig) =
      new TrafficSwitchActor.FactoryImpl(mockTrafficSwitchService, trafficSwitchConfig)

    def trafficSwitchNotFlowing(trafficSwitchConfig: TrafficSwitchConfig = defaultConfig): TrafficSwitch = {
      val ts: TrafficSwitch = trafficSwitch(trafficSwitchConfig)
      MockTrafficSwitchService.stopTrafficFlow returns Future.successful(unit)

      for (_ <- 1 to maxFailures) {
        ts.withTrafficSwitch(throwException, exceptionAsFailure).failed.futureValue shouldBe e
      }

      ts
    }
  }

  "TrafficSwitch" when {
    "flowing" must {
      "allow calls through" in new Test {
        val ts: TrafficSwitch = trafficSwitch()

        ts.withTrafficSwitch(sayHi, exceptionAsFailure).futureValue shouldBe "hi"
      }

      "allow failed futures through" in new Test {
        val ts: TrafficSwitch = trafficSwitch()

        ts.withTrafficSwitch(throwException, exceptionAsFailure).failed.futureValue shouldBe e
      }

      "throw TimeoutException on call timeout" in new Test {
        val ts: TrafficSwitch = trafficSwitch(shortTimeoutConfig)

        val promise: Promise[String] = Promise[String]

        val result: Future[String] = ts.withTrafficSwitch(promise.future, exceptionAsFailure)

        result.failed.futureValue shouldBe a[TimeoutException]
      }

      "set state to not flowing after max failures" in new Test {
        val ts: TrafficSwitch = trafficSwitch()

        MockTrafficSwitchService.stopTrafficFlow returns Future.successful(unit)

        for (_ <- 1 to maxFailures)
          ts.withTrafficSwitch(throwException, exceptionAsFailure).failed.futureValue shouldBe e

        ts.withTrafficSwitch(sayHi, exceptionAsFailure).failed.futureValue shouldBe a[CircuitBreakerOpenException]
      }

      "set state to not flowing after max timeouts" in new Test {
        val ts: TrafficSwitch = trafficSwitch(shortTimeoutConfig)

        val promise: Promise[String] = Promise[String]

        MockTrafficSwitchService.stopTrafficFlow returns Future.successful(unit)

        val results: Seq[Throwable] = Future
          .sequence((1 to maxFailures)
            .map(_ => ts.withTrafficSwitch(promise.future, exceptionAsFailure).failed))
          .futureValue

        all(results) shouldBe a[TimeoutException]

        ts.withTrafficSwitch(sayHi, exceptionAsFailure).failed.futureValue shouldBe a[CircuitBreakerOpenException]
      }

      "failsafe timeout (ask timeout) if repo takes ages to initialize traffic switch actor" in new Test {
        val promise: Promise[TrafficSwitchState] = Promise[TrafficSwitchState]
        MockTrafficSwitchService.getTrafficSwitchState returns promise.future anyNumberOfTimes ()

        val ts = new TrafficSwitch(trafficSwitchActorFactory(shortTimeoutConfig), shortTimeoutConfig)

        await(ts.withTrafficSwitch(sayHi, exceptionAsFailure).failed) shouldBe an[AskTimeoutException]
      }

      "continue to work when exception actually thrown by call" in new Test {
        MockTrafficSwitchService.getTrafficSwitchState returns Future.successful(TrafficSwitchState.Flowing) anyNumberOfTimes ()
        val ts = new TrafficSwitch(trafficSwitchActorFactory(shortTimeoutConfig), shortTimeoutConfig)

        // Will probably fail with a timeout - the crucial thing is that CB will continue to accept calls
        ts.withTrafficSwitch(throw e, exceptionAsFailure)

        ts.withTrafficSwitch(sayHi, exceptionAsFailure).futureValue shouldBe "hi"
      }
    }

    "not flowing" must {
      "not perform call (or perform any side effects)" in new Test {
        val ts: TrafficSwitch = trafficSwitchNotFlowing()

        val b = new AtomicBoolean(false)

        ts.withTrafficSwitch(Future.successful(b.set(true)), exceptionAsFailure)
          .failed
          .futureValue shouldBe a[CircuitBreakerOpenException]

        b.get() shouldBe false
      }

      "start flowing when the state is manually changed to flowing" in new Test {
        val ts: TrafficSwitch = trafficSwitchNotFlowing(defaultConfig.copy(notFlowingStateRefreshPeriod = 50.millis))

        MockTrafficSwitchService.getTrafficSwitchState returns Future.successful(TrafficSwitchState.Flowing) anyNumberOfTimes ()

        eventually {
          ts.withTrafficSwitch(sayHi, exceptionAsFailure).futureValue shouldBe "hi"
        }
      }
    }
  }
}
