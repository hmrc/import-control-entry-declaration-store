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

package uk.gov.hmrc.entrydeclarationstore.services

import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.entrydeclarationstore.repositories.TrafficSwitchRepo
import uk.gov.hmrc.entrydeclarationstore.models.{TrafficSwitchState, TrafficSwitchStatus}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class TrafficSwitchServiceSpec extends UnitSpec with MockitoSugar with ScalaFutures {

  class Setup {
    val mockTrafficSwitchRepo: TrafficSwitchRepo   = mock[TrafficSwitchRepo]
    val trafficSwitchService: TrafficSwitchService = new TrafficSwitchService(mockTrafficSwitchRepo)
    val expectedResult: Future[Unit]                 = Future.successful(())

    def setUpResetTrafficSwitchMock =
      when(mockTrafficSwitchRepo.resetToDefault).thenReturn(expectedResult)

    def setUpSetTrafficSwitchMock(trafficFlow: TrafficSwitchState) =
      when(mockTrafficSwitchRepo.setTrafficSwitchState(trafficFlow)).thenReturn(expectedResult)

    def setTrafficSwitchStatus(trafficFlow: TrafficSwitchState) = {
      val testStatus = Future.successful(TrafficSwitchStatus(trafficFlow, None, None))
      when(mockTrafficSwitchRepo.getTrafficSwitchStatus).thenReturn(testStatus)
    }
  }

  "TrafficSwitchService" when {

    "resetting the Traffic Switch" should {
      "call the Traffic Switch Repo" in new Setup {
        setUpResetTrafficSwitchMock
        val result: Future[Unit] = trafficSwitchService.resetTrafficSwitch

        verify(mockTrafficSwitchRepo, times(1)).resetToDefault
        result shouldBe expectedResult
      }
    }

    "stopping the flow on the Traffic Switch" should {
      "call the Traffic Switch Repo" in new Setup {
        setUpSetTrafficSwitchMock(TrafficSwitchState.NotFlowing)
        val result: Future[Unit] = trafficSwitchService.stopTrafficFlow

        verify(mockTrafficSwitchRepo, times(1)).setTrafficSwitchState(TrafficSwitchState.NotFlowing)
        result shouldBe expectedResult
      }
    }

    "starting the flow on the Traffic Switch" should {
      "call the Traffic Switch Repo" in new Setup {
        setUpSetTrafficSwitchMock(TrafficSwitchState.Flowing)
        val result: Future[Unit] = trafficSwitchService.startTrafficFlow

        verify(mockTrafficSwitchRepo, times(1)).setTrafficSwitchState(TrafficSwitchState.Flowing)
        result shouldBe expectedResult
      }
    }

    "retrieving the Traffic Switch Status" when {
      "Not Flowing" must {
        "return nt flowing" in new Setup {
          setTrafficSwitchStatus(TrafficSwitchState.NotFlowing)
          val result: Future[TrafficSwitchStatus] = trafficSwitchService.getTrafficSwitchStatus

          result.futureValue shouldBe TrafficSwitchStatus(TrafficSwitchState.NotFlowing, None, None)
        }
      }

      "Flowing" must {
        "return flowing" in new Setup {
          setTrafficSwitchStatus(TrafficSwitchState.Flowing)
          val result: Future[TrafficSwitchStatus] = trafficSwitchService.getTrafficSwitchStatus

          result.futureValue shouldBe TrafficSwitchStatus(TrafficSwitchState.Flowing, None, None)
        }
      }
    }
  }
}
