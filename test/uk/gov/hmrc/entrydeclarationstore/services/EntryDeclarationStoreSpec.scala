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

import com.codahale.metrics.MetricRegistry
import org.scalatest.Inside
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers.{a, convertToAnyShouldWrapper}
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsString, JsValue}
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.entrydeclarationstore.config.MockAppConfig
import uk.gov.hmrc.entrydeclarationstore.connectors.{EISSendFailure, MockEisConnector}
import uk.gov.hmrc.entrydeclarationstore.models._
import uk.gov.hmrc.entrydeclarationstore.models.json.{InputParameters, MockDeclarationToJsonConverter}
import uk.gov.hmrc.entrydeclarationstore.reporting._
import uk.gov.hmrc.entrydeclarationstore.repositories.MockEntryDeclarationRepo
import uk.gov.hmrc.entrydeclarationstore.utils.{MockIdGenerator, XmlFormatConfig}
import uk.gov.hmrc.entrydeclarationstore.validation._
import uk.gov.hmrc.http.HeaderCarrier

import java.io.IOException
import java.time.{Clock, Instant, ZoneOffset}
import scala.concurrent.{Future, Promise}
import scala.util.control.NoStackTrace
import scala.xml.NodeSeq

class EntryDeclarationStoreSpec
    extends AnyWordSpec
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

  val clientId      = "someClientId"
  val applicationId = "someAppId"
  implicit val hc: HeaderCarrier = HeaderCarrier(
    extraHeaders = Seq("x-client-id" -> clientId, "x-application-id" -> applicationId))
  val metrics: MetricRegistry = new MetricRegistry()

  implicit val xmlFormatConfig: XmlFormatConfig = XmlFormatConfig(responseMaxErrors = 100)

  val receivedDateTime: Instant = Instant.now
  val clock: Clock              = Clock.fixed(receivedDateTime, ZoneOffset.UTC)

  val entryDeclarationStore = new EntryDeclarationStoreImpl(
    mockEntryDeclarationRepo,
    mockValidationHandler,
    mockDeclarationToJsonConverter,
    mockEisConnector,
    mockReportSender,
    clock,
    metrics,
    mockAppConfig
  )

  val eori                        = "ABCDEF1234"
  val messageSender               = s"$eori/1234567890"
  val movementRefNo: Some[String] = Some("MRN")
  val transportMode               = "42"
  val jsonPayload: JsValue        = JsString("payload")
  val clientInfo: ClientInfo      = ClientInfo(ClientType.CSP, None, None)
  val correlationId = "correlationId"

  val submissionId  = "submissionId"
  def inputParams(mrn: Option[String]) = InputParameters(mrn, submissionId, correlationId, clock.instant())

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

  private def submissionReceivedReport(payload: NodeSeq, messageType: MessageType, mrn: Option[String]) =
    SubmissionReceived(
      eori          = eori,
      correlationId = correlationId,
      submissionId  = submissionId,
      messageType,
      body          = jsonPayload,
      bodyLength    = payload.toString().length,
      transportMode = transportMode,
      clientInfo    = clientInfo,
      amendmentMrn = mrn
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
    val payload: RawPayload = RawPayload(xmlPayload)

    MockAppConfig.logSubmissionPayloads returns false
  }

  def successfulSubmission(xml: NodeSeq, msgType: MessageType, movementRef: Option[String]): Unit =
    "return Right(SuccessResponse)" in new Test(xml, msgType, movementRef) {
      MockAppConfig.optionalFieldsEnabled returns false
      MockAppConfig.validateXMLtoJsonTransformation.returns(false)
      MockValidationHandler.handleValidation(payload, eori, movementRef) returns Right(xmlPayload)
      MockDeclarationToJsonConverter.convertToJson(xmlPayload).returns(Right(jsonPayload))

      MockEntryDeclarationRepo
        .saveEntryDeclaration(declarationWith(movementRef))
        .returns(Future.successful(true))

      MockReportSender
        .sendReport(receivedDateTime, submissionReceivedReport(xmlPayload, messageType, movementRef))
        .returns(Future.successful(()))
      MockReportSender.sendReport(submissionSentToEISReport(messageType, None))

      MockEisConnector
        .submitMetadata(metadataWith(messageType, movementRef), bypassTrafficSwitch = false)
        .returns(Future.successful(None))

      // Called async so wait on promise to be sure it's called...
      val setEisSubmissionStateUpdated: Promise[Unit] = Promise[Unit]()
      MockEntryDeclarationRepo.setEisSubmissionSuccess(submissionId, Instant.now(clock)).onCall { _ =>
        setEisSubmissionStateUpdated.success(())
        Future.successful(true)
      }

      entryDeclarationStore
        .handleSubmission(eori, payload, movementRef, receivedDateTime, clientInfo, submissionId, correlationId, inputParams(movementRef))
        .futureValue shouldBe Right(SuccessResponse(correlationId, submissionId))

      await(setEisSubmissionStateUpdated.future)
    }

  "EntryDeclarationStore" when {
    "a valid E315 is submitted" must {
      behave like successfulSubmission(IE315payload, MessageType.IE315, None)
    }

    "a valid E313 is submitted" must {
      behave like successfulSubmission(IE313payload, MessageType.IE313, movementRefNo)
    }

    "Payload has validation errors" must {
      "return Left(FailureResponse)" in new Test {
        val errorWrapper: ErrorWrapper[ValidationErrors] =
          ErrorWrapper(ValidationErrors(Seq(ValidationError("errText", "errType", "123", "errLocation"))))

        MockValidationHandler.handleValidation(payload, eori, mrn) returns Left(errorWrapper)

        entryDeclarationStore
          .handleSubmission(eori, payload, mrn, receivedDateTime, clientInfo, submissionId, correlationId, inputParams(mrn))
          .futureValue shouldBe Left(errorWrapper)
      }
    }

    "Valid EntryDeclaration fails to save in the database" must {
      "return Left(FailureResponse)" in new Test {
        MockAppConfig.validateXMLtoJsonTransformation.returns(false)
        MockAppConfig.optionalFieldsEnabled returns false
        MockValidationHandler.handleValidation(payload, eori, mrn) returns Right(xmlPayload)
        MockDeclarationToJsonConverter.convertToJson(xmlPayload).returns(Right(jsonPayload))

        MockEntryDeclarationRepo
          .saveEntryDeclaration(declarationWith(mrn))
          .returns(Future.successful(false))

        entryDeclarationStore.handleSubmission(eori, payload, mrn, receivedDateTime, clientInfo, submissionId, correlationId, inputParams(mrn)).futureValue shouldBe
          Left(ErrorWrapper(ServerError))
      }
    }

    "SubmissionReceived event fails to send" must {
      "return Left(FailureResponse)" in new Test {
        MockAppConfig.validateXMLtoJsonTransformation.returns(false)
        MockAppConfig.optionalFieldsEnabled returns false
        MockValidationHandler.handleValidation(payload, eori, mrn) returns Right(xmlPayload)
        MockDeclarationToJsonConverter.convertToJson(xmlPayload).returns(Right(jsonPayload))

        MockEntryDeclarationRepo
          .saveEntryDeclaration(declarationWith(mrn))
          .returns(Future.successful(true))

        MockReportSender
          .sendReport(receivedDateTime, submissionReceivedReport(xmlPayload, messageType, mrn))
          .returns(Future.failed(new IOException))

        entryDeclarationStore.handleSubmission(eori, payload, mrn, receivedDateTime, clientInfo, submissionId, correlationId, inputParams(mrn)).futureValue shouldBe
          Left(ErrorWrapper(ServerError))
      }
    }

    "EIS submission fails" must {
      "still send report and set failure status in database" in new Test {
        MockAppConfig.validateXMLtoJsonTransformation.returns(false)
        MockAppConfig.optionalFieldsEnabled returns false
        MockValidationHandler.handleValidation(payload, eori, mrn) returns Right(xmlPayload)
        MockDeclarationToJsonConverter.convertToJson(xmlPayload).returns(Right(jsonPayload))

        MockEntryDeclarationRepo
          .saveEntryDeclaration(declarationWith(mrn))
          .returns(Future.successful(true))

        // WLOG...
        val eisSendFailure: EISSendFailure.ExceptionThrown.type = EISSendFailure.ExceptionThrown

        MockReportSender
          .sendReport(receivedDateTime, submissionReceivedReport(xmlPayload, messageType, mrn))
          .returns(Future.successful(()))
        MockReportSender.sendReport(submissionSentToEISReport(messageType, Some(eisSendFailure)))

        MockEisConnector
          .submitMetadata(metadataWith(messageType, mrn), bypassTrafficSwitch = false)
          .returns(Future.successful(Some(eisSendFailure)))

        // Called async so wait on promise to be sure it's called...
        val setEisSubmissionStateUpdated: Promise[Unit] = Promise[Unit]()
        MockEntryDeclarationRepo.setEisSubmissionFailure(submissionId).onCall { _ =>
          setEisSubmissionStateUpdated.success(())
          Future.successful(true)
        }

        inside(entryDeclarationStore.handleSubmission(eori, payload, mrn, receivedDateTime, clientInfo, submissionId, correlationId, inputParams(mrn)).futureValue) {
          case Right(response) => response shouldBe a[SuccessResponse]
        }

        await(setEisSubmissionStateUpdated.future)
      }
    }

    "declaration is processed successfully" must {
      "not wait for EIS submission to complete" in new Test {
        MockAppConfig.validateXMLtoJsonTransformation.returns(false)
        MockAppConfig.optionalFieldsEnabled returns false
        MockValidationHandler.handleValidation(payload, eori, mrn) returns Right(xmlPayload)
        MockDeclarationToJsonConverter.convertToJson(xmlPayload).returns(Right(jsonPayload))

        MockEntryDeclarationRepo
          .saveEntryDeclaration(declarationWith(mrn))
          .returns(Future.successful(true))

        MockReportSender
          .sendReport(receivedDateTime, submissionReceivedReport(xmlPayload, messageType, mrn))
          .returns(Future.successful(()))

        MockEisConnector
          .submitMetadata(metadataWith(messageType, mrn), bypassTrafficSwitch = false)
          .returns(Promise[Option[EISSendFailure]]().future)

        entryDeclarationStore
          .handleSubmission(eori, payload, mrn, receivedDateTime, clientInfo, submissionId, correlationId, inputParams(mrn))
          .futureValue shouldBe Right(SuccessResponse(correlationId, submissionId))
      }
    }

    "EIS submission results in a failed future" must {
      "still send report and set failure status in database" in new Test {
        MockAppConfig.validateXMLtoJsonTransformation.returns(false)
        MockAppConfig.optionalFieldsEnabled returns false
        MockValidationHandler.handleValidation(payload, eori, mrn) returns Right(xmlPayload)
        MockDeclarationToJsonConverter.convertToJson(xmlPayload).returns(Right(jsonPayload))

        MockEntryDeclarationRepo
          .saveEntryDeclaration(declarationWith(mrn))
          .returns(Future.successful(true))

        MockReportSender
          .sendReport(receivedDateTime, submissionReceivedReport(xmlPayload, messageType, mrn))
          .returns(Future.successful(()))
        MockReportSender.sendReport(submissionSentToEISReport(messageType, Some(EISSendFailure.ExceptionThrown)))

        MockEisConnector
          .submitMetadata(metadataWith(messageType, mrn), bypassTrafficSwitch = false)
          .returns(Future.failed(new RuntimeException with NoStackTrace))

        // Called async so wait on promise to be sure it's called...
        val setEisSubmissionStateUpdated: Promise[Unit] = Promise[Unit]()
        MockEntryDeclarationRepo.setEisSubmissionFailure(submissionId).onCall { _ =>
          setEisSubmissionStateUpdated.success(())
          Future.successful(true)
        }

        entryDeclarationStore
          .handleSubmission(eori, payload, mrn, receivedDateTime, clientInfo, submissionId, correlationId, inputParams(mrn))
          .futureValue shouldBe Right(SuccessResponse(correlationId, submissionId))

        await(setEisSubmissionStateUpdated.future)
      }
    }
  }
}
