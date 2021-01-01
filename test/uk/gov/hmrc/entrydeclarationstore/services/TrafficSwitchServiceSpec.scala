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

package uk.gov.hmrc.entrydeclarationstore.services

import org.scalamock.handlers.CallHandler
import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.entrydeclarationstore.logging.LoggingContext
import uk.gov.hmrc.entrydeclarationstore.models.{TrafficSwitchState, TrafficSwitchStatus}
import uk.gov.hmrc.entrydeclarationstore.reporting.{MockReportSender, TrafficStarted}
import uk.gov.hmrc.entrydeclarationstore.repositories.MockTrafficSwitchRepo
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import java.time.temporal.ChronoUnit
import java.time.{Duration, Instant}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}

class TrafficSwitchServiceSpec extends UnitSpec with ScalaFutures with MockReportSender with MockTrafficSwitchRepo {

  class Setup {
    val trafficSwitchService: TrafficSwitchService = new TrafficSwitchService(mockTrafficSwitchRepo, mockReportSender)
    val expectedResult: Future[Unit]               = Future.successful(())
    val timeDifference: Duration = Duration.of(1, ChronoUnit.MINUTES)
    val timeTrafficStopped: Instant                = Instant.now
    val timeTrafficStarted: Instant                = timeTrafficStopped.plus(timeDifference)

    def trafficSwitchStatus(trafficFlow: TrafficSwitchState): TrafficSwitchStatus =
      TrafficSwitchStatus(trafficFlow, Some(timeTrafficStopped), Some(timeTrafficStarted))

    def mockResetTrafficSwitch(): CallHandler[Future[Unit]] =
      MockTrafficSwitchRepo.resetToDefault returns expectedResult

    def mockSetTrafficSwitch(
      trafficFlow: TrafficSwitchState,
      response: Option[TrafficSwitchStatus]): CallHandler[Future[Option[TrafficSwitchStatus]]] =
      MockTrafficSwitchRepo.setTrafficSwitchState(trafficFlow) returns Future.successful(response)

    def mockGetTrafficSwitchStatus(trafficFlow: TrafficSwitchState): CallHandler[Future[TrafficSwitchStatus]] =
      MockTrafficSwitchRepo.getTrafficSwitchStatus returns Future.successful(trafficSwitchStatus(trafficFlow))
  }

  implicit val lc: LoggingContext = LoggingContext()
  implicit val hc: HeaderCarrier  = HeaderCarrier()

  "TrafficSwitchService" when {

    "resetting the Traffic Switch" should {
      "call the Traffic Switch Repo" in new Setup {
        mockResetTrafficSwitch()
        val result: Future[Unit] = trafficSwitchService.resetTrafficSwitch

        result shouldBe expectedResult
      }
    }

    "stopping the flow on the Traffic Switch" should {
      "call the Traffic Switch Repo" in new Setup {
        val state: TrafficSwitchState = TrafficSwitchState.NotFlowing
        mockSetTrafficSwitch(state, Some(trafficSwitchStatus(state)))
        val result: Future[Unit] = trafficSwitchService.stopTrafficFlow

        result.futureValue shouldBe expectedResult.futureValue
      }
    }

    "starting the flow on the Traffic Switch" should {
      "call the Traffic Switch Repo" in new Setup {
        val state: TrafficSwitchState = TrafficSwitchState.Flowing
        mockSetTrafficSwitch(state, Some(trafficSwitchStatus(state)))
        MockReportSender.sendReport(TrafficStarted(timeDifference)) returns Future.successful((): Unit) noMoreThanOnce()
        val result: Future[Unit] = trafficSwitchService.startTrafficFlow

        result.futureValue shouldBe expectedResult.futureValue
      }
      "not wait for report to send" in new Setup {
        val promise: Promise[Unit] = Promise[Unit]
        val state: TrafficSwitchState = TrafficSwitchState.Flowing
        mockSetTrafficSwitch(state, Some(trafficSwitchStatus(state)))
        MockReportSender.sendReport(TrafficStarted(timeDifference)) returns promise.future noMoreThanOnce()
        val result: Future[Unit] = trafficSwitchService.startTrafficFlow

        result.futureValue shouldBe expectedResult.futureValue
      }
      "not send a report if no startTime is returned" in new Setup {
        val state: TrafficSwitchState = TrafficSwitchState.Flowing
        mockSetTrafficSwitch(state, Some(trafficSwitchStatus(state).copy(lastTrafficStarted = None)))
        val result: Future[Unit] = trafficSwitchService.startTrafficFlow

        result.futureValue shouldBe expectedResult.futureValue
      }
      "not send a report if no stopTime is returned" in new Setup {
        val state: TrafficSwitchState = TrafficSwitchState.Flowing
        mockSetTrafficSwitch(state, Some(trafficSwitchStatus(state).copy(lastTrafficStopped = None)))
        val result: Future[Unit] = trafficSwitchService.startTrafficFlow

        result.futureValue shouldBe expectedResult.futureValue
      }
      "not send a report if nothing is returned" in new Setup {
        val state: TrafficSwitchState = TrafficSwitchState.Flowing
        mockSetTrafficSwitch(state, None)
        val result: Future[Unit] = trafficSwitchService.startTrafficFlow

        result.futureValue shouldBe expectedResult.futureValue
      }
    }

    "retrieving the Traffic Switch Status" when {
      "Not Flowing" must {
        "return not flowing" in new Setup {
          mockGetTrafficSwitchStatus(TrafficSwitchState.NotFlowing)
          val result: Future[TrafficSwitchStatus] = trafficSwitchService.getTrafficSwitchStatus

          result.futureValue shouldBe trafficSwitchStatus(TrafficSwitchState.NotFlowing)
        }
      }

      "Flowing" must {
        "return flowing" in new Setup {
          mockGetTrafficSwitchStatus(TrafficSwitchState.Flowing)
          val result: Future[TrafficSwitchStatus] = trafficSwitchService.getTrafficSwitchStatus

          result.futureValue shouldBe trafficSwitchStatus(TrafficSwitchState.Flowing)
        }
      }
    }
    "retrieving the Traffic Switch State" when {
      "Not Flowing" must {
        "return not flowing" in new Setup {
          mockGetTrafficSwitchStatus(TrafficSwitchState.NotFlowing)
          val result: Future[TrafficSwitchState] = trafficSwitchService.getTrafficSwitchState

          result.futureValue shouldBe TrafficSwitchState.NotFlowing
        }
      }

      "Flowing" must {
        "return flowing" in new Setup {
          mockGetTrafficSwitchStatus(TrafficSwitchState.Flowing)
          val result: Future[TrafficSwitchState] = trafficSwitchService.getTrafficSwitchState

          result.futureValue shouldBe TrafficSwitchState.Flowing
        }
      }
    }
  }
}
