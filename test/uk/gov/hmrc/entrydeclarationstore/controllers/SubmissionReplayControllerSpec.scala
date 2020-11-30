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

import play.api.libs.json.{JsString, JsValue, Json}
import play.api.mvc.Result
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import play.mvc.Http.MimeTypes
import uk.gov.hmrc.entrydeclarationstore.config.MockAppConfig
import uk.gov.hmrc.entrydeclarationstore.models.{BatchReplayError, BatchReplayResult, ReplaySubmissionIds}
import uk.gov.hmrc.entrydeclarationstore.services.MockSubmissionReplayService
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SubmissionReplayControllerSpec extends UnitSpec with MockSubmissionReplayService with MockAppConfig {
  val submissionIds: ReplaySubmissionIds = ReplaySubmissionIds(Seq("subId1", "subId2"))
  val request: FakeRequest[JsValue] =
    FakeRequest().withBody(Json.parse("""{"submissionIds": ["subId1", "subId2"]}"""))

  private val controller =
    new SubmissionReplayController(Helpers.stubControllerComponents(), mockSubmissionReplayService, mockAppConfig)

  "SubmissionReplayController.replay" when {
    "replay successful" should {
      "return 200 with the success and failure counts" in {
        val replayResult = BatchReplayResult(2, 3)
        MockAppConfig.replayBatchSizeLimit.returns(3)
        MockSubmissionReplayService
          .replaySubmissions(submissionIds.submissionIds)
          .returns(Future.successful(Right(replayResult)))

        val result: Future[Result] = controller.replay(request)

        status(result)        shouldBe OK
        contentAsJson(result) shouldBe Json.toJson(replayResult)
        contentType(result)   shouldBe Some(MimeTypes.JSON)
      }
    }
    "replay unsuccessful" should {
      "return 500" in {
        val replayError = Left(BatchReplayError.EISEventError)
        MockAppConfig.replayBatchSizeLimit.returns(3)
        MockSubmissionReplayService
          .replaySubmissions(submissionIds.submissionIds)
          .returns(Future.successful(replayError))

        val result: Future[Result] = controller.replay(request)

        status(result)      shouldBe INTERNAL_SERVER_ERROR
        contentType(result) shouldBe None
      }
    }
    "unable to parse list" should {
      "return 400" in {
        val request                = FakeRequest().withBody(JsString("XXX"))
        val result: Future[Result] = controller.replay(request)

        status(result)      shouldBe BAD_REQUEST
        contentType(result) shouldBe None
      }
    }
    "list length exceeds the limit" should {
      "return 400" in {
        MockAppConfig.replayBatchSizeLimit.returns(1) //List has 2 entries.
        val result: Future[Result] = controller.replay(request)

        status(result)      shouldBe BAD_REQUEST
        contentType(result) shouldBe None
      }
    }
  }
}
