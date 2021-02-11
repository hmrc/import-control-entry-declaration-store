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

import play.api.libs.json._
import uk.gov.hmrc.entrydeclarationstore.models.MessageType
import uk.gov.hmrc.entrydeclarationstore.reporting.audit.AuditEvent
import uk.gov.hmrc.entrydeclarationstore.reporting.events.{Event, EventCode}

import java.time.Instant
import scala.collection.Seq

case class SubmissionReceived(
  eori: String,
  correlationId: String,
  submissionId: String,
  messageType: MessageType,
  body: JsValue,
  bodyLength: Int,
  transportMode: String,
  clientInfo: ClientInfo,
  amendmentMrn: Option[String]
) extends Report

object SubmissionReceived {
  implicit val eventSources: EventSources[SubmissionReceived] = new EventSources[SubmissionReceived] {

    override def eventFor(timestamp: Instant, report: SubmissionReceived): Option[Event] = {
      import report._

      val event = Event(
        eventCode      = EventCode.ENS_REC,
        eventTimestamp = timestamp,
        submissionId   = submissionId,
        eori           = eori,
        correlationId  = correlationId,
        messageType    = messageType,
        detail = Some(
          JsObject(
            Seq(
              "clientType"                                 -> Json.toJson(clientInfo.clientType),
              "transportMode"                              -> JsString(transportMode),
              "bodyLength"                                 -> JsNumber(bodyLength)) ++
              clientInfo.applicationId.map("applicationId" -> JsString(_)) ++
              clientInfo.clientId.map("clientId"           -> JsString(_)) ++
              amendmentMrn.map("amendmentMrn"              -> JsString(_))))
      )

      Some(event)
    }

    override def auditEventFor(report: SubmissionReceived): Option[AuditEvent] = {
      import report._
      val auditEvent = AuditEvent(
        auditType       = "SubmissionReceived",
        transactionName = "ENS submission received",
        JsObject(Seq("eori" -> JsString(eori), "correlationId" -> JsString(correlationId), "declarationBody" -> body))
      )

      Some(auditEvent)
    }
  }
}
