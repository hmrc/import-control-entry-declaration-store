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

package uk.gov.hmrc.entrydeclarationstore.controllers.testonly

import com.codahale.metrics.MetricRegistry
import org.apache.pekko.util.ByteString
import org.scalatest.matchers.should.Matchers.{contain, convertToAnyShouldWrapper}
import org.scalatest.wordspec.AnyWordSpec
import play.api.http.MimeTypes
import play.api.libs.json.{JsString, JsValue}
import play.api.mvc.{Request, Result}
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.entrydeclarationstore.config.MockAppConfig
import uk.gov.hmrc.entrydeclarationstore.models._
import uk.gov.hmrc.entrydeclarationstore.models.json._
import uk.gov.hmrc.entrydeclarationstore.nrs._
import uk.gov.hmrc.entrydeclarationstore.reporting._
import uk.gov.hmrc.entrydeclarationstore.services._
import uk.gov.hmrc.entrydeclarationstore.utils.ChecksumUtils._
import uk.gov.hmrc.entrydeclarationstore.utils.SubmissionUtils.extractSubmissionHandledDetails
import uk.gov.hmrc.entrydeclarationstore.utils.{MockIdGenerator, XmlFormatConfig, XmlFormats}
import uk.gov.hmrc.entrydeclarationstore.validation._
import uk.gov.hmrc.http.HeaderCarrier

import java.time.{Clock, Instant, ZoneOffset}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import scala.xml.{XML => _, _}

