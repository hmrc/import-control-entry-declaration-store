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

package uk.gov.hmrc.entrydeclarationstore.services

import java.time.{Clock, Instant}

import cats.data.EitherT
import cats.implicits._
import com.kenshoo.play.metrics.Metrics
import javax.inject.{Inject, Singleton}
import play.api.libs.json.JsValue
import uk.gov.hmrc.entrydeclarationstore.config.AppConfig
import uk.gov.hmrc.entrydeclarationstore.connectors.{EISSendFailure, EisConnector}
import uk.gov.hmrc.entrydeclarationstore.models._
import uk.gov.hmrc.entrydeclarationstore.models.json.{DeclarationToJsonConverter, InputParameters}
import uk.gov.hmrc.entrydeclarationstore.reporting.{ClientType, ReportSender, SubmissionReceived, SubmissionSentToEIS}
import uk.gov.hmrc.entrydeclarationstore.repositories.EntryDeclarationRepo
import uk.gov.hmrc.entrydeclarationstore.utils._
import uk.gov.hmrc.entrydeclarationstore.validation.ValidationHandler
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import scala.xml.NodeSeq

trait EntryDeclarationStore {
  def handleSubmission(payload: String, mrn: Option[String], clientType: ClientType)(
    implicit hc: HeaderCarrier): Future[Either[ErrorWrapper[_], SuccessResponse]]
}

@Singleton
class EntryDeclarationStoreImpl @Inject()(
  entryDeclarationRepo: EntryDeclarationRepo,
  validationHandler: ValidationHandler,
  idGenerator: IdGenerator,
  declarationToJsonConverter: DeclarationToJsonConverter,
  eisConnector: EisConnector,
  reportSender: ReportSender,
  clock: Clock,
  override val metrics: Metrics,
  appConfig: AppConfig
)(implicit ec: ExecutionContext)
    extends EntryDeclarationStore
    with Timer
    with EventLogger {

  def handleSubmission(payload: String, mrn: Option[String], clientType: ClientType)(
    implicit hc: HeaderCarrier): Future[Either[ErrorWrapper[_], SuccessResponse]] =
    timeFuture("Service handleSubmission", "handleSubmission.total") {

      val correlationId          = idGenerator.generateCorrelationId
      val submissionId           = idGenerator.generateSubmissionId
      val receivedDateTime       = Instant.now(clock)
      val input: InputParameters = InputParameters(mrn.isDefined, submissionId, correlationId, receivedDateTime)

      val result = for {
        xmlPayload <- EitherT.fromEither[Future](validationHandler.handleValidation(payload, mrn))
        eori: String = EoriUtils.eoriFromXml(xmlPayload)
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
      } yield {
        val transportMode = (xmlPayload \\ "TraModAtBorHEA76").head.text

        sendSubmissionReceivedReport(
          receivedDateTime,
          eori,
          correlationId,
          submissionId,
          mrn,
          entryDeclarationAsJson,
          payload,
          transportMode,
          clientType)
        submitToEIS(eori, correlationId, submissionId, mrn, transportMode, receivedDateTime)
        SuccessResponse(entryDeclaration.correlationId)
      }

      result.value
    }

  private def submitToEIS(
    eori: String,
    correlationId: String,
    submissionId: String,
    mrn: Option[String],
    transportMode: String,
    time: Instant)(implicit hc: HeaderCarrier): Unit =
    timeFuture("Submission to EIS", "handleSubmission.submitToEis") {

      val messageType = MessageType(amendment = mrn.isDefined)
      val metadata    = EntryDeclarationMetadata(submissionId, messageType, transportMode, time, mrn)

      eisConnector.submitMetadata(metadata)
    }.andThen {
        case Success(result) => sendSubmissionSendToEISReport(eori, correlationId, submissionId, mrn, result)
        case Failure(_) =>
          sendSubmissionSendToEISReport(eori, correlationId, submissionId, mrn, Some(EISSendFailure.ExceptionThrown))
      }
      .onSuccess {
        case None => entryDeclarationRepo.setSubmissionTime(submissionId, Instant.now(clock))
      }

  private def saveToDatabase(entryDeclaration: EntryDeclarationModel): Future[Either[ErrorWrapper[_], Unit]] =
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

  private def convertToJson(xml: NodeSeq, inputParameters: InputParameters): Either[ErrorWrapper[_], JsValue] =
    time("Json conversion", "handleSubmission.convertToJson") {
      declarationToJsonConverter.convertToJson(xml, inputParameters)
    }

  private def validateJson(json: JsValue): Unit =
    if (appConfig.validateXMLtoJsonTransformation) declarationToJsonConverter.validateJson(json)

  private def sendSubmissionReceivedReport(
    received: Instant,
    eori: String,
    correlationId: String,
    submissionId: String,
    mrn: Option[String],
    body: JsValue,
    xmlPayload: String,
    transportMode: String,
    clientType: ClientType
  )(implicit hc: HeaderCarrier): Unit =
    reportSender.sendReport(
      received,
      SubmissionReceived(
        eori          = eori,
        correlationId = correlationId,
        submissionId  = submissionId,
        MessageType.apply(mrn.isDefined),
        body          = body,
        transportMode = transportMode,
        clientType    = clientType,
        bodyLength    = xmlPayload.length
      )
    )

  private def sendSubmissionSendToEISReport(
    eori: String,
    correlationId: String,
    submissionId: String,
    mrn: Option[String],
    eisSendFailure: Option[EISSendFailure]
  )(implicit hc: HeaderCarrier): Unit =
    reportSender.sendReport(
      SubmissionSentToEIS(
        eori          = eori,
        correlationId = correlationId,
        submissionId  = submissionId,
        MessageType.apply(mrn.isDefined),
        eisSendFailure
      ))
}
