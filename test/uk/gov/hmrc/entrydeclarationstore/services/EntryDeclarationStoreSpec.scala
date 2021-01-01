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

package uk.gov.hmrc.entrydeclarationstore.services

import com.kenshoo.play.metrics.Metrics
import org.scalatest.Inside
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import play.api.libs.json.{JsString, JsValue}
import uk.gov.hmrc.entrydeclarationstore.config.MockAppConfig
import uk.gov.hmrc.entrydeclarationstore.connectors.{EISSendFailure, MockEisConnector}
import uk.gov.hmrc.entrydeclarationstore.models._
import uk.gov.hmrc.entrydeclarationstore.models.json.MockDeclarationToJsonConverter
import uk.gov.hmrc.entrydeclarationstore.reporting.{ClientType, MockReportSender, SubmissionReceived, SubmissionSentToEIS}
import uk.gov.hmrc.entrydeclarationstore.repositories.MockEntryDeclarationRepo
import uk.gov.hmrc.entrydeclarationstore.utils.{MockIdGenerator, MockMetrics, XmlFormatConfig}
import uk.gov.hmrc.entrydeclarationstore.validation._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import java.io.IOException
import java.time.{Clock, Instant, ZoneOffset}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import scala.util.control.NoStackTrace
import scala.xml.NodeSeq

