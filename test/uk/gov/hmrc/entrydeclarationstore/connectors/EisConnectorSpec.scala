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

import java.io.IOException

import akka.actor.ActorSystem
import akka.stream.actor.ActorPublisherMessage.Request
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.client.{CountMatchingStrategy, WireMock}
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.HeaderNames._
import play.api.http.MimeTypes
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.{DefaultAwaitTimeout, FutureAwaits, Injecting}
import play.api.{Application, Environment, Mode}
import play.mvc.Http.Status._
import uk.gov.hmrc.entrydeclarationstore.circuitbreaker.{CircuitBreaker, CircuitBreakerActor, CircuitBreakerConfig}
import uk.gov.hmrc.entrydeclarationstore.config.MockAppConfig
import uk.gov.hmrc.entrydeclarationstore.connectors.helpers.MockHeaderGenerator
import uk.gov.hmrc.entrydeclarationstore.logging.LoggingContext
import uk.gov.hmrc.entrydeclarationstore.models.{CircuitBreakerState, EntryDeclarationMetadata, MessageType}
import uk.gov.hmrc.entrydeclarationstore.repositories.MockCircuitBreakerRepo
import uk.gov.hmrc.entrydeclarationstore.utils.MockPagerDutyLogger
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class EisConnectorSpec
    extends WordSpec
    with Matchers
    with FutureAwaits
    with DefaultAwaitTimeout
    with BeforeAndAfterAll
    with GuiceOneAppPerSuite
    with Injecting
    with MockCircuitBreakerRepo
    with MockAppConfig
    with MockPagerDutyLogger
    with MockHeaderGenerator
    with Eventually {

  override lazy val app: Application = new GuiceApplicationBuilder()
    .in(Environment.simple(mode = Mode.Dev))
    .configure(
      "metrics.enabled"                                                                                    -> "false",
      "microservice.services.import-control-entry-declaration-eis.circuitBreaker.openStateRefreshPeriod"   -> "1 minute",
      "microservice.services.import-control-entry-declaration-eis.circuitBreaker.closedStateRefreshPeriod" -> "1 minute"
    )
    .build()

  val httpClient: HttpClient            = inject[HttpClient]
  implicit val actorSystem: ActorSystem = inject[ActorSystem]

  // So that we can check that only those headers from the HeaderGenerator are included
  private val extraHeader = "extraHeader"
  private val otherHeader = "otherHeader"
  implicit val hc: HeaderCarrier =
    HeaderCarrier(extraHeaders = Seq(extraHeader -> "someValue"), otherHeaders = Seq(otherHeader -> "someOtherValue"))
  implicit val ec: ExecutionContext = ExecutionContext.global
  implicit val request: Request     = Request(0L)
  implicit val lc: LoggingContext   = LoggingContext("eori", "corrId", "subId")

  private val wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort())

  val maxCallFailures                    = 5
  val defaultCallTimeout: FiniteDuration = 1.second

  var port: Int = _

  override def beforeAll(): Unit = {
    wireMockServer.start()
    port = wireMockServer.port()
    WireMock.configureFor("localhost", port)
  }

  override def afterAll(): Unit =
    wireMockServer.stop()

  class Test(callTimeout: FiniteDuration = defaultCallTimeout) {
    val newUrl   = "/safetyandsecurity/newenssubmission/v1"
    val amendUrl = "/safetyandsecurity/amendsubmission/v1"

    MockAppConfig.eisHost returns s"http://localhost:$port" anyNumberOfTimes ()
    MockAppConfig.eisNewEnsUrlPath returns newUrl anyNumberOfTimes ()
    MockAppConfig.eisAmendEnsUrlPath returns amendUrl anyNumberOfTimes ()

    private val circuitBreakerConfig: CircuitBreakerConfig =
      CircuitBreakerConfig(maxCallFailures, callTimeout, 1.minute, 1.minute)

    MockCircuitBreakerRepo.getCircuitBreakerState returns Future.successful(CircuitBreakerState.Closed) anyNumberOfTimes ()

    val circuitBreaker: CircuitBreaker =
      new CircuitBreaker(
        new CircuitBreakerActor.FactoryImpl(mockCircuitBreakerRepo, circuitBreakerConfig),
        circuitBreakerConfig)

    val connector =
      new EisConnectorImpl(httpClient, circuitBreaker, mockAppConfig, mockPagerDutyLogger, mockHeaderGenerator)
    val submissionId        = "743aa85b-5077-438f-8f30-01ab2a39d945"
    val mrn: Option[String] = Some("123456789012345678")

    val declarationMetadata: EntryDeclarationMetadata = EntryDeclarationMetadata(
      submissionId,
      MessageType.IE315,
      "3",
      java.time.Instant.now(),
      None
    )

    val amendmentMetadata: EntryDeclarationMetadata = EntryDeclarationMetadata(
      submissionId,
      MessageType.IE313,
      "3",
      java.time.Instant.now(),
      mrn
    )

    val expectedHeader      = "expectedHeader"
    val expectedHeaderValue = "expectedHeaderValue"
    MockHeaderGenerator
      .headersForEIS(submissionId) returns Seq(expectedHeader -> expectedHeaderValue) anyNumberOfTimes ()

    def stubResponse(statusCode: Int): StubMapping =
      wireMockServer
        .stubFor(
          post(urlPathEqualTo(newUrl))
            .withRequestBody(equalToJson(Json.toJson(declarationMetadata).toString()))
            .willReturn(aResponse()
              .withHeader(CONTENT_TYPE, MimeTypes.JSON)
              .withStatus(statusCode)))

    def stubDelayedResponse(delay: FiniteDuration, statusCode: Int): StubMapping =
      wireMockServer.stubFor(
        post(urlPathEqualTo(newUrl))
          .willReturn(
            aResponse()
              .withFixedDelay(delay.toMillis.toInt)
              .withStatus(statusCode))
      )

    def checkCircuitBreakerOpens(expectedError: EISSendFailure): Int = {
      wireMockServer.resetRequests()

      val extraCalls = 10

      MockCircuitBreakerRepo.setCircuitBreakerState(CircuitBreakerState.Open) returns Future.successful(())

      (0 until maxCallFailures) foreach { _ =>
        await(connector.submitMetadata(declarationMetadata)) shouldBe Some(expectedError)
      }

      (0 until extraCalls) foreach { _ =>
        await(connector.submitMetadata(declarationMetadata)) shouldBe Some(EISSendFailure.CircuitBreakerOpen)
      }

      verifyRequestCount(maxCallFailures)

      maxCallFailures + extraCalls
    }

    def checkCircuitBreakerDoesNotOpen(expectedError: Option[EISSendFailure]): Int = {
      wireMockServer.resetRequests()

      val totalCalls = maxCallFailures + 10

      (0 until totalCalls) foreach { _ =>
        await(connector.submitMetadata(declarationMetadata)) shouldBe expectedError
      }

      verifyRequestCount(totalCalls)

      totalCalls
    }

    def verifyRequestCount(expected: Int): Unit =
      verify(new CountMatchingStrategy(CountMatchingStrategy.EQUAL_TO, expected), postRequestedFor(urlEqualTo(newUrl)))
  }

  "Calling .submitMetadata" when {
    "eis responds 202 (Accepted)" must {
      "return None" when {
        "executing a post for a declaration" in new Test {
          wireMockServer.stubFor(
            post(urlPathEqualTo(newUrl))
              .withHeader(expectedHeader, equalTo(expectedHeaderValue))
              .withRequestBody(equalToJson(Json.toJson(declarationMetadata).toString))
              .willReturn(aResponse()
                .withHeader(CONTENT_TYPE, MimeTypes.JSON)
                .withStatus(ACCEPTED)))

          await(connector.submitMetadata(declarationMetadata)) shouldBe None

          verify(
            postRequestedFor(urlPathEqualTo(newUrl))
              .withoutHeader(extraHeader)
              .withoutHeader(otherHeader))
        }

        "executing a put for an amendment" in new Test {
          wireMockServer.stubFor(
            put(urlPathEqualTo(amendUrl))
              .withHeader(expectedHeader, equalTo(expectedHeaderValue))
              .withRequestBody(equalToJson(Json.toJson(amendmentMetadata).toString))
              .willReturn(aResponse()
                .withHeader(CONTENT_TYPE, MimeTypes.JSON)
                .withStatus(ACCEPTED)))

          await(connector.submitMetadata(amendmentMetadata)) shouldBe None

          verify(
            putRequestedFor(urlPathEqualTo(amendUrl))
              .withoutHeader(extraHeader)
              .withoutHeader(otherHeader))
        }
      }
    }

    "eis responds with 400" must {
      "return the ErrorResponse failure" in new Test {
        stubResponse(BAD_REQUEST)

        await(connector.submitMetadata(declarationMetadata)) shouldBe Some(EISSendFailure.ErrorResponse(BAD_REQUEST))
      }
    }

    "eis responds with 500" must {
      "return the ErrorResponse failure" in new Test {
        stubResponse(INTERNAL_SERVER_ERROR)

        await(connector.submitMetadata(declarationMetadata)) shouldBe Some(
          EISSendFailure.ErrorResponse(INTERNAL_SERVER_ERROR))
      }
    }

    "circuit breaker" must {
      "open after multiple 5xx responses" in new Test {
        stubResponse(SERVICE_UNAVAILABLE)

        val totalCalls: Int = checkCircuitBreakerOpens(EISSendFailure.ErrorResponse(SERVICE_UNAVAILABLE))

        MockPagerDutyLogger.logEISFailure repeated maxCallFailures
        MockPagerDutyLogger.logEISCircuitBreakerOpen repeated (totalCalls - maxCallFailures)
      }

      "not open after 202 responses" in new Test {
        stubResponse(ACCEPTED)

        checkCircuitBreakerDoesNotOpen(None)

        MockPagerDutyLogger.logEISFailure never ()
        MockPagerDutyLogger.logEISCircuitBreakerOpen never ()
      }

      "not open after 200 responses" in new Test {
        stubResponse(OK)

        val totalCalls: Int = checkCircuitBreakerDoesNotOpen(Some(EISSendFailure.ErrorResponse(OK)))

        MockPagerDutyLogger.logEISFailure repeated totalCalls
        MockPagerDutyLogger.logEISCircuitBreakerOpen never ()
      }

      "not open after 400 responses" in new Test {
        stubResponse(BAD_REQUEST)

        val totalCalls: Int = checkCircuitBreakerDoesNotOpen(Some(EISSendFailure.ErrorResponse(BAD_REQUEST)))

        MockPagerDutyLogger.logEISFailure repeated totalCalls
        MockPagerDutyLogger.logEISCircuitBreakerOpen never ()
      }

      "open after multiple 4xx responses" in new Test {
        stubResponse(499)

        val totalCalls: Int = checkCircuitBreakerOpens(EISSendFailure.ErrorResponse(499))

        MockPagerDutyLogger.logEISFailure repeated maxCallFailures
        MockPagerDutyLogger.logEISCircuitBreakerOpen repeated (totalCalls - maxCallFailures)
      }

      "open after multiple failed futures" in new Test {
        val extraCalls = 10

        // Wire mock doesn't give much control over exceptions thrown so test via withCircuitBreaker...
        (0 until maxCallFailures) foreach { _ =>
          await(connector.withCircuitBreaker(Future.failed(new IOException()))) shouldBe Some(
            EISSendFailure.ExceptionThrown)
        }
        (0 until extraCalls) foreach { _ =>
          await(connector.withCircuitBreaker(Future.failed(new IOException()))) shouldBe Some(
            EISSendFailure.CircuitBreakerOpen)
        }

        MockPagerDutyLogger.logEISError repeated maxCallFailures
        MockPagerDutyLogger.logEISCircuitBreakerOpen repeated extraCalls
      }

      "open after multiple timeouts" in new Test(callTimeout = 1.millis) {
        // Use success response code to prove that it's the timeout that triggers the circuit breaker...
        stubDelayedResponse(1.second, ACCEPTED)

        val totalCalls: Int = checkCircuitBreakerOpens(EISSendFailure.Timeout)

        MockPagerDutyLogger.logEISTimeout repeated maxCallFailures
        MockPagerDutyLogger.logEISCircuitBreakerOpen repeated (totalCalls - maxCallFailures)
      }
    }
  }
}
