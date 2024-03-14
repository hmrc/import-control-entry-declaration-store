/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.entrydeclarationstore.services

import cats.data.EitherT
import cats.implicits._
import com.codahale.metrics.MetricRegistry
import play.api.Logging
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.entrydeclarationstore.config.AppConfig
import uk.gov.hmrc.entrydeclarationstore.connectors.{EISSendFailure, EisConnector}
import uk.gov.hmrc.entrydeclarationstore.logging.{ContextLogger, LoggingContext}
import uk.gov.hmrc.entrydeclarationstore.models._
import uk.gov.hmrc.entrydeclarationstore.models.json.{DeclarationToJsonConverter, InputParameters}
import uk.gov.hmrc.entrydeclarationstore.reporting.{ClientInfo, ReportSender, SubmissionReceived, SubmissionSentToEIS}
import uk.gov.hmrc.entrydeclarationstore.repositories.EntryDeclarationRepo
import uk.gov.hmrc.entrydeclarationstore.utils._
import uk.gov.hmrc.entrydeclarationstore.validation.ValidationHandler
import uk.gov.hmrc.http.HeaderCarrier

import java.time.{Clock, Instant}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}
import scala.xml.NodeSeq

trait EntryDeclarationStore {
  def handleSubmission(
    eori: String,
    rawPayload: RawPayload,
    mrn: Option[String],
    receivedDateTime: Instant,
    clientInfo: ClientInfo,
    submissionId: String,
    correlationId: String,
    input: InputParameters)(implicit hc: HeaderCarrier): Future[Either[ErrorWrapper[_], SuccessResponse]]
}

