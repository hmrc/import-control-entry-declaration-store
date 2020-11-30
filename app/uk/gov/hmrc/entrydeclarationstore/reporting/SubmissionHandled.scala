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

import play.api.libs.json.{Format, JsObject, Json}
import uk.gov.hmrc.entrydeclarationstore.reporting.audit.AuditEvent
import uk.gov.hmrc.entrydeclarationstore.reporting.events.Event
import uk.gov.hmrc.entrydeclarationstore.utils.Enums

sealed trait SubmissionHandled {
  val isAmendment: Boolean
}

sealed trait FailureType

object SubmissionHandled {
  case class Success(isAmendment: Boolean) extends SubmissionHandled
  case class Failure(isAmendment: Boolean, failureType: FailureType) extends SubmissionHandled

  implicit val eventSources: EventSources[SubmissionHandled] = new EventSources[SubmissionHandled] {
    override def eventFor(timestamp: Instant, report: SubmissionHandled): Option[Event] = None

    override def auditEventFor(report: SubmissionHandled): Option[AuditEvent] = Some {
      report match {
        case Success(true)  => AuditEvent("SuccessfulAmendment", "Successful amendment", JsObject.empty)
        case Success(false) => AuditEvent("SuccessfulDeclaration", "Successful declaration", JsObject.empty)
        case Failure(true, reason) =>
          AuditEvent("UnsuccessfulAmendment", "Unsuccessful amendment", Json.obj("failureType" -> reason))
        case Failure(false, reason) =>
          AuditEvent("UnsuccessfulDeclaration", "Unsuccessful declaration", Json.obj("failureType" -> reason))
      }
    }
  }
}

object FailureType {
  case object MRNMismatchError extends FailureType
  case object ValidationErrors extends FailureType
  case object EORIMismatchError extends FailureType
  case object InternalServerError extends FailureType
  implicit val formats: Format[FailureType] = Enums.format[FailureType]
}
