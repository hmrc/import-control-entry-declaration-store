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

package uk.gov.hmrc.entrydeclarationstore.controllers

import akka.util.ByteString
import com.kenshoo.play.metrics.Metrics
import play.api.mvc.{Action, ControllerComponents, Request}
import uk.gov.hmrc.entrydeclarationstore.logging.LoggingContext
import uk.gov.hmrc.entrydeclarationstore.models.RawPayload
import uk.gov.hmrc.entrydeclarationstore.nrs.{NRSMetadata, NRSService, NRSSubmission}
import uk.gov.hmrc.entrydeclarationstore.reporting.{FailureType, ReportSender, SubmissionHandled}
import uk.gov.hmrc.entrydeclarationstore.services.{AuthService, EntryDeclarationStore, MRNMismatchError}
import uk.gov.hmrc.entrydeclarationstore.utils.ChecksumUtils._
import uk.gov.hmrc.entrydeclarationstore.utils.{EoriUtils, EventLogger, Timer}
import uk.gov.hmrc.entrydeclarationstore.validation.ValidationErrors
import uk.gov.hmrc.http.HeaderCarrier

import java.nio.charset.StandardCharsets
import java.time.{Clock, Instant}
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import scala.xml.Elem

@Singleton
class EntryDeclarationSubmissionController @Inject()(
  cc: ControllerComponents,
  service: EntryDeclarationStore,
  val authService: AuthService,
  nrsService: NRSService,
  reportSender: ReportSender,
  clock: Clock,
  override val metrics: Metrics
)(implicit ec: ExecutionContext)
    extends AuthorisedController(cc)
    with Timer
    with EventLogger {

  def eoriCorrectForRequest(mrn: Option[String]): (Request[_], String) => Boolean =
    (request, eori) =>
      request.body match {
        case payload: ByteString =>
          if (EoriUtils.eoriFromXmlString(payload.decodeString(StandardCharsets.UTF_8)) == eori) { true } else {
            implicit val lc: LoggingContext           = LoggingContext()
            implicit val headerCarrier: HeaderCarrier = hc(request)
            reportSender.sendReport(
              SubmissionHandled.Failure(mrn.isDefined, FailureType.EORIMismatchError): SubmissionHandled)
            false
          }
        case _ => false
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

  val postSubmission: Action[ByteString] = handleSubmission(None)

  val postSubmissionTestOnly: Action[ByteString] = postSubmission

  def putAmendment(mrn: String): Action[ByteString] = handleSubmission(Some(mrn))

  private def handleSubmission(mrn: Option[String]) =
    authorisedAction(eoriCorrectForRequest(mrn)).async(parse.byteString) { implicit request =>
      val receivedDateTime = Instant.now(clock)

      implicit val lc: LoggingContext = LoggingContext()

      val rawPayload = RawPayload(request.body, request.charset)

      service
        .handleSubmission(request.userDetails.eori, rawPayload, mrn, receivedDateTime, request.userDetails.clientInfo)
        .map {
          case Left(failure) =>
            failure.error match {
              case _: ValidationErrors =>
                reportSender.sendReport(
                  SubmissionHandled.Failure(mrn.isDefined, FailureType.ValidationErrors): SubmissionHandled)
                BadRequest(failure.toXml)
              case MRNMismatchError =>
                reportSender.sendReport(
                  SubmissionHandled.Failure(mrn.isDefined, FailureType.MRNMismatchError): SubmissionHandled)
                BadRequest(failure.toXml)
              case _ =>
                reportSender.sendReport(
                  SubmissionHandled.Failure(mrn.isDefined, FailureType.InternalServerError): SubmissionHandled)
                InternalServerError(failure.toXml)
            }
          case Right(success) =>
            submitToNRSIfReqd(rawPayload, request, receivedDateTime)
            reportSender.sendReport(SubmissionHandled.Success(mrn.isDefined): SubmissionHandled)
            Ok(xmlSuccessResponse(success.correlationId))
        }
    }

  private def submitToNRSIfReqd(rawPayload: RawPayload, request: UserRequest[_], receivedDateTime: Instant)(
    implicit hc: HeaderCarrier): Unit =
    request.userDetails.identityData.foreach { identityData =>
      implicit val lc: LoggingContext = LoggingContext(eori = Some(request.userDetails.eori))

      val submission = NRSSubmission(
        rawPayload,
        NRSMetadata(
          receivedDateTime,
          request.userDetails.eori,
          identityData,
          request,
          rawPayload.byteArray.calculateSha256))

      nrsService.submit(submission)
    }
}