@Singleton
class EntryDeclarationStoreImpl @Inject()(
  entryDeclarationRepo: EntryDeclarationRepo,
  validationHandler: ValidationHandler,
  declarationToJsonConverter: DeclarationToJsonConverter,
  eisConnector: EisConnector,
  reportSender: ReportSender,
  clock: Clock,
  override val metrics: MetricRegistry,
  appConfig: AppConfig
)(implicit ec: ExecutionContext)
    extends EntryDeclarationStore
    with Timer
    with Logging
    with MetricsReporter {

  def handleSubmission(
    eori: String,
    rawPayload: RawPayload,
    mrn: Option[String],
    receivedDateTime: Instant,
    clientInfo: ClientInfo,
    submissionId: String,
    correlationId: String,
    input: InputParameters)(implicit hc: HeaderCarrier): Future[Either[ErrorWrapper[_], SuccessResponse]] =
    timeFuture("Service handleSubmission", "handleSubmission.total") {

      implicit val lc: LoggingContext =
        LoggingContext
          .withMessageType(eori = eori, correlationId = correlationId, submissionId = submissionId, mrn = mrn)

      if (appConfig.logSubmissionPayloads) {
        ContextLogger.info(
          s"Handling submission for ${clientInfo.clientType} client. Payload:\n${rawPayload.byteString.utf8String}")
      } else {
        ContextLogger.info(s"Handling submission for ${clientInfo.clientType} client.")
      }

      val result = for {
        xmlPayload             <- EitherT.fromEither[Future](validationHandler.handleValidation(rawPayload, eori, mrn))
        entryDeclarationAsJson <- EitherT.fromEither[Future](convertToJson(xmlPayload, input))
        _ = validateJson(entryDeclarationAsJson)
        entryDeclaration = EntryDeclarationModel(
          correlationId    = correlationId,
          submissionId     = submissionId,
          eori             = eori,
          payload          = entryDeclarationAsJson,
          mrn              = mrn,
          receivedDateTime = receivedDateTime
        )
        _ <- EitherT(saveToDatabase(entryDeclaration))
        transportMode = (xmlPayload \\ "TraModAtBorHEA76").head.text
        _ <- EitherT(
              sendSubmissionReceivedReport(input, eori, entryDeclarationAsJson, rawPayload, transportMode, clientInfo))
      } yield {
        submitToEIS(input, eori, transportMode, receivedDateTime)
        reportMetrics(MessageType(amendment = mrn.isDefined), clientInfo.clientType, transportMode, rawPayload.length)
        SuccessResponse(entryDeclaration.correlationId, entryDeclaration.submissionId)
      }

      result.value
    }

  private def submitToEIS(input: InputParameters, eori: String, transportMode: String, time: Instant)(
    implicit hc: HeaderCarrier,
    lc: LoggingContext): Unit = {

    import input._

    timeFuture("Submission to EIS", "handleSubmission.submitToEis") {
      val messageType = MessageType(amendment = mrn.isDefined)
      val metadata    = EntryDeclarationMetadata(submissionId, messageType, transportMode, time, mrn)

      eisConnector.submitMetadata(metadata, bypassTrafficSwitch = false)
    }.andThen {
      case Success(result) =>
        sendSubmissionSendToEISReport(input, eori, result)
        result match {
          case None    => entryDeclarationRepo.setEisSubmissionSuccess(submissionId, Instant.now(clock))
          case Some(_) => entryDeclarationRepo.setEisSubmissionFailure(submissionId)
        }
      case Failure(NonFatal(_)) =>
        sendSubmissionSendToEISReport(input, eori, Some(EISSendFailure.ExceptionThrown))
        entryDeclarationRepo.setEisSubmissionFailure(submissionId)
    }
  }

  private def saveToDatabase(entryDeclaration: EntryDeclarationModel)(
    implicit lc: LoggingContext): Future[Either[ErrorWrapper[_], Unit]] =
    timeFuture("Save submission to database", "handleSubmission.saveToDatabase") {

      entryDeclarationRepo
        .save(entryDeclaration)
        .map(result =>
          if (result) {
            Right(())
          } else {
            Left(ErrorWrapper(ServerError))
        })
    }

  private def convertToJson(xml: NodeSeq, inputParameters: InputParameters)(
    implicit lc: LoggingContext): Either[ErrorWrapper[_], JsValue] =
    time("Json conversion", "handleSubmission.convertToJson") {
      if(appConfig.optionalFieldsFeature) {
        declarationToJsonConverter.convertToJsonNew(xml, inputParameters)
      } else {
        declarationToJsonConverter.convertToJson(xml, inputParameters)
      }
    }

  private def validateJson(json: JsValue)(implicit lc: LoggingContext): Unit =
    if (appConfig.validateXMLtoJsonTransformation) {
      if(appConfig.optionalFieldsFeature) {
        declarationToJsonConverter.validateJsonNew(json)
      } else {
        declarationToJsonConverter.validateJson(json)
      }
    }

  private def sendSubmissionReceivedReport(
    input: InputParameters,
    eori: String,
    body: JsValue,
    rawPayload: RawPayload,
    transportMode: String,
    clientInfo: ClientInfo
  )(implicit hc: HeaderCarrier, lc: LoggingContext): Future[Either[ErrorWrapper[_], Unit]] = {
    import input._

    reportSender
      .sendReport(
        receivedDateTime,
        SubmissionReceived(
          eori          = eori,
          correlationId = correlationId,
          submissionId  = submissionId,
          MessageType.apply(mrn.isDefined),
          body          = body,
          transportMode = transportMode,
          clientInfo    = clientInfo,
          bodyLength    = rawPayload.length,
          amendmentMrn  = mrn
        )
      )
      .map(_.asRight[ErrorWrapper[_]])
      .recover { case NonFatal(_) => Left(ErrorWrapper(ServerError)) }
  }

  private def sendSubmissionSendToEISReport(
    input: InputParameters,
    eori: String,
    eisSendFailure: Option[EISSendFailure]
  )(implicit hc: HeaderCarrier, lc: LoggingContext): Unit = {
    import input._
    reportSender.sendReport(
      SubmissionSentToEIS(
        eori          = eori,
        correlationId = correlationId,
        submissionId  = submissionId,
        MessageType.apply(mrn.isDefined),
        eisSendFailure
      ))
  }
}
