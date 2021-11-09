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

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.wordspec.AnyWordSpec
import play.api.http.{HeaderNames, Status}
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.Helpers.{defaultAwaitTimeout, stubControllerComponents}
import play.api.test.{FakeRequest, ResultExtractors}
import uk.gov.hmrc.entrydeclarationstore.config.MockAppConfig
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class EisInboundAuthorisedControllerSpec
    extends AnyWordSpec
    with Status
    with HeaderNames
    with ResultExtractors
    with ScalaFutures
    with MockAppConfig {

  lazy val cc: ControllerComponents = stubControllerComponents()
  lazy val bearerToken              = "bearerToken"

  trait Test {
    val hc: HeaderCarrier = HeaderCarrier()

    class TestController extends EisInboundAuthorisedController(cc, mockAppConfig) {
      def action(): Action[AnyContent] = authorisedAction.async {
        Future.successful(Ok(Json.obj()))
      }
    }

    lazy val controller = new TestController()
  }

  "calling an action" when {

    "return a 200" when {
      "the user is authorised" in new Test {
        MockAppConfig.eisInboundBearerToken returns bearerToken

        val fakeGetRequest: FakeRequest[AnyContentAsEmpty.type] =
          FakeRequest().withHeaders(HeaderNames.AUTHORIZATION -> s"Bearer $bearerToken")
        private val result: Future[Result] = controller.action()(fakeGetRequest)

        status(result) shouldBe OK
      }
    }

    "return a 403" when {
      "user is not authorised" in new Test {
        MockAppConfig.eisInboundBearerToken returns bearerToken

        val badBearerToken = "xxx"
        val fakeGetRequest: FakeRequest[AnyContentAsEmpty.type] =
          FakeRequest().withHeaders(HeaderNames.AUTHORIZATION -> s"Bearer $badBearerToken")
        private val result: Future[Result] = controller.action()(fakeGetRequest)

        status(result) shouldBe FORBIDDEN
      }
      "no bearer token is supplied" in new Test {
        private val result: Future[Result] = controller.action()(FakeRequest())

        status(result) shouldBe FORBIDDEN
      }
    }
  }

}
