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

import java.time.{Clock, Instant}

import com.kenshoo.play.metrics.Metrics
import javax.inject.{Inject, Singleton}
import play.api.mvc.{Action, ControllerComponents, Request}
import uk.gov.hmrc.entrydeclarationstore.logging.LoggingContext
import uk.gov.hmrc.entrydeclarationstore.nrs.{NRSMetadata, NRSService, NRSSubmission}
import uk.gov.hmrc.entrydeclarationstore.reporting.audit.{AuditEvent, AuditHandler}
import uk.gov.hmrc.entrydeclarationstore.services.{AuthService, EntryDeclarationStore, MRNMismatchError}
import uk.gov.hmrc.entrydeclarationstore.utils.ChecksumUtils.StringWithSha256
import uk.gov.hmrc.entrydeclarationstore.utils.{EoriUtils, EventLogger, Timer}
import uk.gov.hmrc.entrydeclarationstore.validation.ValidationErrors
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext
import scala.xml.Elem

@Singleton
class EntryDeclarationSubmissionController @Inject()(
  cc: ControllerComponents,
  service: EntryDeclarationStore,
  val authService: AuthService,
  nrsService: NRSService,
  auditHandler: AuditHandler,
  clock: Clock,
  override val metrics: Metrics
)(implicit ec: ExecutionContext)
    extends AuthorisedController(cc, auditHandler)
    with Timer
    with EventLogger {

  override def eoriCorrectForRequest[A](request: Request[A], eori: String): Boolean =
    request.body match {
      case payload: String => eori == EoriUtils.eoriFromXmlString(payload)
      case _               => false
    }

  def xmlSuccessResponse(correlationId: String): Elem =
    // @formatter:off
    <ns:SuccessResponse TestInLive="false"
                        xmlns:ns="http://www.hmrc.gov.uk/successresponse/2"
                        xmlns:xd="http://www.w3.org/2000/09/xmldsig#">
      <ns:ResponseData>
        <CorrelationId>{correlationId}</CorrelationId>
      </ns:ResponseData>
    </ns:SuccessResponse>
  // @formatter:on

  val postSubmission: Action[String] = handleSubmission(None)

  val postSubmissionTestOnly: Action[String] = postSubmission

  def putAmendment(mrn: String): Action[String] = handleSubmission(Some(mrn))

  private def handleSubmission(mrn: Option[String]) = authorisedAction(mrn).async(parse.tolerantText) { implicit request =>
    val receivedDateTime = Instant.now(clock)

    service
      .handleSubmission(request.userDetails.eori, request.body, request.mrn, receivedDateTime, request.userDetails.clientType)
      .map {
        case Left(failure) =>
          auditHandler.audit(AuditEvent.auditFailure(request.mrn.isDefined))
          failure.error match {
            case _: ValidationErrors => BadRequest(failure.toXml)
            case MRNMismatchError    => BadRequest(failure.toXml)
            case _                   => InternalServerError(failure.toXml)
          }
        case Right(success) =>
          submitToNRSIfReqd(request, receivedDateTime)
          auditHandler.audit(AuditEvent.auditSuccess(request.mrn.isDefined))
          Ok(xmlSuccessResponse(success.correlationId))
      }
  }

  private def submitToNRSIfReqd(request: UserRequest[String], receivedDateTime: Instant)(
    implicit hc: HeaderCarrier): Unit =
    request.userDetails.identityData.foreach { identityData =>
      implicit val lc: LoggingContext = LoggingContext(eori = Some(request.userDetails.eori))

      val submission = NRSSubmission(
        request.body,
        NRSMetadata(receivedDateTime, request.userDetails.eori, identityData, request, request.body.calculateSha256))

      nrsService.submit(submission)
    }
}
