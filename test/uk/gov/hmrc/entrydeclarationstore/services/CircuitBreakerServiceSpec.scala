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
import org.mockito.Matchers
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.entrydeclarationstore.repositories.CircuitBreakerRepo
import uk.gov.hmrc.entrydeclarationstore.models.CircuitBreakerState.{Closed, Open}
import uk.gov.hmrc.entrydeclarationstore.models.{CircuitBreakerState, CircuitBreakerStatus}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class CircuitBreakerServiceSpec extends UnitSpec with MockitoSugar {

  class Setup(state: CircuitBreakerState) {
    val mockCircuitBreakerRepo = mock[CircuitBreakerRepo]
    val circuitBreakerService = new CircuitBreakerService(mockCircuitBreakerRepo)

    val testStatus = Future.successful(CircuitBreakerStatus(state, None, None))
    val expectedResult = Future.successful()


    when(mockCircuitBreakerRepo.resetToDefault).thenReturn(expectedResult)
    when(mockCircuitBreakerRepo.setCircuitBreaker(Matchers.any())).thenReturn(expectedResult)
    when(mockCircuitBreakerRepo.getCircuitBreakerStatus).thenReturn(testStatus)
  }

  "CircuitBreakerService" when {

    "resetting the Circuit Breaker" should {
      "call the Circuit Breaker Repo" in new Setup(Closed) {
        val result = circuitBreakerService.resetCircuitBreaker

        verify(mockCircuitBreakerRepo, times(1)).resetToDefault
        result shouldBe expectedResult

      }
    }

    "opening the Circuit Breaker" should {
      "call the Circuit Breaker Repo" in new Setup(Closed) {
        val result = circuitBreakerService.openCircuitBreaker

        verify(mockCircuitBreakerRepo, times(1)).setCircuitBreaker(Open)
        result shouldBe expectedResult
      }
    }

    "closing the Circuit Breaker" should {
      "call the Circuit Breaker Repo" in new Setup(Closed) {
        val result = circuitBreakerService.closeCircuitBreaker

        verify(mockCircuitBreakerRepo, times(1)).setCircuitBreaker(Closed)
        result shouldBe expectedResult
      }
    }

    "retrieving the Circuit Breaker Status" when {
      "Open" must {
        "return Open" in new Setup(Open) {
          val result = circuitBreakerService.getCircuitBreakerStatus
          result shouldBe testStatus
        }
      }
      "Closed" must {
        "return Closed" in new Setup(Closed) {
          val result = circuitBreakerService.getCircuitBreakerStatus
          result shouldBe testStatus
        }
      }
    }
  }
}
