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

import play.api.http.MimeTypes
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.entrydeclarationstore.models.HousekeepingStatus
import uk.gov.hmrc.entrydeclarationstore.services.MockHousekeepingService
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class HousekeepingControllerSpec extends UnitSpec with MockHousekeepingService {

  val controller = new HousekeepingController(Helpers.stubControllerComponents(), mockHousekeepingService)

  private def houseKeepingStatusJson(value: Boolean) =
    Json.parse(s"""{"housekeeping": $value}""".stripMargin)

  "HousekeepingController" when {
    "getting housekeeping state" when {
      "housekeeping is on" must {
        "return 200 with housekeeping as true" in {
          MockHousekeepingService.getHousekeepingStatus returns HousekeepingStatus.On

          val result = controller.getStatus()(FakeRequest())

          status(result)        shouldBe OK
          contentAsJson(result) shouldBe houseKeepingStatusJson(true)
          contentType(result)   shouldBe Some(MimeTypes.JSON)
        }
      }
    }

    "housekeeping is off" must {
      "return 200 with housekeeping as false" in {
        MockHousekeepingService.getHousekeepingStatus returns HousekeepingStatus.Off

        val result = controller.getStatus()(FakeRequest())

        status(result)        shouldBe OK
        contentAsJson(result) shouldBe houseKeepingStatusJson(false)
        contentType(result)   shouldBe Some(MimeTypes.JSON)
      }

      "housekeeping is unknown" must {
        "return 500" in {
          MockHousekeepingService.getHousekeepingStatus returns HousekeepingStatus.Unknown

          val result = controller.getStatus()(FakeRequest())

          status(result) shouldBe INTERNAL_SERVER_ERROR
        }
      }
    }

    "setting housekeeping state" when {
      "setting true is successful" must {
        "return 204" in {
          val housekeepingValue = true
          MockHousekeepingService.enableHousekeeping(housekeepingValue) returns true

          val result = controller.setStatus()(FakeRequest().withBody(houseKeepingStatusJson(housekeepingValue)))

          status(result) shouldBe NO_CONTENT
        }
      }
      "setting false is successful" must {
        "return 204" in {
          val housekeepingValue = false
          MockHousekeepingService.enableHousekeeping(housekeepingValue) returns true

          val result = controller.setStatus()(FakeRequest().withBody(houseKeepingStatusJson(housekeepingValue)))

          status(result) shouldBe NO_CONTENT
        }
      }

      "setting fails" must {
        "return 500" in {
          val housekeepingValue = true
          MockHousekeepingService.enableHousekeeping(housekeepingValue) returns false

          val result = controller.setStatus()(FakeRequest().withBody(houseKeepingStatusJson(housekeepingValue)))

          status(result) shouldBe INTERNAL_SERVER_ERROR
        }
      }

      "JSON has incorrect form" must {
        "return 400" in {
          val badJson = Json.parse("""{"somethingElse": true}""".stripMargin)

          val result = controller.setStatus()(FakeRequest().withBody(badJson))

          status(result) shouldBe BAD_REQUEST
        }
      }
    }
  }

}