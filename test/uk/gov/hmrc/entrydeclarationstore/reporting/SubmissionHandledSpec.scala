/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.entrydeclarationstore.reporting

import java.time.Instant

import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsObject, Json}

class SubmissionHandledSpec extends AnyWordSpec {

  val now: Instant  = Instant.now

  val failureType: FailureType = FailureType.MRNMismatchError

  def checkEvents(
    submissionHandled: SubmissionHandled,
    auditType: String,
    transactionName: String,
    detail: JsObject): Unit = {
    "have the correct associated JSON event" in {
      val event = implicitly[EventSources[SubmissionHandled]].eventFor(now, submissionHandled)

      event shouldBe None
    }

    "have the correct associated audit event" in {
      val event = implicitly[EventSources[SubmissionHandled]].auditEventFor(submissionHandled).get

      event.auditType       shouldBe auditType
      event.transactionName shouldBe transactionName
      event.detail          shouldBe detail
    }
  }

  "SubmissionHandled" when {
    "Success(true)" must {
      checkEvents(SubmissionHandled.Success(true), "SuccessfulAmendment", "Successful amendment", JsObject.empty)
    }
    "Success(false)" must {
      checkEvents(SubmissionHandled.Success(false), "SuccessfulDeclaration", "Successful declaration", JsObject.empty)
    }
    "Failure(true, FailureType)" must {
      checkEvents(
        SubmissionHandled.Failure(isAmendment = true, failureType),
        "UnsuccessfulAmendment",
        "Unsuccessful amendment",
        Json.obj("failureType" -> failureType))
    }
    "Failure(false, FailureType)" must {
      checkEvents(
        SubmissionHandled.Failure(isAmendment = false, failureType),
        "UnsuccessfulDeclaration",
        "Unsuccessful declaration",
        Json.obj("failureType" -> failureType))
    }
  }
}
