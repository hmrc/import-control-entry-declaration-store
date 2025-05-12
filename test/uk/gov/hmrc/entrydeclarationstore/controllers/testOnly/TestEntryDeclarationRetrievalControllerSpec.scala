/*
 * Copyright 2025 HM Revenue & Customs
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

import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import play.mvc.Http.MimeTypes
import uk.gov.hmrc.entrydeclarationstore.config.MockAppConfig
import uk.gov.hmrc.entrydeclarationstore.models.{EisSubmissionState, SubmissionIdLookupResult}
import uk.gov.hmrc.entrydeclarationstore.services.MockEntryDeclarationRetrievalService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TestEntryDeclarationRetrievalControllerSpec
    extends AnyWordSpec
    with MockEntryDeclarationRetrievalService
    with MockAppConfig {

  private val controller: TestEntryDeclarationRetrievalController =
    new TestEntryDeclarationRetrievalController(
      Helpers.stubControllerComponents(),
      mockEntryDeclarationRetrievalService,
      mockAppConfig)

  val eori             = "eori"
  val submissionId     = "submissionId"
  val correlationId    = "correlationId"
  val receivedDateTime = "receivedDateTime"
  val submissionIdLookupResult: SubmissionIdLookupResult =
    SubmissionIdLookupResult("dateTime", "housekeepingAt", "SubID", Some("eisSentTime"), EisSubmissionState.Sent)

  val bearerToken: String = "bearerToken"

  "TestEntryDeclarationRetrievalController" when {
    "getting submissionId from eori and correlationId" when {
      "id exists" must {
        "return OK with the submissionId in a JSON object" in {
          MockEntryDeclarationRetrievalService
            .retrieveSubmissionIdAndReceivedDateTime(eori, correlationId)
            .returns(Future.successful(Some(submissionIdLookupResult)))

          val result: Future[Result] = controller.retrieveDataFromMongo(eori, correlationId)(FakeRequest())

          status(result)        shouldBe OK
          contentAsJson(result) shouldBe Json.toJson(submissionIdLookupResult)
          contentType(result)   shouldBe Some(MimeTypes.JSON)
        }
      }
    }
  }
}
