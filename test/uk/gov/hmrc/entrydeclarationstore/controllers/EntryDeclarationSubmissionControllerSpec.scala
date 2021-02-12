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
import play.api.http.MimeTypes
import play.api.mvc.{Request, Result}
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.entrydeclarationstore.config.MockAppConfig
import uk.gov.hmrc.entrydeclarationstore.models.{ErrorWrapper, RawPayload, StandardError, SuccessResponse}
import uk.gov.hmrc.entrydeclarationstore.nrs._
import uk.gov.hmrc.entrydeclarationstore.reporting._
import uk.gov.hmrc.entrydeclarationstore.services._
import uk.gov.hmrc.entrydeclarationstore.utils.ChecksumUtils._
import uk.gov.hmrc.entrydeclarationstore.utils.{MockMetrics, XmlFormatConfig, XmlFormats}
import uk.gov.hmrc.entrydeclarationstore.validation.{ValidationError, ValidationErrors}
import uk.gov.hmrc.play.test.UnitSpec

import java.time.{Clock, Instant, ZoneOffset}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import scala.xml.{XML => _, _}

class EntryDeclarationSubmissionControllerSpec
    extends UnitSpec
    with MockEntryDeclarationStore
    with MockAuthService
    with MockNRSService
    with NRSMetadataTestData
    with MockReportSender
    with MockAppConfig {

  val eori                   = "GB1234567890"
  val mrn                    = "mrn"
  val clientInfo: ClientInfo = ClientInfo(ClientType.CSP, None, None)

  implicit val xmlFormatConfig: XmlFormatConfig = XmlFormatConfig(responseMaxErrors = 100)

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

  private def fakeRequest(xml: NodeSeq) = FakeRequest().withBody(ByteString.fromString(xml.toString))

  val mockedMetrics: Metrics = new MockMetrics

  val now: Instant = Instant.now
  val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)
  val nrsSubmission: NRSSubmission =
    NRSSubmission(
      rawPayload,
      NRSMetadata(now, eori, identityData, fakeRequest(xmlPayload), rawPayload.byteArray.calculateSha256))

  private val controller = new EntryDeclarationSubmissionController(
    Helpers.stubControllerComponents(),
    mockEntryDeclarationStore,
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

  private def mockReportUnsuccessfulSubmission(isAmendment: Boolean, failureType: FailureType) =
    MockReportSender.sendReport(SubmissionHandled.Failure(isAmendment, failureType)) returns Future.successful((): Unit)

  private def mockReportSuccessfulSubmission(isAmendment: Boolean) =
    MockReportSender.sendReport(SubmissionHandled.Success(isAmendment)) returns Future.successful((): Unit)

  def mockServiceFailWithError[E: XmlFormats](
    e: E,
    mrn: Option[String],
    optIdentityData: Option[IdentityData]): Unit = {
    MockAuthService.authenticate returns Some(UserDetails(eori, clientInfo, optIdentityData))
    MockEntryDeclarationStore
      .handleSubmission(eori, rawPayload, mrn, now, clientInfo)
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

    "return 403" when {
      "eori does not match that from auth service" in {
        MockAuthService.authenticate returns Some(UserDetails("OTHEREORI", clientInfo, None))
        mockReportUnsuccessfulSubmission(mrn.isDefined, FailureType.EORIMismatchError)
        check(fakeRequest(xmlPayload), FORBIDDEN, "FORBIDDEN")
      }

      "empty eori element (MesSenMES3) in xml (so that level 2 validation for this is not preempted)" in {
        MockAuthService.authenticate returns Some(UserDetails(eori, clientInfo, None))
        mockReportUnsuccessfulSubmission(mrn.isDefined, FailureType.EORIMismatchError)
        check(fakeRequest(xmlPayloadNoEori), FORBIDDEN, "FORBIDDEN")
      }

      "no eori element (MesSenMES3) in xml (so that level 2 validation for this is not preempted)" in {
        MockAuthService.authenticate returns Some(UserDetails(eori, clientInfo, None))
        mockReportUnsuccessfulSubmission(mrn.isDefined, FailureType.EORIMismatchError)
        check(fakeRequest(xmlPayloadBlankEori), FORBIDDEN, "FORBIDDEN")
      }
    }

    "return 401" when {
      "no eori is available from auth service" in {
        MockAuthService.authenticate returns None
        check(fakeRequest(xmlPayload), UNAUTHORIZED, "UNAUTHORIZED")
      }
    }

    "The submission fails with EORI mismatch" should {
      "return 403 with platform standard xml error body" in {
        MockAuthService.authenticate returns Some(UserDetails(eori, clientInfo, None))
        MockEntryDeclarationStore
          .handleSubmission(eori, rawPayload, mrn, now, clientInfo)
          .returns(Future.successful(Left(ErrorWrapper(StandardError.EORIMismatch))))

        mockReportUnsuccessfulSubmission(mrn.isDefined, FailureType.EORIMismatchError)

        check(fakeRequest(xmlPayload), FORBIDDEN, "FORBIDDEN")
      }
    }
  }

  // Validating behaviour that both put & post should have...
  def validatingEndpoint(mrn: Option[String], handler: Request[ByteString] => Future[Result]): Unit = {

    "The submission fails with ValidationErrors" should {
      "Return BAD_REQUEST" in {
        mockServiceFailWithError(validationErrors, mrn, None)
        mockReportUnsuccessfulSubmission(mrn.isDefined, FailureType.ValidationErrors)

        val result: Future[Result] = handler(fakeRequest(xmlPayload))
        status(result) shouldBe BAD_REQUEST
        val xmlBody: Elem = xml.XML.loadString(contentAsString(result))

        xmlBody             shouldBe validationErrorsXml
        contentType(result) shouldBe Some("application/xml")
      }
      "Not submit to nrs even if enabled" in {
        mockServiceFailWithError(validationErrors, mrn, Some(identityData))
        mockReportUnsuccessfulSubmission(mrn.isDefined, FailureType.ValidationErrors)
        MockNRSService.submit(nrsSubmission).never()

        await(handler(fakeRequest(xmlPayload)))
      }
    }

    "The submission fails with a ServerError (e.g. database problem)" should {
      "Return INTERNAL_SERVER_ERROR" in {
        mockServiceFailWithError(ServerError, mrn, None)
        mockReportUnsuccessfulSubmission(mrn.isDefined, FailureType.InternalServerError)

        val result: Future[Result] = handler(fakeRequest(xmlPayload))
        status(result) shouldBe INTERNAL_SERVER_ERROR

        val xmlBody: Elem = xml.XML.loadString(contentAsString(result))
        xmlBody             shouldBe serverErrorsXml
        contentType(result) shouldBe Some("application/xml")
      }
      "Not submit to nrs even if enabled" in {
        mockServiceFailWithError(ServerError, mrn, Some(identityData))
        mockReportUnsuccessfulSubmission(mrn.isDefined, FailureType.InternalServerError)
        MockNRSService.submit(nrsSubmission).never()

        await(handler(fakeRequest(xmlPayload)))
      }
    }
  }

  // Payload encoding behaviour that both put & post should have...
  def encodingEndpoint(mrn: Option[String], handler: Request[ByteString] => Future[Result]): Unit = {
    "pass to the service with a character encoding if one is present in the request" in {
      MockAuthService.authenticate returns Some(UserDetails(eori, clientInfo, None))
      mockReportSuccessfulSubmission(mrn.isDefined)
      MockEntryDeclarationStore
        .handleSubmission(eori, rawPayload.copy(encoding = Some("US-ASCII")), mrn, now, clientInfo)
        .returns(Future.successful(Right(SuccessResponse("12345678901234"))))

      val result: Future[Result] = handler(
        fakeRequest(xmlPayload)
          .withHeaders("Content-Type" -> "application/xml;charset=US-ASCII"))

      status(result) shouldBe OK
    }

    "pass to the service without a character encoding if none is present in the request" in {
      MockAuthService.authenticate returns Some(UserDetails(eori, clientInfo, None))
      mockReportSuccessfulSubmission(mrn.isDefined)
      MockEntryDeclarationStore
        .handleSubmission(eori, rawPayload.copy(encoding = None), mrn, now, clientInfo)
        .returns(Future.successful(Right(SuccessResponse("12345678901234"))))

      val result: Future[Result] = handler(fakeRequest(xmlPayload))

      status(result) shouldBe OK
    }
  }

  def nrsSubmittingEndpoint(mrn: Option[String], handler: Request[ByteString] => Future[Result]): Unit =
    "submission is successful" when {
      "nrs is enabled" must {
        "submit to NRS and not wait until NRS submission completes" in {
          MockAuthService.authenticate returns Some(UserDetails(eori, clientInfo, Some(identityData)))
          mockReportSuccessfulSubmission(mrn.isDefined)
          MockEntryDeclarationStore
            .handleSubmission(eori, rawPayload, mrn, now, clientInfo)
            .returns(Future.successful(Right(SuccessResponse("12345678901234"))))

          val nrsPromise = Promise[Option[NRSResponse]]
          MockNRSService.submit(nrsSubmission) returns nrsPromise.future

          val result: Future[Result] = handler(fakeRequest(xmlPayload))

          status(result) shouldBe OK
        }
      }

      "nrs is not enabled" must {
        "not submit to NRS" in {
          MockAuthService.authenticate returns Some(UserDetails(eori, clientInfo, None))
          mockReportSuccessfulSubmission(mrn.isDefined)
          MockEntryDeclarationStore
            .handleSubmission(eori, rawPayload, mrn, now, clientInfo)
            .returns(Future.successful(Right(SuccessResponse("12345678901234"))))

          MockNRSService.submit(nrsSubmission).never()

          val result: Future[Result] = handler(fakeRequest(xmlPayload))

          status(result) shouldBe OK
        }
      }
    }

  "EntryDeclarationSubmissionController postSubmission" should {
    "Return OK" when {
      "The submission is handled successfully" in {
        MockAuthService.authenticate returns Some(UserDetails(eori, clientInfo, None))
        mockReportSuccessfulSubmission(false)
        MockEntryDeclarationStore
          .handleSubmission(eori, rawPayload, None, now, clientInfo)
          .returns(Future.successful(Right(SuccessResponse("12345678901234"))))

        val result: Future[Result] = controller.postSubmission(fakeRequest(xmlPayload))

        status(result) shouldBe OK
        val xmlBody: Elem = xml.XML.loadString(contentAsString(result))
        (xmlBody \\ "CorrelationId").head.text.length shouldBe 14
        contentType(result)                           shouldBe Some("application/xml")
      }
    }

    behave like encodingEndpoint(mrn = None, controller.postSubmission(_))

    behave like validatingEndpoint(mrn = None, controller.postSubmission(_))

    behave like authenticatingEndpoint(mrn = None, controller.postSubmission(_))

    behave like nrsSubmittingEndpoint(mrn = None, controller.postSubmission(_))
  }

  "EntryDeclarationSubmissionController putAmendment" when {
    "The submission is handled successfully" should {
      "Return OK" in {
        MockAuthService.authenticate returns Some(UserDetails(eori, clientInfo, None))
        mockReportSuccessfulSubmission(true)
        MockEntryDeclarationStore
          .handleSubmission(eori, rawPayload, Some(mrn), now, clientInfo)
          .returns(Future.successful(Right(SuccessResponse("12345678901234"))))

        val result = controller.putAmendment(mrn)(fakeRequest(xmlPayload))

        status(result) shouldBe OK
        val xmlBody: Elem = xml.XML.loadString(contentAsString(result))

        (xmlBody \\ "CorrelationId").head.text.length shouldBe 14
        contentType(result)                           shouldBe Some("application/xml")
      }
    }

    "The MRN in the body doesnt match the MRN in URL" should {
      "Return MRNMismatch Bad Request error" in {
        MockAuthService.authenticate returns Some(UserDetails(eori, clientInfo, None))
        mockReportUnsuccessfulSubmission(isAmendment = true, FailureType.MRNMismatchError)
        MockEntryDeclarationStore
          .handleSubmission(eori, rawPayload, Some(mrn), now, clientInfo)
          .returns(Future.successful(Left(ErrorWrapper(MRNMismatchError))))

        val result = controller.putAmendment(mrn)(fakeRequest(xmlPayload))

        status(result) shouldBe BAD_REQUEST
        val xmlBody: Elem = xml.XML.loadString(contentAsString(result))

        xmlBody shouldBe mrnMismatchErrorXml

        contentType(result) shouldBe Some("application/xml")
      }

      "Not submit to nrs even if enabled" in {
        MockAuthService.authenticate returns Some(UserDetails(eori, clientInfo, Some(identityData)))
        mockReportUnsuccessfulSubmission(isAmendment = true, FailureType.MRNMismatchError)
        MockEntryDeclarationStore
          .handleSubmission(eori, rawPayload, Some(mrn), now, clientInfo)
          .returns(Future.successful(Left(ErrorWrapper(MRNMismatchError))))

        MockNRSService.submit(nrsSubmission).never()

        await(controller.putAmendment(mrn)(fakeRequest(xmlPayload)))
      }
    }

    behave like encodingEndpoint(mrn = Some(mrn), controller.putAmendment(mrn)(_))

    behave like validatingEndpoint(mrn = Some(mrn), controller.putAmendment(mrn)(_))

    behave like authenticatingEndpoint(mrn = Some(mrn), controller.putAmendment(mrn)(_))

    behave like nrsSubmittingEndpoint(mrn = Some(mrn), controller.putAmendment(mrn)(_))
  }
}
