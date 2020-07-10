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
import uk.gov.hmrc.entrydeclarationstore.repositories.CircuitBreakerRepo
import uk.gov.hmrc.entrydeclarationstore.models.CircuitBreakerState.{Closed, Open}
import uk.gov.hmrc.entrydeclarationstore.models.{CircuitBreakerState, CircuitBreakerStatus}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class CircuitBreakerServiceSpec extends UnitSpec with MockitoSugar with ScalaFutures {

  class Setup {
    val mockCircuitBreakerRepo:CircuitBreakerRepo = mock[CircuitBreakerRepo]
    val circuitBreakerService:CircuitBreakerService = new CircuitBreakerService(mockCircuitBreakerRepo)
    val expectedResult:Future[Unit] = Future.successful(())

    def setUpResetCircuitBreakerMock = {
      when(mockCircuitBreakerRepo.resetToDefault).thenReturn(expectedResult)
    }

    def setUpSetCircuitBreakerMock(state: CircuitBreakerState) = {
      when(mockCircuitBreakerRepo.setCircuitBreaker(state)).thenReturn(expectedResult)
    }

    def setCircuitBreakerStatus(state: CircuitBreakerState) = {
      val testStatus = Future.successful(CircuitBreakerStatus(state, None, None))
      when(mockCircuitBreakerRepo.getCircuitBreakerStatus).thenReturn(testStatus)
    }
  }

  "CircuitBreakerService" when {

    "resetting the Circuit Breaker" should {
      "call the Circuit Breaker Repo" in new Setup{
        setUpResetCircuitBreakerMock
        val result: Future[Unit] = circuitBreakerService.resetCircuitBreaker

        verify(mockCircuitBreakerRepo, times(1)).resetToDefault
        result shouldBe expectedResult
      }
    }

    "opening the Circuit Breaker" should {
      "call the Circuit Breaker Repo" in new Setup{
        setUpSetCircuitBreakerMock(Open)
        val result: Future[Unit] = circuitBreakerService.openCircuitBreaker

        verify(mockCircuitBreakerRepo, times(1)).setCircuitBreaker(Open)
        result shouldBe expectedResult
      }
    }

    "closing the Circuit Breaker" should {
      "call the Circuit Breaker Repo" in new Setup {
        setUpSetCircuitBreakerMock(Closed)
        val result: Future[Unit] = circuitBreakerService.closeCircuitBreaker

        verify(mockCircuitBreakerRepo, times(1)).setCircuitBreaker(Closed)
        result shouldBe expectedResult
      }
    }

    "retrieving the Circuit Breaker Status" when {
      "Open" must {
        "return Open" in new Setup {
          setCircuitBreakerStatus(Open)
          val result: Future[CircuitBreakerStatus] = circuitBreakerService.getCircuitBreakerStatus

          result.futureValue shouldBe CircuitBreakerStatus(Open, None, None)
        }
      }

      "Closed" must {
        "return Closed" in new Setup {
          setCircuitBreakerStatus(Closed)
          val result: Future[CircuitBreakerStatus] = circuitBreakerService.getCircuitBreakerStatus

          result.futureValue shouldBe CircuitBreakerStatus(Closed, None, None)
        }
      }
    }
  }
}
