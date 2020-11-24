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

import java.time.{Clock, Instant, ZoneOffset}

import com.kenshoo.play.metrics.Metrics
import play.api.http.MimeTypes
import play.api.mvc.{Request, Result}
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.entrydeclarationstore.config.MockAppConfig
import uk.gov.hmrc.entrydeclarationstore.models.{ErrorWrapper, SuccessResponse}
import uk.gov.hmrc.entrydeclarationstore.nrs._
import uk.gov.hmrc.entrydeclarationstore.reporting.ClientType
import uk.gov.hmrc.entrydeclarationstore.reporting.audit.{AuditEvent, MockAuditHandler}
import uk.gov.hmrc.entrydeclarationstore.services._
import uk.gov.hmrc.entrydeclarationstore.utils.ChecksumUtils.StringWithSha256
import uk.gov.hmrc.entrydeclarationstore.utils.{MockMetrics, XmlFormatConfig, XmlFormats}
import uk.gov.hmrc.entrydeclarationstore.validation.{ValidationError, ValidationErrors}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import scala.xml.{XML => _, _}

class EntryDeclarationSubmissionControllerSpec
    extends UnitSpec
    with MockEntryDeclarationStore
    with MockAuthService
    with MockNRSService
    with NRSMetadataTestData
    with MockAuditHandler
    with MockAppConfig {

  val eori                   = "GB1234567890"
  val mrn                    = "mrn"
  val clientType: ClientType = ClientType.CSP

  implicit val xmlFormatConfig: XmlFormatConfig = XmlFormatConfig(responseMaxErrors = 100)

  val payload: NodeSeq =
    // @formatter:off
    <AnyXml>><MesSenMES3>{eori}</MesSenMES3></AnyXml>
  // @formatter:on

  private val payloadString: String = payload.toString

  val payloadNoEori: NodeSeq =
    // @formatter:off
    <AnyXml><MesSenMES3/></AnyXml>
  // @formatter:on

  val payloadBlankEori: NodeSeq =
    // @formatter:off
    <AnyXml></AnyXml>
  // @formatter:on

  private val fakeRequest = FakeRequest().withBody(payloadString)

  val mockedMetrics: Metrics = new MockMetrics

  val now: Instant                 = Instant.now
  val clock: Clock                 = Clock.fixed(now, ZoneOffset.UTC)
  val nrsSubmission: NRSSubmission = NRSSubmission(payloadString, NRSMetadata(now, eori, identityData, fakeRequest, payloadString.calculateSha256))

  private val controller = new EntryDeclarationSubmissionController(
    Helpers.stubControllerComponents(),
    mockEntryDeclarationStore,
    mockAuthService,
    mockNRSService,
    mockAuditHandler,
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
  
  private def mockAuditUnsuccessfulSubmission(isAmendment: Boolean) =
    MockAuditHandler.audit(AuditEvent.auditFailure(isAmendment)) returns Future.successful(():Unit)

  private def mockAuditSuccessfulSubmission(isAmendment: Boolean) =
    MockAuditHandler.audit(AuditEvent.auditSuccess(isAmendment)) returns Future.successful(():Unit)


  // Authenticating behaviour that both put & post should have...
  def authenticatingEndpoint(mrn: Option[String], handler: Request[String] => Future[Result]): Unit = {

    def check(request: FakeRequest[String], statusCode: Int, errorCode: String) = {
      val result = handler(request)
      status(result)                                                                 shouldBe statusCode
      contentType(result)                                                            shouldBe Some(MimeTypes.XML)
      (xml.XML.loadString(contentAsString(result)) \\ "code").map(_.text).headOption should contain(errorCode)
    }

    "return 403" when {
      "eori does not match that from auth service" in {
        MockAuthService.authenticate returns Some(UserDetails("OTHEREORI", clientType, None))
        mockAuditUnsuccessfulSubmission(mrn.isDefined)
        check(fakeRequest, FORBIDDEN, "FORBIDDEN")
      }

      "empty eori element (MesSenMES3) in xml (so that level 2 validation for this is not preempted)" in {
        MockAuthService.authenticate returns Some(UserDetails(eori, clientType, None))
        mockAuditUnsuccessfulSubmission(mrn.isDefined)
        check(FakeRequest().withBody(payloadNoEori.toString), FORBIDDEN, "FORBIDDEN")
      }

      "no eori element (MesSenMES3) in xml (so that level 2 validation for this is not preempted)" in {
        MockAuthService.authenticate returns Some(UserDetails(eori, clientType, None))
        mockAuditUnsuccessfulSubmission(mrn.isDefined)
        check(FakeRequest().withBody(payloadBlankEori.toString), FORBIDDEN, "FORBIDDEN")
      }
    }

    "return 401" when {
      "no eori is available from auth service" in {
        MockAuthService.authenticate returns None
        check(fakeRequest, UNAUTHORIZED, "UNAUTHORIZED")
      }
    }
  }

  // Validating behaviour that both put & post should have...
  def validatingEndpoint(mrn: Option[String], handler: Request[String] => Future[Result]): Unit = {

    def mockFailWithError[E: XmlFormats](e: E, optIdentityData: Option[IdentityData]) = {
      MockAuthService.authenticate returns Some(UserDetails(eori, clientType, optIdentityData))
      MockEntryDeclarationStore
        .handleSubmission(eori, payloadString, mrn, now, clientType)
        .returns(Future.successful(Left(ErrorWrapper(e))))
    }

    "The submission fails with ValidationErrors" should {
      "Return BAD_REQUEST" in {
        mockFailWithError(validationErrors, None)
        mockAuditUnsuccessfulSubmission(mrn.isDefined)

        val result: Future[Result] = handler(fakeRequest)
        status(result) shouldBe BAD_REQUEST
        val xmlBody: Elem = xml.XML.loadString(contentAsString(result))

        xmlBody             shouldBe validationErrorsXml
        contentType(result) shouldBe Some("application/xml")
      }
      "Not submit to nrs even if enabled" in {
        mockFailWithError(validationErrors, Some(identityData))
        mockAuditUnsuccessfulSubmission(mrn.isDefined)
        MockNRSService.submit(nrsSubmission).never()

        await(handler(fakeRequest))
      }
    }

    "The submission fails with a ServerError (e.g. database problem)" should {
      "Return INTERNAL_SERVER_ERROR" in {
        mockFailWithError(ServerError, None)
        mockAuditUnsuccessfulSubmission(mrn.isDefined)

        val result: Future[Result] = handler(fakeRequest)
        status(result) shouldBe INTERNAL_SERVER_ERROR

        val xmlBody: Elem = xml.XML.loadString(contentAsString(result))
        xmlBody             shouldBe serverErrorsXml
        contentType(result) shouldBe Some("application/xml")
      }
      "Not submit to nrs even if enabled" in {
        mockFailWithError(ServerError, Some(identityData))
        mockAuditUnsuccessfulSubmission(mrn.isDefined)
        MockNRSService.submit(nrsSubmission).never()

        await(handler(fakeRequest))
      }
    }
  }

  def nrsSubmittingEndpoint(mrn: Option[String], handler: Request[String] => Future[Result]): Unit =
    "submission is successful" when {
      "nrs is enabled" must {
        "submit to NRS and not wait until NRS submission completes" in {
          MockAuthService.authenticate returns Some(UserDetails(eori, clientType, Some(identityData)))
          mockAuditSuccessfulSubmission(mrn.isDefined)
          MockEntryDeclarationStore
            .handleSubmission(eori, payloadString, mrn, now, clientType)
            .returns(Future.successful(Right(SuccessResponse("12345678901234"))))

          val nrsPromise = Promise[Option[NRSResponse]]
          MockNRSService.submit(nrsSubmission) returns nrsPromise.future

          val result: Future[Result] = handler(fakeRequest)

          status(result) shouldBe OK
        }
      }

      "nrs is not enabled" must {
        "not submit to NRS" in {
          MockAuthService.authenticate returns Some(UserDetails(eori, clientType, None))
          mockAuditSuccessfulSubmission(mrn.isDefined)
          MockEntryDeclarationStore
            .handleSubmission(eori, payloadString, mrn, now, clientType)
            .returns(Future.successful(Right(SuccessResponse("12345678901234"))))

          MockNRSService.submit(nrsSubmission).never()

          val result: Future[Result] = handler(fakeRequest)

          status(result) shouldBe OK
        }
      }
    }

  def asynchronousAuditingEndpoint(mrn: Option[String], handler: Request[String] => Future[Result]): Unit =
    "auditing" should {
      "be asynchronous" when {
        "submission is successful" in {
          val auditPromise = Promise[Unit]

          MockAuthService.authenticate returns Some(UserDetails(eori, clientType, None))
          MockEntryDeclarationStore
            .handleSubmission(eori, payloadString, mrn, now, clientType)
            .returns(Future.successful(Right(SuccessResponse("12345678901234"))))
          MockAuditHandler.audit(AuditEvent.auditSuccess(mrn.isDefined)) returns auditPromise.future

          val result: Future[Result] = handler(fakeRequest)
          status(result) shouldBe OK
        }
        "submission has validation errors" in {
          val auditPromise = Promise[Unit]

          MockAuthService.authenticate returns Some(UserDetails(eori, clientType, None))
          MockEntryDeclarationStore
            .handleSubmission(eori, payloadString, mrn, now, clientType)
            .returns(Future.successful(Left(ErrorWrapper(validationErrors))))
          MockAuditHandler.audit(AuditEvent.auditFailure(mrn.isDefined)) returns auditPromise.future

          val result: Future[Result] = handler(fakeRequest)
          status(result) shouldBe BAD_REQUEST
        }
        "submission to return 403 Forbidden" in {
          val auditPromise = Promise[Unit]

          MockAuthService.authenticate returns Some(UserDetails("OTHEREORI", clientType, None))
          MockAuditHandler.audit(AuditEvent.auditFailure(mrn.isDefined)) returns auditPromise.future

          val result: Future[Result] = handler(fakeRequest)
          status(result) shouldBe FORBIDDEN
        }
      }
    }

    "EntryDeclarationSubmissionController postSubmission" should {
    "Return OK" when {
      "The submission is handled successfully" in {
        MockAuthService.authenticate returns Some(UserDetails(eori, clientType, None))
        mockAuditSuccessfulSubmission(false)
        MockEntryDeclarationStore
          .handleSubmission(eori, payloadString, None, now, clientType)
          .returns(Future.successful(Right(SuccessResponse("12345678901234"))))

        val result: Future[Result] = controller.postSubmission(fakeRequest)

        status(result) shouldBe OK
        val xmlBody: Elem = xml.XML.loadString(contentAsString(result))
        (xmlBody \\ "CorrelationId").head.text.length shouldBe 14
        contentType(result)                           shouldBe Some("application/xml")
      }
    }

    behave like validatingEndpoint(mrn = None, controller.postSubmission(_))

    behave like authenticatingEndpoint(mrn = None, controller.postSubmission(_))

    behave like nrsSubmittingEndpoint(mrn = None, controller.postSubmission(_))

    behave like asynchronousAuditingEndpoint(mrn = None, controller.postSubmission(_))
  }

  "EntryDeclarationSubmissionController putAmendment" when {
    "The submission is handled successfully" should {
      "Return OK" in {
        MockAuthService.authenticate returns Some(UserDetails(eori, clientType, None))
        mockAuditSuccessfulSubmission(true)
        MockEntryDeclarationStore
          .handleSubmission(eori, payloadString, Some(mrn), now, clientType)
          .returns(Future.successful(Right(SuccessResponse("12345678901234"))))

        val result = controller.putAmendment(mrn)(fakeRequest)

        status(result) shouldBe OK
        val xmlBody: Elem = xml.XML.loadString(contentAsString(result))

        (xmlBody \\ "CorrelationId").head.text.length shouldBe 14
        contentType(result)                           shouldBe Some("application/xml")
      }
    }

    "The MRN in the body doesnt match the MRN in URL" should {
      "Return MRNMismatch Bad Request error" in {
        MockAuthService.authenticate returns Some(UserDetails(eori, clientType, None))
        MockAuditHandler.audit(AuditEvent.auditFailure(true)) returns Future.successful(():Unit)
        MockEntryDeclarationStore
          .handleSubmission(eori, payloadString, Some(mrn), now, clientType)
          .returns(Future.successful(Left(ErrorWrapper(MRNMismatchError))))

        val result = controller.putAmendment(mrn)(fakeRequest)

        status(result) shouldBe BAD_REQUEST
        val xmlBody: Elem = xml.XML.loadString(contentAsString(result))

        xmlBody shouldBe mrnMismatchErrorXml

        contentType(result) shouldBe Some("application/xml")
      }

      "Not submit to nrs even if enabled" in {
        MockAuthService.authenticate returns Some(UserDetails(eori, clientType, Some(identityData)))
        MockAuditHandler.audit(AuditEvent.auditFailure(true)) returns Future.successful(():Unit)
        MockEntryDeclarationStore
          .handleSubmission(eori, payloadString, Some(mrn), now, clientType)
          .returns(Future.successful(Left(ErrorWrapper(MRNMismatchError))))

        MockNRSService.submit(nrsSubmission).never()

        await(controller.putAmendment(mrn)(fakeRequest))
      }
    }

    behave like validatingEndpoint(mrn = Some(mrn), controller.putAmendment(mrn)(_))

    behave like authenticatingEndpoint(mrn = Some(mrn), controller.putAmendment(mrn)(_))

    behave like nrsSubmittingEndpoint(mrn = Some(mrn), controller.putAmendment(mrn)(_))

    behave like asynchronousAuditingEndpoint(mrn = Some(mrn), controller.putAmendment(mrn)(_))
  }
}
