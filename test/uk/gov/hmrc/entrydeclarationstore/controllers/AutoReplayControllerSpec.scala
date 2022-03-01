/*
 * Copyright 2022 HM Revenue & Customs
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

import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.wordspec.AnyWordSpec
import play.api.http.MimeTypes
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.entrydeclarationstore.models.AutoReplayStatus
import uk.gov.hmrc.entrydeclarationstore.services.MockAutoReplayService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AutoReplayControllerSpec extends AnyWordSpec with MockAutoReplayService {

  val controller = new AutoReplayController(Helpers.stubControllerComponents(), mockAutoReplayService)

  private def autoReplayStatusJson(value: Boolean) =
    Json.parse(s"""{"autoReplay": $value}""".stripMargin)

  "AutoReplayController" when {
    "getting autoReplay state" when {
      "autoReplay is on" must {
        "return 200 with autoReplay as true" in {
          MockAutoReplayService.getStatus returns Future.successful(AutoReplayStatus.On(None))
          val result = controller.getStatus()(FakeRequest())

          status(result)        shouldBe OK
          contentAsJson(result) shouldBe autoReplayStatusJson(true)
          contentType(result)   shouldBe Some(MimeTypes.JSON)
        }
      }
    }

    "autoReplay is off" must {
      "return 200 with autoReplay as false" in {
        MockAutoReplayService.getStatus returns Future.successful(AutoReplayStatus.Off(None))
        val result = controller.getStatus()(FakeRequest())

        status(result)        shouldBe OK
        contentAsJson(result) shouldBe autoReplayStatusJson(false)
        contentType(result)   shouldBe Some(MimeTypes.JSON)
      }
    }

    "setting autoReplay state" when {
      "setting true is successful" must {
        "return 204" in {
          MockAutoReplayService.start() returns Future.unit
          val result = controller.start()(FakeRequest())

          status(result) shouldBe NO_CONTENT
        }
      }
      "setting false is successful" must {
        "return 204" in {
          MockAutoReplayService.stop() returns Future.unit
          val result = controller.stop()(FakeRequest())

          status(result) shouldBe NO_CONTENT
        }
      }
    }

  }

}
