/*
 * Copyright 2022 HM Revenue & Customs
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

import play.api.libs.json.{Format, JsObject, Json, Writes}
import uk.gov.hmrc.auth.core.Enrolments
import uk.gov.hmrc.auth.core.retrieve.Name
import uk.gov.hmrc.entrydeclarationstore.nrs.IdentityData
import uk.gov.hmrc.entrydeclarationstore.reporting.audit.AuditEvent
import uk.gov.hmrc.entrydeclarationstore.reporting.events.Event
import uk.gov.hmrc.entrydeclarationstore.utils.Enums

import java.time.Instant

case class SubmissionHandledData(identityData: Option[IdentityData], eori: String, name: Option[Name], country: Option[String], enrolments: Option[Enrolments])

object SubmissionHandledData {
  implicit val nameWrites: Writes[Name]              = Json.writes[Name]
  implicit val enrolmentWrites: Writes[Enrolments]   = Json.writes[Enrolments]
  implicit val writes: Writes[SubmissionHandledData] = Json.writes[SubmissionHandledData]
}

sealed trait SubmissionHandled {
  val isAmendment: Boolean
  val submissionHandledData: SubmissionHandledData
}

sealed trait FailureType

object SubmissionHandled {
  def createAuditObject(submissionHandledData: SubmissionHandledData, initialObject: JsObject = JsObject.empty): JsObject = {
    implicit val nameWrites: Writes[Name]              = Json.writes[Name]
    implicit val enrolmentWrites: Writes[Enrolments]   = Json.writes[Enrolments]

    val optionalIdentityData = submissionHandledData.identityData match {
      case Some(data) => Json.toJson(data)
      case _ => JsObject.empty
    }

    val optionalNameData = submissionHandledData.name match {
      case Some(data) => Json.toJson(data)
      case _ => JsObject.empty
    }

    val optionalCountryData = submissionHandledData.country match {
      case Some(data) => Json.toJson(data)
      case _ => JsObject.empty
    }

    val optionalEnrolmentsData = submissionHandledData.enrolments match {
      case Some(data) => Json.toJson(data)
      case _ => JsObject.empty
    }

    initialObject ++ Json.obj(
      "eori" -> submissionHandledData.eori,
      "identityData" -> optionalIdentityData,
      "name" -> optionalNameData,
      "country" -> optionalCountryData,
      "enrolments" -> optionalEnrolmentsData
    )
  }

  case class Success(isAmendment: Boolean, submissionHandledData: SubmissionHandledData) extends SubmissionHandled
  case class Failure(isAmendment: Boolean, failureType: FailureType, submissionHandledData: SubmissionHandledData) extends SubmissionHandled

  implicit val eventSources: EventSources[SubmissionHandled] = new EventSources[SubmissionHandled] {
    override def eventFor(timestamp: Instant, report: SubmissionHandled): Option[Event] = None

    override def auditEventFor(report: SubmissionHandled): Option[AuditEvent] = Some {
      report match {
        case Success(true, shd)  => AuditEvent("SuccessfulAmendment", "Successful amendment", createAuditObject(shd))
        case Success(false, shd) => AuditEvent("SuccessfulDeclaration", "Successful declaration", createAuditObject(shd))
        case Failure(true, reason, shd) =>
          AuditEvent("UnsuccessfulAmendment", "Unsuccessful amendment", createAuditObject(shd, Json.obj("failureType" -> reason)))
        case Failure(false, reason, shd) =>
          AuditEvent("UnsuccessfulDeclaration", "Unsuccessful declaration", createAuditObject(shd, Json.obj("failureType" -> reason)))
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
