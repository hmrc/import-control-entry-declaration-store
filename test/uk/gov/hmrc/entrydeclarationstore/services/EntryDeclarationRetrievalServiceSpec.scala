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

import org.scalatest.concurrent.ScalaFutures
import play.api.libs.json.JsString
import uk.gov.hmrc.entrydeclarationstore.models.SubmissionIdLookupResult
import uk.gov.hmrc.entrydeclarationstore.repositories.MockEntryDeclarationRepo
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class EntryDeclarationRetrievalServiceSpec extends UnitSpec with MockEntryDeclarationRepo with ScalaFutures {

  val entryDeclarationRetrievalService = new EntryDeclarationRetrievalService(mockEntryDeclarationRepo)

  val eori              = "eori"
  val submissionId      = "submissionId"
  val correlationId     = "correlationId"
  val payload: JsString = JsString("payload")
  val submissionIdAndReceivedDateTime: SubmissionIdLookupResult =
    SubmissionIdLookupResult("dateTime", "housekeepingAt", "SubId", Some("eisSentTime"))

  "EntryDeclarationRetrievalService" when {

    "retrieving submissionId/receivedDateTime from eori & correlationId" when {
      "successfully found" must {
        "return it" in {
          MockEntryDeclarationRepo
            .lookupSubmissionIdAndReceivedDateTime(eori, correlationId)
            .returns(Future.successful(Some(submissionIdAndReceivedDateTime)))

          entryDeclarationRetrievalService
            .retrieveSubmissionIdAndReceivedDateTime(eori, correlationId)
            .futureValue shouldBe Some(submissionIdAndReceivedDateTime)
        }
      }

      "not found" must {
        "return None" in {
          MockEntryDeclarationRepo
            .lookupSubmissionIdAndReceivedDateTime(eori, correlationId)
            .returns(Future.successful(None))

          entryDeclarationRetrievalService
            .retrieveSubmissionIdAndReceivedDateTime(eori, correlationId)
            .futureValue shouldBe None
        }
      }
    }

    "retrieving payload from submissionId" when {
      "successfully found" must {
        "return it" in {
          MockEntryDeclarationRepo
            .lookupEntryDeclaration(submissionId)
            .returns(Future.successful(Some(payload)))

          entryDeclarationRetrievalService.retrieveSubmission(submissionId).futureValue shouldBe Some(payload)
        }
      }

      "not found" must {
        "return None" in {
          MockEntryDeclarationRepo
            .lookupEntryDeclaration(submissionId)
            .returns(Future.successful(None))

          entryDeclarationRetrievalService.retrieveSubmission(submissionId).futureValue shouldBe None
        }
      }
    }
  }
}
