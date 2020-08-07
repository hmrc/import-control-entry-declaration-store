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
import org.scalamock.matchers.ArgCapture.CaptureOne
import org.scalatest.concurrent.ScalaFutures
import play.api.http.{HeaderNames, Status}
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test.{FakeRequest, ResultExtractors}
import play.mvc.Http.MimeTypes
import uk.gov.hmrc.entrydeclarationstore.nrs.NRSMetadataTestData
import uk.gov.hmrc.entrydeclarationstore.reporting.ClientType
import uk.gov.hmrc.entrydeclarationstore.services.{AuthService, MockAuthService, UserDetails}
import uk.gov.hmrc.entrydeclarationstore.utils.{EventLogger, MockMetrics, Timer}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.logging.Authorization
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future
import scala.xml.{Node, Utility}

class AuthorisedControllerSpec
    extends UnitSpec
    with Status
    with HeaderNames
    with ResultExtractors
    with ScalaFutures
    with MockAuthService
    with NRSMetadataTestData {

  lazy val cc: ControllerComponents = stubControllerComponents()
  lazy val bearerToken              = "Bearer Token"
  def request(body: String): Request[String] =
    FakeRequest()
      .withHeaders(
        HeaderNames.AUTHORIZATION -> bearerToken
      )
      .withBody(body)

  val eori                   = "GB123"
  val clientType: ClientType = ClientType.CSP

  val bodyContainingEori      = s"<someXml>$eori</someXml>"
  val bodyContainingOtherEori = s"<someXml>otherEori</someXml>"

  // WLOG
  val userDetails: UserDetails = UserDetails(eori, clientType, None)

  trait Test {
    val hc: HeaderCarrier = HeaderCarrier()

    class TestController extends AuthorisedController(cc) with Timer with EventLogger {
      override val authService: AuthService = mockAuthService

      override def eoriCorrectForRequest[A](request: Request[A], eori: String): Boolean =
        request.body match {
          case b: String => b.contains(eori)
          case _         => false
        }

      def action(): Action[String] = authorisedAction().async(parse.tolerantText) { userRequest =>
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

  val forbiddenXml: Node = Utility.trim(<error>
      <code>FORBIDDEN</code>
      <message>Permission denied</message>
    </error>)

  "calling an action" when {

    "the user is authorised" should {
      "return a 200" in new Test {
        MockAuthService.authenticate() returns Future.successful(Some(userDetails))

        private val result: Future[Result] = controller.action()(request(bodyContainingEori))
        status(await(result)) shouldBe OK
      }
    }

    "user is not authorised" should {
      "return a 401" in new Test {
        MockAuthService.authenticate() returns Future.successful(None)

        private val result: Future[Result] = controller.action()(request(bodyContainingEori))
        status(await(result))                                     shouldBe UNAUTHORIZED
        Utility.trim(xml.XML.loadString(contentAsString(result))) shouldBe unauthorisedXml
        contentType(result)                                       shouldBe Some(MimeTypes.XML)
      }
    }

    "auth eori does not match that in the request payload" should {
      "return a 403" in new Test {
        MockAuthService.authenticate() returns Future.successful(Some(userDetails))

        private val result: Future[Result] = controller.action()(request(bodyContainingOtherEori))
        status(await(result))                                     shouldBe FORBIDDEN
        Utility.trim(xml.XML.loadString(contentAsString(result))) shouldBe forbiddenXml
        contentType(result)                                       shouldBe Some(MimeTypes.XML)
      }
    }
  }

  "AuthController" should {
    "use the authorization header to send to auth service" in new Test {
      val hcCapture: CaptureOne[HeaderCarrier] = CaptureOne[HeaderCarrier]()
      MockAuthService.authenticateCapture()(hcCapture) returns Future.successful(Some(userDetails))
      controller.action()(request(bodyContainingEori))

      hcCapture.value.authorization shouldBe Some(Authorization(bearerToken))
    }
  }
}
