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

package uk.gov.hmrc.entrydeclarationstore.connectors

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, urlPathEqualTo}
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.http.Fault
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status.{NOT_FOUND, OK}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits, Injecting}
import play.api.{Application, Environment, Mode}
import uk.gov.hmrc.entrydeclarationstore.config.MockAppConfig
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.ExecutionContext.Implicits.global

class ApiSubscriptionFieldsConnectorSpec
    extends WordSpec
    with Matchers
    with FutureAwaits
    with DefaultAwaitTimeout
    with BeforeAndAfterAll
    with GuiceOneAppPerSuite
    with Injecting
    with MockAppConfig {

  override lazy val app: Application = new GuiceApplicationBuilder()
    .in(Environment.simple(mode = Mode.Dev))
    .configure("metrics.enabled" -> "false", "auditing.enabled" -> "false")
    .build()

  val httpClient: HttpClient = app.injector.instanceOf[HttpClient]

  private val wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort())

  var port: Int = _

  override def beforeAll(): Unit = {
    wireMockServer.start()
    port = wireMockServer.port()
  }

  override def afterAll(): Unit =
    wireMockServer.stop()

  val clientId: String = "abc123"
  val eori: String     = "GB123"

  val successJson: JsValue = Json.parse(s"""
                                           |{
                                           |    "clientId": "$clientId",
                                           |    "apiContext": "customs/imports/declarations",
                                           |    "apiVersion": "1.0",
                                           |    "fieldsId": "987de8f6-c983-444d-8a76-766fd24ddc85",
                                           |    "fields": {
                                           |        "authenticatedEori": "$eori"
                                           |    }
                                           |}""".stripMargin)

  val badJson: JsValue = Json.parse(s"""{
                                       |    "clientId": "$clientId",
                                       |    "apiContext": "customs/imports/declarations",
                                       |    "apiVersion": "1.0",
                                       |    "fieldsId": "987de8f6-c983-444d-8a76-766fd24ddc85",
                                       |    "fields": {}
                                       |}""".stripMargin)

  class Test {
    MockAppConfig.apiSubscriptionFieldsHost.returns(s"http://localhost:$port")
    MockAppConfig.apiGatewayContext.returns("customs/imports/declarations")
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()
    val connector                             = new ApiSubscriptionFieldsConnector(httpClient, mockAppConfig)

    def stubRequest(url: String, responseStatus: Int): StubMapping =
      wireMockServer.stubFor(get(urlPathEqualTo(url)).willReturn(aResponse().withStatus(responseStatus)))

    def stubConnectionFault(url: String): StubMapping =
      wireMockServer.stubFor(get(urlPathEqualTo(url)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)))
  }

  "ApiSubscriptionFieldsConnector.getAuthenticatedEoriField" when {

    val url = s"/field/application/$clientId/context/customs%2Fimports%2Fdeclarations/version/1.0"

    "subscription responds 200 Ok" when {
      "response contains AuthenticatedEori field" must {
        "return Some(eori)" in new Test {
          wireMockServer.stubFor(
            get(urlPathEqualTo(url))
              .willReturn(aResponse()
                .withBody(successJson.toString)
                .withStatus(OK)))

          await(connector.getAuthenticatedEoriField(clientId)) shouldBe Some(eori)
        }
      }
      "response does not contain AuthenticatedEori field" must {
        "return None" in new Test {
          wireMockServer.stubFor(
            get(urlPathEqualTo(url))
              .willReturn(aResponse()
                .withBody(badJson.toString)
                .withStatus(OK)))

          await(connector.getAuthenticatedEoriField(clientId)) shouldBe None
        }
      }
    }

    "subscription responds 404" must {
      "return None" in new Test {
        stubRequest(url, NOT_FOUND)

        await(connector.getAuthenticatedEoriField(clientId)) shouldBe None
      }
    }
  }
}
