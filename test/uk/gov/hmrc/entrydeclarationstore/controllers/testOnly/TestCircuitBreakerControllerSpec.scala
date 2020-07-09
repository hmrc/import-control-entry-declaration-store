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

package uk.gov.hmrc.entrydeclarationstore.controllers.testOnly

import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.entrydeclarationstore.services.MockCircuitBreakerService
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class TestCircuitBreakerControllerSpec extends UnitSpec with MockCircuitBreakerService {

  val controller = new TestCircuitBreakerController(Helpers.stubControllerComponents(), mockCircuitBreakerService)

  "TestCircuitBreakerController" when {
    "opening the circuit breaker" must {
      "return 204" when {
        "successful" in {
          MockCircuitBreakerService.openCircuitBreaker returns (): Unit

          val result = controller.openCircuitBreaker()(FakeRequest())

          status(result) shouldBe NO_CONTENT
        }
      }
    }
  }
  "resetting the circuit breaker" must {
    "return 204" when {
      "successful" in {
        MockCircuitBreakerService.resetCircuitBreaker returns (): Unit

        val result = controller.resetCircuitBreaker()(FakeRequest())

        status(result) shouldBe NO_CONTENT
      }
    }
  }
}