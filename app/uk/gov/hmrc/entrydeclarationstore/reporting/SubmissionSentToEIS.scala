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

package uk.gov.hmrc.entrydeclarationstore.reporting

import java.time.Instant

import play.api.libs.json._
import uk.gov.hmrc.entrydeclarationstore.connectors.EISSendFailure
import uk.gov.hmrc.entrydeclarationstore.models.MessageType
import uk.gov.hmrc.entrydeclarationstore.reporting.audit.AuditEvent
import uk.gov.hmrc.entrydeclarationstore.reporting.events.{Event, EventCode}

case class SubmissionSentToEIS(
  eori: String,
  correlationId: String,
  submissionId: String,
  messageType: MessageType,
  failure: Option[EISSendFailure])
    extends Report

object SubmissionSentToEIS {
  implicit val eventSources: EventSources[SubmissionSentToEIS] = new EventSources[SubmissionSentToEIS] {
    override def eventFor(timestamp: Instant, report: SubmissionSentToEIS): Option[Event] = {
      import report._

      val (eventCode, failureJson) = report.failure match {
        case None    => (EventCode.ENS_TO_EIS, None)
        case Some(f) => (EventCode.ENS_TO_EIS_FAILED, Some(JsObject(Seq("failure" -> Json.toJson(f)))))
      }

      val event = Event(
        eventCode      = eventCode,
        eventTimestamp = timestamp,
        submissionId   = submissionId,
        eori           = eori,
        correlationId  = correlationId,
        messageType    = messageType,
        detail         = failureJson
      )

      Some(event)
    }

    override def auditEventFor(report: SubmissionSentToEIS): Option[AuditEvent] = {
      import report._
      val auditEvent = AuditEvent(
        auditType       = "submissionForwarded",
        transactionName = "ENS submission forwarded to EIS",
        JsObject(Seq("eori" -> JsString(eori), "correlationId" -> JsString(correlationId)))
      )

      Some(auditEvent)
    }
  }
}
