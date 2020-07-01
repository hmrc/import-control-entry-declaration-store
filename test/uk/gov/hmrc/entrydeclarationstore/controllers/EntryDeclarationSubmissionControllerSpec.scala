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

import com.kenshoo.play.metrics.Metrics
import play.api.http.MimeTypes
import play.api.mvc.{Request, Result}
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.entrydeclarationstore.models.{ErrorWrapper, SuccessResponse}
import uk.gov.hmrc.entrydeclarationstore.reporting.ClientType
import uk.gov.hmrc.entrydeclarationstore.services.{MRNMismatchError, MockAuthService, MockEntryDeclarationStore, ServerError}
import uk.gov.hmrc.entrydeclarationstore.utils.{MockMetrics, XmlFormatConfig}
import uk.gov.hmrc.entrydeclarationstore.validation.{ValidationError, ValidationErrors}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.xml.{XML => _, _}

class EntryDeclarationSubmissionControllerSpec extends UnitSpec with MockEntryDeclarationStore with MockAuthService {

  val eori                   = "GB1234567890"
  val mrn                    = "mrn"
  val clientType: ClientType = ClientType.CSP

  implicit val xmlFormatConfig: XmlFormatConfig = XmlFormatConfig(responseMaxErrors = 100)

  val payload: NodeSeq =
    // @formatter:off
    <AnyXml>><MesSenMES3>{eori}</MesSenMES3></AnyXml>
  // @formatter:on

  val payloadNoEori: NodeSeq =
    // @formatter:off
    <AnyXml><MesSenMES3/></AnyXml>
  // @formatter:on

  val payloadBlankEori: NodeSeq =
    // @formatter:off
    <AnyXml></AnyXml>
  // @formatter:on

  private val fakeRequest = FakeRequest().withBody(payload.toString)

  val mockedMetrics: Metrics = new MockMetrics

  private val controller = new EntryDeclarationSubmissionController(
    Helpers.stubControllerComponents(),
    mockEntryDeclarationStore,
    mockAuthService,
    mockedMetrics)

  val validationErrors: ValidationErrors = ValidationErrors(
    Seq(
      ValidationError("text", "type", "1235", "location")
    ))

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

  // Authenticating behaviour that both put & post should have...
  def authenticatingEndpoint(handler: Request[String] => Future[Result]): Unit = {

    def check(request: FakeRequest[String], statusCode: Int, errorCode: String) = {
      val result = handler(request)
      status(result)                                                                 shouldBe statusCode
      contentType(result)                                                            shouldBe Some(MimeTypes.XML)
      (xml.XML.loadString(contentAsString(result)) \\ "code").map(_.text).headOption should contain(errorCode)
    }

    "return 403" when {
      "eori does not match that from auth service" in {
        MockAuthService.authenticate() returns Some(("OTHEREORI", clientType))
        check(fakeRequest, FORBIDDEN, "FORBIDDEN")
      }

      "empty eori element (MesSenMES3) in xml (so that level 2 validation for this is not preempted)" in {
        MockAuthService.authenticate() returns Some((eori, clientType))
        check(FakeRequest().withBody(payloadNoEori.toString), FORBIDDEN, "FORBIDDEN")
      }

      "no eori element (MesSenMES3) in xml (so that level 2 validation for this is not preempted)" in {
        MockAuthService.authenticate() returns Some((eori, clientType))
        check(FakeRequest().withBody(payloadBlankEori.toString), FORBIDDEN, "FORBIDDEN")
      }
    }

    "return 401" when {
      "no eori is available from auth service" in {
        MockAuthService.authenticate() returns None
        check(fakeRequest, UNAUTHORIZED, "UNAUTHORIZED")
      }
    }
  }

  // Validating behaviour that both put & post should have...
  def validatingEndpoint(mrn: Option[String], handler: Request[String] => Future[Result]): Unit = {
    "Return BAD_REQUEST" when {
      "The submission fails with ValidationErrors" in {
        MockAuthService.authenticate() returns Some((eori, clientType))
        MockEntryDeclarationStore
          .handleSubmission(eori, payload.toString(), mrn, clientType)
          .returns(Future.successful(Left(ErrorWrapper(validationErrors))))

        val result: Future[Result] = handler(fakeRequest)

        status(result) shouldBe BAD_REQUEST
        val xmlBody: Elem = xml.XML.loadString(contentAsString(result))

        xmlBody shouldBe validationErrorsXml

        contentType(result) shouldBe Some("application/xml")
      }
    }

    "Return INTERNAL_SERVER_ERROR" when {
      "The submission fails with a ServerError (e.g. database problem)" in {
        MockAuthService.authenticate() returns Some((eori, clientType))
        MockEntryDeclarationStore
          .handleSubmission(eori, payload.toString(), mrn, clientType)
          .returns(Future.successful(Left(ErrorWrapper(ServerError))))
        val result: Future[Result] = handler(fakeRequest)

        status(result) shouldBe INTERNAL_SERVER_ERROR

        val xmlBody: Elem = xml.XML.loadString(contentAsString(result))
        xmlBody shouldBe serverErrorsXml

        contentType(result) shouldBe Some("application/xml")
      }
    }
  }

  "EntryDeclarationSubmissionController postSubmission" should {

    "Return OK" when {
      "The submission is handled successfully" in {
        MockAuthService.authenticate() returns Some((eori, clientType))
        MockEntryDeclarationStore
          .handleSubmission(eori, payload.toString(), None, clientType)
          .returns(Future.successful(Right(SuccessResponse("12345678901234"))))

        val result: Future[Result] = controller.postSubmission(fakeRequest)

        status(result) shouldBe OK
        val xmlBody: Elem = xml.XML.loadString(contentAsString(result))
        (xmlBody \\ "CorrelationId").head.text.length shouldBe 14
        contentType(result)                           shouldBe Some("application/xml")
      }
    }

    behave like validatingEndpoint(mrn = None, controller.postSubmission(_))

    behave like authenticatingEndpoint(controller.postSubmission(_))
  }

  "EntryDeclarationSubmissionController putAmendment" should {
    "Return OK" when {
      "The submission is handled successfully" in {
        MockAuthService.authenticate() returns Some((eori, clientType))
        MockEntryDeclarationStore
          .handleSubmission(eori, payload.toString(), Some(mrn), clientType)
          .returns(Future.successful(Right(SuccessResponse("12345678901234"))))
        val result = controller.putAmendment(mrn)(fakeRequest)

        status(result) shouldBe OK
        val xmlBody: Elem = xml.XML.loadString(contentAsString(result))

        (xmlBody \\ "CorrelationId").head.text.length shouldBe 14
        contentType(result)                           shouldBe Some("application/xml")
      }
    }

    "Return MRNMismatch Bad Request error" when {
      "The MRN in the body doesnt match the MRN in URL" in {
        MockAuthService.authenticate() returns Some((eori, clientType))
        MockEntryDeclarationStore
          .handleSubmission(eori, payload.toString(), Some(mrn), clientType)
          .returns(Future.successful(Left(ErrorWrapper(MRNMismatchError))))

        val result = controller.putAmendment(mrn)(fakeRequest)

        status(result) shouldBe BAD_REQUEST
        val xmlBody: Elem = xml.XML.loadString(contentAsString(result))

        xmlBody shouldBe mrnMismatchErrorXml

        contentType(result) shouldBe Some("application/xml")
      }
    }

    behave like validatingEndpoint(mrn = Some(mrn), controller.putAmendment(mrn)(_))

    behave like authenticatingEndpoint(controller.putAmendment(mrn)(_))
  }
}
