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

import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers.{contentType, _}
import play.api.test.{FakeRequest, Helpers}
import play.mvc.Http.MimeTypes
import uk.gov.hmrc.entrydeclarationstore.models.{ReplayResult, TransportCount, UndeliveredCounts}
import uk.gov.hmrc.entrydeclarationstore.orchestrators.MockReplayOrchestrator
import uk.gov.hmrc.entrydeclarationstore.services.MockSubmissionReplayService
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}

class ReplayControllerSpec extends UnitSpec with MockReplayOrchestrator with MockSubmissionReplayService {
  val controller =
    new ReplayController(Helpers.stubControllerComponents(), mockReplayOrchestrator, mockSubmissionReplayService)
  val limit    = 100
  val replayId = "replayId"

  val replayIdJson: JsValue = Json.parse(s"""{"replayId": "$replayId"}""")

  val replayJson: JsValue = Json.parse(s"""
                                          |{
                                          |  "limit": $limit
                                          |}
                                          |""".stripMargin)

  val fakeRequest: FakeRequest[JsValue] = FakeRequest().withBody(replayJson)

  val ignoredReplayResultFuture: Future[ReplayResult] = Promise[ReplayResult].future

  "ReplayController startReplay" should {
    "return Accepted" when {
      "request with limit defined is handled successfully" in {
        MockReplayOrchestrator.startReplay(Some(limit)) returns Future.successful(replayId) -> ignoredReplayResultFuture

        val result = controller.startReplay(fakeRequest)

        status(result)        shouldBe ACCEPTED
        contentType(result)   shouldBe Some("application/json")
        contentAsJson(result) shouldBe replayIdJson
      }

      "request with no limit defined is handled successfully" in {
        MockReplayOrchestrator.startReplay(None) returns Future.successful(replayId) -> ignoredReplayResultFuture
        val noLimitReplayJson: JsValue        = Json.parse("{}")
        val fakeRequest: FakeRequest[JsValue] = FakeRequest().withBody(noLimitReplayJson)
        val result                            = controller.startReplay(fakeRequest)

        status(result)        shouldBe ACCEPTED
        contentType(result)   shouldBe Some("application/json")
        contentAsJson(result) shouldBe replayIdJson
      }
    }

    "return BAD_REQUEST" when {
      "request body cannot be read as an ReplayLimit object" in {
        val replayLimitJson = Json.parse("""
                                           |{
                                           |  "limit": "xxx"
                                           |}
                                           |""".stripMargin)

        val fakeRequest = FakeRequest().withBody(replayLimitJson)

        val result = controller.startReplay(fakeRequest)

        status(result)      shouldBe BAD_REQUEST
        contentType(result) shouldBe None
      }
    }
  }

  "ReplayController getUndeliveredCounts" should {
    "work" in {
      val undeliveredCounts = UndeliveredCounts(totalCount = 2, Some(Seq(TransportCount("11", 2))))
      MockSubmissionReplayService.getUndeliveredCounts returns Future.successful(undeliveredCounts)

      val result = controller.getUndeliveredCounts(FakeRequest())

      status(result)      shouldBe OK
      contentType(result) shouldBe Some(MimeTypes.JSON)
      contentAsJson(result) shouldBe
        Json.parse("""
                     |{
                     |"totalCount": 2,
                     |"transportCounts": [
                     | { "transportMode": "11", "count": 2}
                     |]
                     |}
                     |""".stripMargin)
    }
  }
}
