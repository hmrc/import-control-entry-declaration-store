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

package uk.gov.hmrc.entrydeclarationstore.nrs

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.http.Fault
import com.github.tomakehurst.wiremock.stubbing.Scenario._
import org.apache.pekko.actor.{ActorSystem, Scheduler}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.MimeTypes
import play.api.http.Status._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits, Injecting}
import play.api.{Application, Environment, Mode}
import uk.gov.hmrc.entrydeclarationstore.config.MockAppConfig
import uk.gov.hmrc.entrydeclarationstore.housekeeping.HousekeepingScheduler
import uk.gov.hmrc.entrydeclarationstore.logging.LoggingContext
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class NRSConnectorSpec
    extends AnyWordSpec
    with Matchers
    with FutureAwaits
    with DefaultAwaitTimeout
    with BeforeAndAfterAll
    with GuiceOneAppPerSuite
    with Injecting
    with MockAppConfig
    with NRSMetadataTestData {

  override lazy val app: Application = new GuiceApplicationBuilder()
    .in(Environment.simple(mode = Mode.Dev))
    .disable[HousekeepingScheduler]
    .configure("metrics.enabled" -> "false", "auditing.enabled" -> "false")
    .build()

  val httpClient: HttpClientV2 = app.injector.instanceOf[HttpClientV2]

  private val wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort())

  var port: Int = _

  val actorSystem: ActorSystem              = inject[ActorSystem]
  implicit val scheduler: Scheduler         = actorSystem.scheduler
  implicit val headerCarrier: HeaderCarrier = HeaderCarrier()
  implicit val lc: LoggingContext           = LoggingContext("eori", "corrId", "subId")

  // Long delays to force a test to timeout if it does retry when we're not expecting it...
  val longDelays = List(10.minutes)

  val successResponseJson: JsValue =
    Json.parse("""{
                 |   "nrSubmissionId": "submissionId"
                 |}""".stripMargin)

  val nrsSubmission: NRSSubmission = NRSSubmission(nrsMetadataRawPayload, nrsMetadata)

  override def beforeAll(): Unit = {
    wireMockServer.start()
    port = wireMockServer.port()
  }

  override def afterAll(): Unit =
    wireMockServer.stop()

  val url         = "/submission"
  val apiKeyValue = "api-key"

  class Test(retryDelays: List[FiniteDuration] = List(100.millis)) {
    MockAppConfig.nrsBaseUrl.returns(s"http://localhost:$port")
    MockAppConfig.nrsRetries returns retryDelays
    MockAppConfig.nrsApiKey returns apiKeyValue

    val nrsSubmissionJsonString: String =
      s"""{
         | "payload": "cGF5bG9hZA==",
         | "metadata": ${nrsMetadataJson.toString()}
         |}""".stripMargin

    val connector = new NRSConnectorImpl(httpClient, mockAppConfig)
  }

  "NRSConnector" when {
    "immediately successful" must {
      "return the response" in new Test(longDelays) {
        wireMockServer.stubFor(
          post(urlPathEqualTo(url))
            .withHeader("Content-Type", equalTo(MimeTypes.JSON))
            .withHeader("X-API-Key", equalTo(apiKeyValue))
            .withRequestBody(equalToJson(nrsSubmissionJsonString, true, false))
            .willReturn(aResponse()
              .withBody(successResponseJson.toString)
              .withStatus(ACCEPTED)))

        await(connector.submit(nrsSubmission)) shouldBe Right(NRSResponse("submissionId"))
      }
    }

    "fails with 5xx status" must {
      "retry" in new Test {
        wireMockServer.stubFor(
          post(urlPathEqualTo(url))
            .withHeader("Content-Type", equalTo(MimeTypes.JSON))
            .withHeader("X-API-Key", equalTo(apiKeyValue))
            .inScenario("Retry")
            .whenScenarioStateIs(STARTED)
            .willReturn(aResponse()
              .withStatus(GATEWAY_TIMEOUT))
            .willSetStateTo("SUCCESS"))

        wireMockServer.stubFor(
          post(urlPathEqualTo(url))
            .withHeader("Content-Type", equalTo(MimeTypes.JSON))
            .withHeader("X-API-Key", equalTo(apiKeyValue))
            .inScenario("Retry")
            .whenScenarioStateIs("SUCCESS")
            .willReturn(aResponse()
              .withBody(successResponseJson.toString)
              .withStatus(ACCEPTED)))

        await(connector.submit(nrsSubmission)) shouldBe Right(NRSResponse("submissionId"))
      }

      "give up after all retries" in new Test {
        wireMockServer.stubFor(
          post(urlPathEqualTo(url))
            .withHeader("Content-Type", equalTo(MimeTypes.JSON))
            .withHeader("X-API-Key", equalTo(apiKeyValue))
            .willReturn(aResponse()
              .withStatus(GATEWAY_TIMEOUT)))

        await(connector.submit(nrsSubmission)) shouldBe Left(NRSSubmisionFailure.ErrorResponse(GATEWAY_TIMEOUT))
      }
    }

    "fails with 4xx status" must {
      "give up" in new Test(longDelays) {
        wireMockServer.stubFor(
          post(urlPathEqualTo(url))
            .withHeader("Content-Type", equalTo(MimeTypes.JSON))
            .withHeader("X-API-Key", equalTo(apiKeyValue))
            .willReturn(aResponse()
              .withStatus(BAD_REQUEST)))

        await(connector.submit(nrsSubmission)) shouldBe Left(NRSSubmisionFailure.ErrorResponse(BAD_REQUEST))
      }
    }

    "fails with exception" must {
      "give up" in new Test(longDelays) {

        wireMockServer.stubFor(
          post(urlPathEqualTo(url))
            .withHeader("Content-Type", equalTo(MimeTypes.JSON))
            .withHeader("X-API-Key", equalTo(apiKeyValue))
            .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)))

        await(connector.submit(nrsSubmission)) shouldBe Left(NRSSubmisionFailure.ExceptionThrown)
      }
    }

    "fails because unparsable JSON returned" must {
      "give up" in new Test(longDelays) {
        wireMockServer.stubFor(
          post(urlPathEqualTo(url))
            .withHeader("Content-Type", equalTo(MimeTypes.JSON))
            .withHeader("X-API-Key", equalTo(apiKeyValue))
            .willReturn(aResponse()
              .withBody("""{
                          |   "badKey": "badValue"
                          |}""".stripMargin)
              .withStatus(ACCEPTED)))

        await(connector.submit(nrsSubmission)) shouldBe Left(NRSSubmisionFailure.ExceptionThrown)
      }
    }
  }
}