class EntryDeclarationStoreSpec
    extends UnitSpec
    with MockEntryDeclarationRepo
    with Inside
    with ScalaFutures
    with MockValidationHandler
    with MockIdGenerator
    with MockDeclarationToJsonConverter
    with MockAppConfig
    with MockEisConnector
    with IntegrationPatience
    with MockReportSender {

  implicit val hc: HeaderCarrier = HeaderCarrier()
  val mockedMetrics: Metrics     = new MockMetrics

  implicit val xmlFormatConfig: XmlFormatConfig = XmlFormatConfig(responseMaxErrors = 100)

  val receivedDateTime: Instant = Instant.now
  val now: Instant              = receivedDateTime.plusSeconds(1)
  val clock: Clock              = Clock.fixed(now, ZoneOffset.UTC)

  val entryDeclarationStore = new EntryDeclarationStoreImpl(
    mockEntryDeclarationRepo,
    mockValidationHandler,
    mockIdGenerator,
    mockDeclarationToJsonConverter,
    mockEisConnector,
    mockReportSender,
    clock,
    mockedMetrics,
    mockAppConfig
  )

  val eori                        = "ABCDEF1234"
  val messageSender               = s"$eori/1234567890"
  val movementRefNo: Some[String] = Some("MRN")
  val transportMode               = "42"
  val jsonPayload: JsValue        = JsString("payload")
  val clientType: ClientType      = ClientType.CSP

  val correlationId = "correlationId"
  val submissionId  = "submissionId"

  val IE315payload: NodeSeq =
    <ie:CC315A>
      <MesSenMES3>{messageSender}</MesSenMES3>
      <TraModAtBorHEA76>{transportMode}</TraModAtBorHEA76>
    </ie:CC315A>

  val IE313payload: NodeSeq =
    <ie:CC313A>
      <HEAHEA>
        <TraModAtBorHEA76>{transportMode}</TraModAtBorHEA76>
      </HEAHEA>
      <MesSenMES3>{messageSender}</MesSenMES3>
    </ie:CC313A>

  private def declarationWith(mrn: Option[String]) =
    EntryDeclarationModel(
      correlationId,
      submissionId,
      eori,
      jsonPayload,
      mrn,
      receivedDateTime,
      None,
      EisSubmissionState.NotSent)

  private def metadataWith(messageType: MessageType, mrn: Option[String]) =
    EntryDeclarationMetadata(submissionId, messageType, transportMode, receivedDateTime, mrn)

  private def submissionReceivedReport(payload: NodeSeq, messageType: MessageType) =
    SubmissionReceived(
      eori          = eori,
      correlationId = correlationId,
      submissionId  = submissionId,
      messageType,
      body          = jsonPayload,
      bodyLength    = payload.toString().length,
      transportMode = transportMode,
      clientType    = clientType
    )

  private def submissionSentToEISReport(messageType: MessageType, failure: Option[EISSendFailure]) =
    SubmissionSentToEIS(
      eori          = eori,
      correlationId = correlationId,
      submissionId  = submissionId,
      messageType,
      failure
    )

  class Test(
    val xmlPayload: NodeSeq      = IE313payload,
    val messageType: MessageType = MessageType.IE313,
    val mrn: Option[String]      = movementRefNo) {
    val payload: String = xmlPayload.toString
    MockIdGenerator.generateCorrelationId returns correlationId
    MockIdGenerator.generateSubmissionId returns submissionId

    MockAppConfig.logSubmissionPayloads returns false
  }

  def successfulSubmission(xmlPayload: NodeSeq, messageType: MessageType, mrn: Option[String]): Unit =
    "return Right(SuccessResponse)" in new Test(xmlPayload, messageType, mrn) {
      MockAppConfig.validateXMLtoJsonTransformation.returns(false)
      MockValidationHandler.handleValidation(payload, mrn) returns Right(xmlPayload)
      MockDeclarationToJsonConverter.convertToJson(xmlPayload).returns(Right(jsonPayload))

      MockEntryDeclarationRepo
        .saveEntryDeclaration(declarationWith(mrn))
        .returns(Future.successful(true))

      MockReportSender
        .sendReport(receivedDateTime, submissionReceivedReport(xmlPayload, messageType))
        .returns(Future.successful(()))
      MockReportSender.sendReport(submissionSentToEISReport(messageType, None))

      MockEisConnector
        .submitMetadata(metadataWith(messageType, mrn), bypassTrafficSwitch = false)
        .returns(Future.successful(None))

      // Called async so wait on promise to be sure it's called...
      val setEisSubmissionStateUpdated: Promise[Unit] = Promise[Unit]
      MockEntryDeclarationRepo.setEisSubmissionSuccess(submissionId, now).onCall { _ =>
        setEisSubmissionStateUpdated.success(())
        Future.successful(true)
      }

      entryDeclarationStore
        .handleSubmission(eori, payload, mrn, receivedDateTime, clientType)
        .futureValue shouldBe Right(SuccessResponse(correlationId))

      await(setEisSubmissionStateUpdated.future)
    }

  "EntryDeclarationStore" when {
    "a valid E315 is submitted" should {
      behave like successfulSubmission(IE315payload, MessageType.IE315, None)
    }

    "a valid E313 is submitted" should {
      behave like successfulSubmission(IE313payload, MessageType.IE313, movementRefNo)
    }

    "Payload has validation errors" should {
      "return Left(FailureResponse)" in new Test {
        val errorWrapper: ErrorWrapper[ValidationErrors] =
          ErrorWrapper(ValidationErrors(Seq(ValidationError("errText", "errType", "123", "errLocation"))))

        MockValidationHandler.handleValidation(payload, mrn) returns Left(errorWrapper)

        entryDeclarationStore
          .handleSubmission(eori, payload, mrn, receivedDateTime, clientType)
          .futureValue shouldBe Left(errorWrapper)
      }
    }

    "Valid EntryDeclaration fails to save in the database" should {
      "return Left(FailureResponse)" in new Test {
        MockAppConfig.validateXMLtoJsonTransformation.returns(false)
        MockValidationHandler.handleValidation(payload, mrn) returns Right(xmlPayload)
        MockDeclarationToJsonConverter.convertToJson(xmlPayload).returns(Right(jsonPayload))

        MockEntryDeclarationRepo
          .saveEntryDeclaration(declarationWith(mrn))
          .returns(Future.successful(false))

        entryDeclarationStore.handleSubmission(eori, payload, mrn, receivedDateTime, clientType).futureValue shouldBe
          Left(ErrorWrapper(ServerError))
      }
    }

    "SubmissionReceived event fails to send" should {
      "return Left(FailureResponse)" in new Test {
        MockAppConfig.validateXMLtoJsonTransformation.returns(false)
        MockValidationHandler.handleValidation(payload, mrn) returns Right(xmlPayload)
        MockDeclarationToJsonConverter.convertToJson(xmlPayload).returns(Right(jsonPayload))

        MockEntryDeclarationRepo
          .saveEntryDeclaration(declarationWith(mrn))
          .returns(Future.successful(true))

        MockReportSender
          .sendReport(receivedDateTime, submissionReceivedReport(xmlPayload, messageType))
          .returns(Future.failed(new IOException))

        entryDeclarationStore.handleSubmission(eori, payload, mrn, receivedDateTime, clientType).futureValue shouldBe
          Left(ErrorWrapper(ServerError))
      }
    }

    "EIS submission fails" should {
      "still send report and set failure status in database" in new Test {
        MockAppConfig.validateXMLtoJsonTransformation.returns(false)
        MockValidationHandler.handleValidation(payload, mrn) returns Right(xmlPayload)
        MockDeclarationToJsonConverter.convertToJson(xmlPayload).returns(Right(jsonPayload))

        MockEntryDeclarationRepo
          .saveEntryDeclaration(declarationWith(mrn))
          .returns(Future.successful(true))

        // WLOG...
        val eisSendFailure: EISSendFailure.ExceptionThrown.type = EISSendFailure.ExceptionThrown

        MockReportSender
          .sendReport(receivedDateTime, submissionReceivedReport(xmlPayload, messageType))
          .returns(Future.successful(()))
        MockReportSender.sendReport(submissionSentToEISReport(messageType, Some(eisSendFailure)))

        MockEisConnector
          .submitMetadata(metadataWith(messageType, mrn), bypassTrafficSwitch = false)
          .returns(Future.successful(Some(eisSendFailure)))

        // Called async so wait on promise to be sure it's called...
        val setEisSubmissionStateUpdated: Promise[Unit] = Promise[Unit]
        MockEntryDeclarationRepo.setEisSubmissionFailure(submissionId).onCall { _ =>
          setEisSubmissionStateUpdated.success(())
          Future.successful(true)
        }

        inside(entryDeclarationStore.handleSubmission(eori, payload, mrn, receivedDateTime, clientType).futureValue) {
          case Right(response) => response shouldBe a[SuccessResponse]
        }

        await(setEisSubmissionStateUpdated.future)
      }
    }

    "declaration is processed successfully" must {
      "not wait for EIS submission to complete" in new Test {
        MockAppConfig.validateXMLtoJsonTransformation.returns(false)
        MockValidationHandler.handleValidation(payload, mrn) returns Right(xmlPayload)
        MockDeclarationToJsonConverter.convertToJson(xmlPayload).returns(Right(jsonPayload))

        MockEntryDeclarationRepo
          .saveEntryDeclaration(declarationWith(mrn))
          .returns(Future.successful(true))

        MockReportSender
          .sendReport(receivedDateTime, submissionReceivedReport(xmlPayload, messageType))
          .returns(Future.successful(()))

        MockEisConnector
          .submitMetadata(metadataWith(messageType, mrn), bypassTrafficSwitch = false)
          .returns(Promise[Option[EISSendFailure]].future)

        entryDeclarationStore
          .handleSubmission(eori, payload, mrn, receivedDateTime, clientType)
          .futureValue shouldBe Right(SuccessResponse(correlationId))
      }
    }

    "EIS submission results in a failed future" should {
      "still send report and set failure status in database" in new Test {
        MockAppConfig.validateXMLtoJsonTransformation.returns(false)
        MockValidationHandler.handleValidation(payload, mrn) returns Right(xmlPayload)
        MockDeclarationToJsonConverter.convertToJson(xmlPayload).returns(Right(jsonPayload))

        MockEntryDeclarationRepo
          .saveEntryDeclaration(declarationWith(mrn))
          .returns(Future.successful(true))

        MockReportSender
          .sendReport(receivedDateTime, submissionReceivedReport(xmlPayload, messageType))
          .returns(Future.successful(()))
        MockReportSender.sendReport(submissionSentToEISReport(messageType, Some(EISSendFailure.ExceptionThrown)))

        MockEisConnector
          .submitMetadata(metadataWith(messageType, mrn), bypassTrafficSwitch = false)
          .returns(Future.failed(new RuntimeException with NoStackTrace))

        // Called async so wait on promise to be sure it's called...
        val setEisSubmissionStateUpdated: Promise[Unit] = Promise[Unit]
        MockEntryDeclarationRepo.setEisSubmissionFailure(submissionId).onCall { _ =>
          setEisSubmissionStateUpdated.success(())
          Future.successful(true)
        }

        entryDeclarationStore
          .handleSubmission(eori, payload, mrn, receivedDateTime, clientType)
          .futureValue shouldBe Right(SuccessResponse(correlationId))

        await(setEisSubmissionStateUpdated.future)
      }
    }
  }
}
