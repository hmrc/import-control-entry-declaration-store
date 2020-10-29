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
import java.time.temporal.ChronoUnit

import play.api.http.MimeTypes
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.entrydeclarationstore.models.{TrafficSwitchState, TrafficSwitchStatus}
import uk.gov.hmrc.entrydeclarationstore.services.MockTrafficSwitchService
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class TrafficSwitchControllerSpec extends UnitSpec with MockTrafficSwitchService {

  val controller = new TrafficSwitchController(Helpers.stubControllerComponents(), mockTrafficSwitchService)

  val timeTrafficStopped: Instant = Instant.now
  val timeTrafficStart: Instant = timeTrafficStopped.plus(1, ChronoUnit.MINUTES)

  private def trafficSwitchJson(value: TrafficSwitchState) =
    Json.parse(s"""{"isTrafficFlowing": "$value",
                  | "lastTrafficStopped": "$timeTrafficStopped",
                  | "lastTrafficStarted": "$timeTrafficStart"}""".stripMargin)

  "TrafficSwitchController" when {
    "getting traffic switch state" must {
      "return 200 with the value" when {
        "the traffic switch is not flowing" in {
          MockTrafficSwitchService.getTrafficSwitchStatus
            .returns(TrafficSwitchStatus(TrafficSwitchState.NotFlowing, Some(timeTrafficStopped), Some(timeTrafficStart)))

          val result = controller.getStatus()(FakeRequest())

          status(result)        shouldBe OK
          contentAsJson(result) shouldBe trafficSwitchJson(TrafficSwitchState.NotFlowing)
          contentType(result)   shouldBe Some(MimeTypes.JSON)
        }
        "the traffic switch is flowing" in {
          MockTrafficSwitchService.getTrafficSwitchStatus
            .returns(TrafficSwitchStatus(TrafficSwitchState.Flowing, Some(timeTrafficStopped), Some(timeTrafficStart)))

          val result = controller.getStatus()(FakeRequest())

          status(result)        shouldBe OK
          contentAsJson(result) shouldBe trafficSwitchJson(TrafficSwitchState.Flowing)
          contentType(result)   shouldBe Some(MimeTypes.JSON)
        }
      }
    }
    "start the traffic flow" must {
      "return 204" when {
        "successful" in {
          MockTrafficSwitchService.startTrafficFlow returns ((): Unit)

          val result = controller.startTrafficFlow()(FakeRequest())

          status(result) shouldBe NO_CONTENT
        }
      }
    }
  }
}