class TestEntryDeclarationSubmissionControllerSpec
    extends AnyWordSpec
    with MockEntryDeclarationStore
    with MockAuthService
    with MockNRSService
    with NRSMetadataTestData
    with MockValidationHandler
    with MockIdGenerator
    with MockDeclarationToJsonConverter
    with MockReportSender
    with MockAppConfig {

  private val responseMaxErrors = 100
  val eori                   = "GB1234567890"
  val submissionId           = "3216783621-123873821-12332"
  val mrn                    = "mrn"
  val clientInfo: ClientInfo = ClientInfo(ClientType.CSP, None, None)
  implicit val hc: HeaderCarrier = HeaderCarrier()

  implicit val xmlFormatConfig: XmlFormatConfig = XmlFormatConfig(responseMaxErrors)

  val xmlPayload: NodeSeq =
    // @formatter:off
    <AnyXml>><MesSenMES3>{eori}</MesSenMES3></AnyXml>
  // @formatter:on

  val xmlPayloadNoEori: NodeSeq =
    // @formatter:off
    <AnyXml><MesSenMES3/></AnyXml>
  // @formatter:on

  val xmlPayloadBlankEori: NodeSeq =
    // @formatter:off
    <AnyXml></AnyXml>
  // @formatter:on

  private val rawPayload = RawPayload(xmlPayload)
  val jsonPayload: JsValue        = JsString("payload")
  val correlationId = "correlationId"
  val entrySummaryDeclaration: EntrySummaryDeclaration = EntrySummaryDeclaration(
    submissionId,
    None,
    Metadata("", "", "", MessageType.IE315, "", "", ""),
    None,
    Parties(None, None, Trader(None, None,None, None), None, None, None),
    Goods(1,None, None, None, None),
    Itinerary("", None, None, None, None, None, None, OfficeOfFirstEntry("", ""), None),
    None
  )

  private def fakeRequest(xml: NodeSeq) = FakeRequest().withBody(ByteString.fromString(xml.toString))

  val mockedMetrics: MetricRegistry = new MetricRegistry()

  val now: Instant = Instant.now
  val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)
  val nrsSubmission: NRSSubmission =
    NRSSubmission(
      rawPayload,
      NRSMetadata(now, submissionId, identityData, fakeRequest(xmlPayload), rawPayload.byteArray.calculateSha256))
  def inputParams(mrn : Option[String]): InputParameters = InputParameters(mrn, submissionId, correlationId, now)

  private val controller = new TestEntryDeclarationSubmissionController(
    Helpers.stubControllerComponents(),
    mockEntryDeclarationStore,
    mockIdGenerator,
    mockValidationHandler,
    mockDeclarationToJsonConverter,
    mockAuthService,
    mockNRSService,
    mockReportSender,
    clock,
    mockedMetrics)

  val validationErrors: ValidationErrors = ValidationErrors(Seq(ValidationError("text", "type", "1235", "location")))

  val validationErrorsXml: Node =
    // @formatter:off
    Utility.trim(
    <err:ErrorResponse xmlns:err="http://www.govtalk.gov.uk/CM/errorresponse" xmlns:dsl="http://decisionsoft.com/rim/errorExtension" SchemaVersion="2.0">
      <err:Application>
        <err:MessageCount>1</err:MessageCount>
      </err:Application>
      <err:Error>
        <err:RaisedBy>HMRC</err:RaisedBy>
        <err:Number>1235</err:Number>
        <err:Type>type</err:Type>
        <err:Text>text</err:Text>
        <err:Location>location</err:Location>
      </err:Error>
    </err:ErrorResponse>)
  // @formatter:on

  val mrnMismatchErrorXml: Node =
    // @formatter:off
    Utility.trim(
      <err:ErrorResponse xmlns:err="http://www.govtalk.gov.uk/CM/errorresponse" xmlns:dsl="http://decisionsoft.com/rim/errorExtension" SchemaVersion="2.0">
        <err:Application>
          <err:MessageCount>1</err:MessageCount>
        </err:Application>
        <err:Error>
          <err:RaisedBy>HMRC</err:RaisedBy>
          <err:Number>8999</err:Number>
          <err:Type>business</err:Type>
          <err:Text>MRN in body and URL do not match.</err:Text>
          <err:Location></err:Location>
        </err:Error>
      </err:ErrorResponse>)
    // @formatter:on

  val serverErrorsXml: Node =
    // @formatter:off
    Utility.trim(
    <err:ErrorResponse xmlns:err="http://www.govtalk.gov.uk/CM/errorresponse" xmlns:dsl="http://decisionsoft.com/rim/errorExtension" SchemaVersion="2.0">
      <err:Application>
        <err:MessageCount>1</err:MessageCount>
      </err:Application>
      <err:Error>
      <err:RaisedBy>HMRC</err:RaisedBy>
      <err:Type>error</err:Type>
      <err:Text>Internal server error</err:Text>
    </err:Error>
  </err:ErrorResponse>)
  // @formatter:on

  private def mockReportUnsuccessfulSubmission(isAmendment: Boolean, failureType: FailureType, submissionHandledData: SubmissionHandledData) =
    MockReportSender.sendReport(SubmissionHandled.Failure(isAmendment, failureType, submissionHandledData)) returns Future.successful((): Unit)

  private def mockReportSuccessfulSubmission(isAmendment: Boolean, submissionHandledData: SubmissionHandledData) =
    MockReportSender.sendReport(SubmissionHandled.Success(isAmendment, submissionHandledData)) returns Future.successful((): Unit)

  def mockServiceFailWithError[E: XmlFormats](
    e: E,
    mrn: Option[String],
    optIdentityData: Option[IdentityData]): Unit = {
    MockAuthService.authenticate returns Future.successful(Some(UserDetails(eori, clientInfo, optIdentityData)))
    MockEntryDeclarationStore
      .handleSubmission(eori, rawPayload, mrn, now, clientInfo, submissionId, correlationId, inputParams(mrn))
      .returns(Future.successful(Left(ErrorWrapper(e))))
  }

  // Authenticating behaviour that both put & post should have...
  def authenticatingEndpoint(mrn: Option[String], handler: Request[ByteString] => Future[Result]): Unit = {

    def check(request: FakeRequest[ByteString], statusCode: Int, errorCode: String) = {
      val result = handler(request)
      status(result)                                                                 shouldBe statusCode
      contentType(result)                                                            shouldBe Some(MimeTypes.XML)
      (xml.XML.loadString(contentAsString(result)) \\ "code").map(_.text).headOption should contain(errorCode)
    }

    "return 401" when {
      "no eori is available from auth service" in {
        MockAuthService.authenticate returns Future.successful(None)
        check(fakeRequest(xmlPayload), UNAUTHORIZED, "UNAUTHORIZED")
      }
    }

    "The submission fails with EORIMismatch" must {
      "return 403 with platform standard xml error body" in {
        MockAuthService.authenticate returns Future.successful(Some(UserDetails(eori, clientInfo, None)))
        setupMocks()
        MockEntryDeclarationStore
          .handleSubmission(eori, rawPayload, mrn, now, clientInfo, submissionId, correlationId, inputParams(mrn))
          .returns(Future.successful(Left(ErrorWrapper(EORIMismatchError))))

        mockReportUnsuccessfulSubmission(mrn.isDefined, FailureType.EORIMismatchError, extractSubmissionHandledDetails(eori,
          None,
          Right(entrySummaryDeclaration)))

        check(fakeRequest(xmlPayload), FORBIDDEN, "FORBIDDEN")
      }
    }
  }

  // Validating behaviour that both put & post should have...
  def validatingEndpoint(mrn: Option[String], handler: Request[ByteString] => Future[Result]): Unit = {
    "The submission fails with ValidationErrors" must {
      "Return BAD_REQUEST" in {
        setupMocks()
        mockServiceFailWithError(validationErrors, mrn, None)
        mockReportUnsuccessfulSubmission(mrn.isDefined, FailureType.ValidationErrors, extractSubmissionHandledDetails(eori, None,
          Right(entrySummaryDeclaration)))
        lazy val result: Future[Result] = handler(fakeRequest(xmlPayload))
        status(result) shouldBe BAD_REQUEST
        val xmlBody: Elem = xml.XML.loadString(contentAsString(result))

        xmlBody             shouldBe validationErrorsXml
        contentType(result) shouldBe Some("application/xml")
      }
      "Not submit to nrs even if enabled" in {
        setupMocks()
        mockServiceFailWithError(validationErrors, mrn, None)
        mockReportUnsuccessfulSubmission(mrn.isDefined, FailureType.ValidationErrors, extractSubmissionHandledDetails(eori,
          None,
          Right(entrySummaryDeclaration)))
        MockNRSService.submit(nrsSubmission).never()

        await(handler(fakeRequest(xmlPayload)))
      }
    }

    "The submission fails with a ServerError (e.g. database problem)" must {
      "Return INTERNAL_SERVER_ERROR" in {
        setupMocks()
        mockServiceFailWithError(ServerError, mrn, None)
        mockReportUnsuccessfulSubmission(mrn.isDefined,
          FailureType.InternalServerError,
          extractSubmissionHandledDetails(eori, None, Right(entrySummaryDeclaration)))
        lazy val result: Future[Result] = handler(fakeRequest(xmlPayload))
        status(result) shouldBe INTERNAL_SERVER_ERROR

        val xmlBody: Elem = xml.XML.loadString(contentAsString(result))
        xmlBody             shouldBe serverErrorsXml
        contentType(result) shouldBe Some("application/xml")
      }
      "Not submit to nrs even if enabled" in {
        setupMocks()
        mockServiceFailWithError(validationErrors, mrn, None)
        mockReportUnsuccessfulSubmission(mrn.isDefined, FailureType.ValidationErrors, extractSubmissionHandledDetails(eori,
          None,
          Right(entrySummaryDeclaration)))
        MockNRSService.submit(nrsSubmission).never()

        await(handler(fakeRequest(xmlPayload)))
      }
    }
  }

  // Payload encoding behaviour that both put & post should have...
  def encodingEndpoint(mrn: Option[String], handler: Request[ByteString] => Future[Result]): Unit = {
    "pass to the service with a character encoding if one is present in the request" in {
      MockAuthService.authenticate returns Future.successful(Some(UserDetails(eori, clientInfo, None)))
      MockIdGenerator.generateCorrelationIdFor(clientInfo) returns correlationId
      MockIdGenerator.generateSubmissionId returns submissionId
      MockValidationHandler.handleValidation(rawPayload.copy(encoding = Some("US-ASCII")), eori, mrn) returns Right(xmlPayload)
      MockDeclarationToJsonConverter.convertToModel(xmlPayload) returns Right(entrySummaryDeclaration)
      mockReportSuccessfulSubmission(mrn.isDefined, extractSubmissionHandledDetails(eori, None, Right(entrySummaryDeclaration)))
      MockEntryDeclarationStore
        .handleSubmission(eori, rawPayload.copy(encoding = Some("US-ASCII")), mrn, now, clientInfo, submissionId, correlationId, inputParams(mrn))
        .returns(Future.successful(Right(SuccessResponse("12345678901234", "3216783621-123873821-12332"))))

      val result: Future[Result] = handler(
        fakeRequest(xmlPayload)
          .withHeaders("Content-Type" -> "application/xml;charset=US-ASCII"))

      status(result) shouldBe OK
    }

    "pass to the service without a character encoding if none is present in the request" in {
      MockAuthService.authenticate returns Future.successful(Some(UserDetails(eori, clientInfo, None)))
      setupMocks()
      mockReportSuccessfulSubmission(mrn.isDefined, extractSubmissionHandledDetails(eori, None, Right(entrySummaryDeclaration)))
      MockEntryDeclarationStore
        .handleSubmission(eori, rawPayload.copy(encoding = None), mrn, now, clientInfo, submissionId, correlationId, inputParams(mrn))
        .returns(Future.successful(Right(SuccessResponse("12345678901234","3216783621-123873821-12332"))))

      val result: Future[Result] = handler(fakeRequest(xmlPayload))

      status(result) shouldBe OK
    }
  }

  def nrsSubmittingEndpoint(mrn: Option[String], handler: Request[ByteString] => Future[Result]): Unit =
    "submission is successful" when {
      "nrs is enabled" must {
        "submit to NRS and not wait until NRS submission completes" in {
          MockAuthService.authenticate returns Future.successful(Some(UserDetails(eori, clientInfo, Some(identityData))))
          setupMocks()
          mockReportSuccessfulSubmission(mrn.isDefined, extractSubmissionHandledDetails(eori, Some(identityData), Right(entrySummaryDeclaration)))
          MockEntryDeclarationStore
            .handleSubmission(eori, rawPayload, mrn, now, clientInfo, submissionId, correlationId, inputParams(mrn))
            .returns(Future.successful(Right(SuccessResponse("12345678901234", "3216783621-123873821-12332"))))

          val nrsPromise = Promise[Option[NRSResponse]]()
          MockNRSService.submit(nrsSubmission) returns nrsPromise.future

          val result: Future[Result] = handler(fakeRequest(xmlPayload))

          status(result) shouldBe OK
        }
      }

      "nrs is not enabled" must {
        "not submit to NRS" in {
          MockAuthService.authenticate returns Future.successful(Some(UserDetails(eori, clientInfo, None)))
          setupMocks()
          mockReportSuccessfulSubmission(mrn.isDefined, extractSubmissionHandledDetails(eori, None, Right(entrySummaryDeclaration)))
          MockEntryDeclarationStore
            .handleSubmission(eori, rawPayload, mrn, now, clientInfo, submissionId, correlationId, inputParams(mrn))
            .returns(Future.successful(Right(SuccessResponse("12345678901234", "3216783621-123873821-12332"))))

          MockNRSService.submit(nrsSubmission).never()

          val result: Future[Result] = handler(fakeRequest(xmlPayload))

          status(result) shouldBe OK
        }
      }
    }

  "TestEntryDeclarationSubmissionController postSubmission" must {
    "Return OK" when {
      "The submission is handled successfully" in {
        MockAuthService.authenticate returns Future.successful(Some(UserDetails(eori, clientInfo, None)))
        setupMocks()
        mockReportSuccessfulSubmission(isAmendment = false, extractSubmissionHandledDetails(eori, None, Right(entrySummaryDeclaration)))
        MockEntryDeclarationStore
          .handleSubmission(eori, rawPayload, None, now, clientInfo, submissionId, correlationId, inputParams(None))
          .returns(Future.successful(Right(SuccessResponse("12345678901234", "3216783621-123873821-12332"))))

        val result: Future[Result] = controller.postSubmissionTestOnly(fakeRequest(xmlPayload))

        status(result) shouldBe OK
        val xmlBody: Elem = xml.XML.loadString(contentAsString(result))
        (xmlBody \\ "CorrelationId").head.text.length shouldBe 14
        contentType(result)                           shouldBe Some("application/xml")
      }
    }

    behave like encodingEndpoint(mrn = None, controller.postSubmissionTestOnly(_))

    behave like validatingEndpoint(mrn = None, controller.postSubmissionTestOnly(_))

    behave like authenticatingEndpoint(mrn = None, controller.postSubmissionTestOnly(_))

    behave like nrsSubmittingEndpoint(mrn = None, controller.postSubmissionTestOnly(_))
  }

  private def setupMocks(): Unit = {
    MockIdGenerator.generateCorrelationIdFor(clientInfo) returns correlationId
    MockIdGenerator.generateSubmissionId returns submissionId
    MockValidationHandler.handleValidation(rawPayload, eori, None) returns Right(xmlPayload)
    MockDeclarationToJsonConverter.convertToModel(xmlPayload) returns Right(entrySummaryDeclaration)
  }
}
