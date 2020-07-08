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

package uk.gov.hmrc.entrydeclarationstore.controllers

import java.time.Instant

import play.api.http.MimeTypes
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.entrydeclarationstore.models.{CircuitBreakerState, CircuitBreakerStatus}
import uk.gov.hmrc.entrydeclarationstore.services.MockCircuitBreakerService
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class CircuitBreakerControllerSpec extends UnitSpec with MockCircuitBreakerService {

  val controller = new CircuitBreakerController(Helpers.stubControllerComponents(), mockCircuitBreakerService)

  val time: Instant = Instant.now

  private def circuitBreakerJson(value: CircuitBreakerState) =
    Json.parse(s"""{"circuitBreakerState": "$value",
                  | "lastOpened": "$time",
                  | "lastClosed": "$time"}""".stripMargin)

  "CircuitBreakerController" when {
    "getting circuit breaker state" must {
      "return 200 with the value" when {
        "the circuit breaker is Open" in {
          MockCircuitBreakerService.getCircuitBreakerStatus
            .returns(CircuitBreakerStatus(CircuitBreakerState.Open, Some(time), Some(time)))

          val result = controller.getStatus()(FakeRequest())

          status(result)        shouldBe OK
          contentAsJson(result) shouldBe circuitBreakerJson(CircuitBreakerState.Open)
          contentType(result)   shouldBe Some(MimeTypes.JSON)
        }
        "the circuit breaker is Closed" in {
          MockCircuitBreakerService.getCircuitBreakerStatus
            .returns(CircuitBreakerStatus(CircuitBreakerState.Closed, Some(time), Some(time)))

          val result = controller.getStatus()(FakeRequest())

          status(result)        shouldBe OK
          contentAsJson(result) shouldBe circuitBreakerJson(CircuitBreakerState.Closed)
          contentType(result)   shouldBe Some(MimeTypes.JSON)
        }
      }
    }
    "closing the circuit breaker" must {
      "return 204" when {
        "successful" in {
          MockCircuitBreakerService.closeCircuitBreaker returns (): Unit

          val result = controller.closeCircuitBreaker()(FakeRequest())

          status(result) shouldBe NO_CONTENT
        }
      }
    }
  }
}
