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

import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.WordSpec
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.entrydeclarationstore.connectors.EISSendFailure
import uk.gov.hmrc.entrydeclarationstore.models.MessageType

import java.time.Instant

class SubmissionSentToEISSpec extends WordSpec {

  val now: Instant = Instant.now

  def report(failure: Option[EISSendFailure]): SubmissionSentToEIS = SubmissionSentToEIS(
    eori          = "eori",
    correlationId = "correlationId",
    submissionId  = "submissionId",
    messageType   = MessageType.IE313,
    failure
  )

  "SubmissionSentToEIS" must {
    "have the correct associated JSON event" when {
      "successfully sent" in {
        val event = implicitly[EventSources[SubmissionSentToEIS]].eventFor(now, report(None)).get

        Json.toJson(event) shouldBe
          Json.parse(s"""
                        |{
                        |    "eventCode" : "ENS_TO_EIS",
                        |    "eventTimestamp" : "${now.toString}",
                        |    "submissionId" : "submissionId",
                        |    "eori" : "eori",
                        |    "correlationId" : "correlationId",
                        |    "messageType" : "IE313"
                        |}
                        |""".stripMargin)
      }

      "send fails owing to http error" in {
        val event = implicitly[EventSources[SubmissionSentToEIS]]
          .eventFor(now, report(Some(EISSendFailure.ErrorResponse(503))))
          .get

        Json.toJson(event) shouldBe
          Json.parse(s"""
                        |{
                        |    "eventCode" : "ENS_TO_EIS_FAILED",
                        |    "eventTimestamp" : "${now.toString}",
                        |    "submissionId" : "submissionId",
                        |    "eori" : "eori",
                        |    "correlationId" : "correlationId",
                        |    "messageType" : "IE313",
                        |    "detail" : {
                        |        "failure" : {
                        |           "type": "ERROR_RESPONSE",
                        |           "status": 503
                        |        }
                        |    }
                        |}
                        |""".stripMargin)
      }

      "send fails owing to exception" in {
        val event = implicitly[EventSources[SubmissionSentToEIS]]
          .eventFor(now, report(Some(EISSendFailure.ExceptionThrown)))
          .get

        Json.toJson(event) shouldBe
          Json.parse(s"""
                        |{
                        |    "eventCode" : "ENS_TO_EIS_FAILED",
                        |    "eventTimestamp" : "${now.toString}",
                        |    "submissionId" : "submissionId",
                        |    "eori" : "eori",
                        |    "correlationId" : "correlationId",
                        |    "messageType" : "IE313",
                        |    "detail" : {
                        |        "failure" : {
                        |           "type": "EXCEPTION_THROWN"
                        |        }
                        |    }
                        |}
                        |""".stripMargin)
      }

      "send fails owing to timeout" in {
        val event = implicitly[EventSources[SubmissionSentToEIS]]
          .eventFor(now, report(Some(EISSendFailure.Timeout)))
          .get

        Json.toJson(event) shouldBe
          Json.parse(s"""
                        |{
                        |    "eventCode" : "ENS_TO_EIS_FAILED",
                        |    "eventTimestamp" : "${now.toString}",
                        |    "submissionId" : "submissionId",
                        |    "eori" : "eori",
                        |    "correlationId" : "correlationId",
                        |    "messageType" : "IE313",
                        |    "detail" : {
                        |        "failure" : {
                        |           "type": "TIMEOUT"
                        |        }
                        |    }
                        |}
                        |""".stripMargin)
      }

      "send fails owing to circuit breaker" in {
        val event = implicitly[EventSources[SubmissionSentToEIS]]
          .eventFor(now, report(Some(EISSendFailure.TrafficSwitchNotFlowing)))
          .get

        Json.toJson(event) shouldBe
          Json.parse(s"""
                        |{
                        |    "eventCode" : "ENS_TO_EIS_FAILED",
                        |    "eventTimestamp" : "${now.toString}",
                        |    "submissionId" : "submissionId",
                        |    "eori" : "eori",
                        |    "correlationId" : "correlationId",
                        |    "messageType" : "IE313",
                        |    "detail" : {
                        |        "failure" : {
                        |           "type": "TRAFFIC_SWITCH_NOT_FLOWING"
                        |        }
                        |    }
                        |}
                        |""".stripMargin)
      }
    }

    "have the correct associated audit event" when {
      "successfully sent" in {
        val event = implicitly[EventSources[SubmissionSentToEIS]].auditEventFor(report(None)).get

        event.auditType       shouldBe "SubmissionForwarded"
        event.transactionName shouldBe "ENS submission forwarded to EIS"

        Json.toJson(event.detail) shouldBe
          Json.parse("""
                       |{
                       |    "eori" : "eori",
                       |    "correlationId": "correlationId"
                       |}
                       |""".stripMargin)
      }
      "send fails" in {
        val event = implicitly[EventSources[SubmissionSentToEIS]]
          .auditEventFor(report(Some(EISSendFailure.ErrorResponse(503))))
          .get

        event.auditType       shouldBe "SubmissionUndelivered"
        event.transactionName shouldBe "ENS Submission failed to forward to EIS"
        event.detail          shouldBe JsObject.empty
      }
    }
  }
}
