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

import play.api.libs.json.{JsObject, JsString, Json}
import uk.gov.hmrc.entrydeclarationstore.models.MessageType
import uk.gov.hmrc.play.test.UnitSpec

import java.time.Instant

class SubmissionReceivedSpec extends UnitSpec {

  val now: Instant = Instant.now

  def report(clientId: Option[String] = None, applicationId: Option[String] = None, mrn: Option[String] = None): SubmissionReceived =
    SubmissionReceived(
      eori          = "eori",
      correlationId = "correlationId",
      submissionId  = "submissionId",
      messageType   = MessageType.IE313,
      body          = JsObject(Seq("body1" -> JsString("value"))),
      bodyLength    = 123,
      clientInfo    = ClientInfo(ClientType.GGW, clientId, applicationId),
      transportMode = "10",
      amendmentMrn = mrn
    )

  "SubmissionReceived" must {
    "have the correct associated JSON event" when {
      "no applicationId or clientId headers provided" in {
        val event = implicitly[EventSources[SubmissionReceived]].eventFor(now, report()).get

        Json.toJson(event) shouldBe
          Json.parse(s"""
                        |{
                        |    "eventCode" : "ENS_REC",
                        |    "eventTimestamp" : "${now.toString}",
                        |    "submissionId" : "submissionId",
                        |    "eori" : "eori",
                        |    "correlationId" : "correlationId",
                        |    "messageType" : "IE313",
                        |    "detail" : {
                        |        "bodyLength" : 123,
                        |        "transportMode": "10",
                        |        "clientType": "GGW"
                        |    }
                        |}
                        |""".stripMargin)
      }
      "applcationId header provided" in {
        val event =
          implicitly[EventSources[SubmissionReceived]].eventFor(now, report(applicationId = Some("someAppId"))).get

        Json.toJson(event) shouldBe
          Json.parse(s"""
                        |{
                        |    "eventCode" : "ENS_REC",
                        |    "eventTimestamp" : "${now.toString}",
                        |    "submissionId" : "submissionId",
                        |    "eori" : "eori",
                        |    "correlationId" : "correlationId",
                        |    "messageType" : "IE313",
                        |    "detail" : {
                        |        "bodyLength" : 123,
                        |        "transportMode": "10",
                        |        "clientType": "GGW",
                        |        "applicationId": "someAppId"
                        |    }
                        |}
                        |""".stripMargin)
      }

      "clientId header provided" in {
        val event =
          implicitly[EventSources[SubmissionReceived]].eventFor(now, report(clientId = Some("someClientId"))).get

        Json.toJson(event) shouldBe
          Json.parse(s"""
                        |{
                        |    "eventCode" : "ENS_REC",
                        |    "eventTimestamp" : "${now.toString}",
                        |    "submissionId" : "submissionId",
                        |    "eori" : "eori",
                        |    "correlationId" : "correlationId",
                        |    "messageType" : "IE313",
                        |    "detail" : {
                        |        "bodyLength" : 123,
                        |        "transportMode": "10",
                        |        "clientType": "GGW",
                        |        "clientId": "someClientId"
                        |    }
                        |}
                        |""".stripMargin)
      }

      "applicationId and clientId headers provided" in {
        val event =
          implicitly[EventSources[SubmissionReceived]]
            .eventFor(now, report(clientId = Some("someClientId"), applicationId = Some("someAppId")))
            .get

        Json.toJson(event) shouldBe
          Json.parse(s"""
                        |{
                        |    "eventCode" : "ENS_REC",
                        |    "eventTimestamp" : "${now.toString}",
                        |    "submissionId" : "submissionId",
                        |    "eori" : "eori",
                        |    "correlationId" : "correlationId",
                        |    "messageType" : "IE313",
                        |    "detail" : {
                        |        "bodyLength" : 123,
                        |        "transportMode": "10",
                        |        "clientType": "GGW",
                        |        "clientId": "someClientId",
                        |        "applicationId": "someAppId"
                        |    }
                        |}
                        |""".stripMargin)
      }

      "mrn provided" in {
        val event =
          implicitly[EventSources[SubmissionReceived]].eventFor(now, report(mrn = Some("00GB12345678912340"))).get

        Json.toJson(event) shouldBe
          Json.parse(s"""
                        |{
                        |    "eventCode" : "ENS_REC",
                        |    "eventTimestamp" : "${now.toString}",
                        |    "submissionId" : "submissionId",
                        |    "eori" : "eori",
                        |    "correlationId" : "correlationId",
                        |    "messageType" : "IE313",
                        |    "detail" : {
                        |        "bodyLength" : 123,
                        |        "transportMode": "10",
                        |        "clientType": "GGW",
                        |        "amendmentMrn": "00GB12345678912340"
                        |    }
                        |}
                        |""".stripMargin)
      }
    }

    "have the correct associated audit event" in {
      val event = implicitly[EventSources[SubmissionReceived]].auditEventFor(report()).get

      event.auditType       shouldBe "SubmissionReceived"
      event.transactionName shouldBe "ENS submission received"

      Json.toJson(event.detail) shouldBe
        Json.parse("""
                     |{
                     |    "eori" : "eori",
                     |    "correlationId": "correlationId",
                     |    "declarationBody": {
                     |      "body1": "value"
                     |    }
                     |}
                     |""".stripMargin)
    }
  }
}
