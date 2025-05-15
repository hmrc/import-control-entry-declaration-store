/*
 * Copyright 2025 HM Revenue & Customs
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

import com.codahale.metrics.MetricRegistry
import org.apache.pekko.util.ByteString
import play.api.Logging
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.entrydeclarationstore.logging.LoggingContext
import uk.gov.hmrc.entrydeclarationstore.models.RawPayload
import uk.gov.hmrc.entrydeclarationstore.models.json.{DeclarationToJsonConverter, InputParameters}
import uk.gov.hmrc.entrydeclarationstore.nrs.{NRSMetadata, NRSService, NRSSubmission}
import uk.gov.hmrc.entrydeclarationstore.reporting.{FailureType, ReportSender, SubmissionHandled}
import uk.gov.hmrc.entrydeclarationstore.services.{AuthService, EntryDeclarationStore}
import uk.gov.hmrc.entrydeclarationstore.utils.ChecksumUtils._
import uk.gov.hmrc.entrydeclarationstore.utils.SubmissionUtils.extractSubmissionHandledDetails
import uk.gov.hmrc.entrydeclarationstore.utils.{IdGenerator, Timer}
import uk.gov.hmrc.entrydeclarationstore.validation.{EORIMismatchError, MRNMismatchError, ValidationErrors, ValidationHandler}
import uk.gov.hmrc.http.HeaderCarrier

import java.time.{Clock, Instant}
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import scala.xml.Elem

@Singleton
class EntryDeclarationSubmissionController @Inject()(
  cc: ControllerComponents,
  service: EntryDeclarationStore,
  idGenerator: IdGenerator,
  validationHandler: ValidationHandler,
  declarationToJsonConverter: DeclarationToJsonConverter,
  val authService: AuthService,
  nrsService: NRSService,
  reportSender: ReportSender,
  clock: Clock,
  override val metrics: MetricRegistry
)(implicit ec: ExecutionContext)
    extends AuthorisedController(cc)
    with Timer
    with Logging {

  private def xmlSuccessResponse(correlationId: String): Elem =
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

  def putAmendment(mrn: String): Action[ByteString] = handleSubmission(Some(mrn))

  private def handleSubmission(mrn: Option[String]) =
    authorisedAction().async(parse.byteString) { implicit request =>
      val receivedDateTime = Instant.now(clock)

      implicit val lc: LoggingContext = LoggingContext()

      val rawPayload = RawPayload(request.body, request.charset)
      val correlationId          = idGenerator.generateCorrelationId
      val submissionId           = idGenerator.generateSubmissionId
      val input: InputParameters = InputParameters(mrn, submissionId, correlationId, receivedDateTime)

      val xml = validationHandler.handleValidation(rawPayload, request.userDetails.eori, mrn)

      // when enabling optionalFields in prod, update this to use new model
      val model = for {
        xml <- xml
        model <- declarationToJsonConverter.convertToModel(xml, input)
        } yield model

      service
        .handleSubmission(request.userDetails.eori, rawPayload, mrn, receivedDateTime, request.userDetails.clientInfo, submissionId, correlationId, input)
        .map {
          case Left(failure) =>
            failure.error match {
              case _: ValidationErrors =>
                reportSender.sendReport(
                  SubmissionHandled.Failure(
                    mrn.isDefined,
                    FailureType.ValidationErrors,
                    extractSubmissionHandledDetails(request.userDetails.eori, request.userDetails.identityData, model)
                  ): SubmissionHandled)
                BadRequest(failure.toXml)
              case MRNMismatchError =>
                reportSender.sendReport(
                  SubmissionHandled.Failure(mrn.isDefined,
                    FailureType.MRNMismatchError,
                    extractSubmissionHandledDetails(request.userDetails.eori, request.userDetails.identityData, model)
                  ): SubmissionHandled)
                BadRequest(failure.toXml)
              case EORIMismatchError =>
                reportSender.sendReport(
                  SubmissionHandled.Failure(mrn.isDefined,
                    FailureType.EORIMismatchError,
                    extractSubmissionHandledDetails(request.userDetails.eori, request.userDetails.identityData, model)
                  ): SubmissionHandled)
                Forbidden(failure.toXml)
              case _ =>
                reportSender.sendReport(
                  SubmissionHandled.Failure(mrn.isDefined,
                    FailureType.InternalServerError,
                    extractSubmissionHandledDetails(request.userDetails.eori, request.userDetails.identityData, model)
                  ): SubmissionHandled)
                InternalServerError(failure.toXml)
            }
          case Right(success) =>
            submitToNRSIfReqd(rawPayload, request, receivedDateTime, success.submissionId)
            reportSender.sendReport(
              SubmissionHandled.Success(
                mrn.isDefined,
                extractSubmissionHandledDetails(request.userDetails.eori, request.userDetails.identityData, model)
              ): SubmissionHandled)
            Ok(xmlSuccessResponse(success.correlationId))
        }
    }

  private def submitToNRSIfReqd(rawPayload: RawPayload, request: UserRequest[_], receivedDateTime: Instant, submissionId: String)(
    implicit hc: HeaderCarrier): Unit =
    request.userDetails.identityData.foreach { identityData =>
      implicit val lc: LoggingContext = LoggingContext(eori = Some(request.userDetails.eori))

      val submission = NRSSubmission(
        rawPayload,
        NRSMetadata(
          receivedDateTime,
          submissionId,
          identityData,
          request,
          rawPayload.byteArray.calculateSha256))

      nrsService.submit(submission)
    }
}
