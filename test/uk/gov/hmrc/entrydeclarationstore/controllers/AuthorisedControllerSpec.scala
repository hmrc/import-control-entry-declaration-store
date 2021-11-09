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

import com.kenshoo.play.metrics.Metrics
import org.scalamock.matchers.ArgCapture.CaptureOne
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.wordspec.AnyWordSpec
import play.api.Logging
import play.api.http.{HeaderNames, Status}
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test.{FakeRequest, ResultExtractors}
import play.mvc.Http.MimeTypes
import uk.gov.hmrc.entrydeclarationstore.nrs.NRSMetadataTestData
import uk.gov.hmrc.entrydeclarationstore.reporting.{ClientInfo, ClientType}
import uk.gov.hmrc.entrydeclarationstore.services.{AuthService, MockAuthService, UserDetails}
import uk.gov.hmrc.entrydeclarationstore.utils.{MockMetrics, Timer}
import uk.gov.hmrc.http.{Authorization, HeaderCarrier}

import scala.concurrent.Future
import scala.xml.{Node, Utility}

class AuthorisedControllerSpec
    extends AnyWordSpec
    with Status
    with HeaderNames
    with ResultExtractors
    with ScalaFutures
    with MockAuthService
    with NRSMetadataTestData {

  lazy val cc: ControllerComponents = stubControllerComponents()
  lazy val bearerToken              = "Bearer Token"
  val request: FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest().withHeaders(HeaderNames.AUTHORIZATION -> bearerToken)

  val eori                   = "GB123"
  val clientInfo: ClientInfo = ClientInfo(ClientType.CSP, None, None)

  // WLOG - whatever the service returns should be passed to the custom controller code block...
  val userDetails: UserDetails = UserDetails(eori, clientInfo, None)

  trait Test {
    val hc: HeaderCarrier   = HeaderCarrier()
    val mrn: Option[String] = None

    class TestController extends AuthorisedController(cc) with Timer with Logging {
      override val authService: AuthService = mockAuthService

      def action(): Action[AnyContent] = authorisedAction().async { userRequest =>
        userRequest.userDetails shouldBe userDetails
        Future.successful(Ok(Json.obj()))
      }

      override val metrics: Metrics = new MockMetrics
    }

    lazy val controller = new TestController()
  }

  val unauthorisedXml: Node = Utility.trim(<error>
      <code>UNAUTHORIZED</code>
      <message>Permission denied</message>
    </error>)

  "calling an action" when {

    "the user is authorised" must {
      "return a 200" in new Test {
        MockAuthService.authenticate returns Future.successful(Some(userDetails))

        private val result: Future[Result] = controller.action()(request)
        status(result) shouldBe OK
      }
    }

    "user is not authorised" must {
      "return a 401" in new Test {
        MockAuthService.authenticate returns Future.successful(None)

        private val result: Future[Result] = controller.action()(request)
        status(result)                                            shouldBe UNAUTHORIZED
        Utility.trim(xml.XML.loadString(contentAsString(result))) shouldBe unauthorisedXml
        contentType(result)                                       shouldBe Some(MimeTypes.XML)
      }
    }
  }

  "AuthController" must {
    "use the authorization header to send to auth service" in new Test {
      val hcCapture: CaptureOne[HeaderCarrier] = CaptureOne[HeaderCarrier]()
      MockAuthService.authenticateCapture(hcCapture) returns Future.successful(Some(userDetails))
      controller.action()(request)

      hcCapture.value.authorization shouldBe Some(Authorization(bearerToken))
    }
  }
}
