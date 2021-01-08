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

package uk.gov.hmrc.entrydeclarationstore.connectors

import akka.actor.{ActorSystem, Scheduler}
import akka.stream.actor.ActorPublisherMessage.Request
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.client.{CountMatchingStrategy, MappingBuilder, ResponseDefinitionBuilder, WireMock}
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.stubbing.{Scenario, StubMapping}
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
import uk.gov.hmrc.entrydeclarationstore.config.MockAppConfig
import uk.gov.hmrc.entrydeclarationstore.connectors.helpers.MockHeaderGenerator
import uk.gov.hmrc.entrydeclarationstore.housekeeping.HousekeepingScheduler
import uk.gov.hmrc.entrydeclarationstore.logging.LoggingContext
import uk.gov.hmrc.entrydeclarationstore.models.{EntryDeclarationMetadata, MessageType, TrafficSwitchState}
import uk.gov.hmrc.entrydeclarationstore.services.MockTrafficSwitchService
import uk.gov.hmrc.entrydeclarationstore.trafficswitch.{TrafficSwitch, TrafficSwitchActor, TrafficSwitchConfig}
import uk.gov.hmrc.entrydeclarationstore.utils.MockPagerDutyLogger
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient}

import java.io.IOException
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class EisConnectorSpec
    extends WordSpec
    with Matchers
    with FutureAwaits
    with DefaultAwaitTimeout
    with BeforeAndAfterAll
    with GuiceOneAppPerSuite
    with Injecting
    with MockTrafficSwitchService
    with MockAppConfig
    with MockPagerDutyLogger
    with MockHeaderGenerator
    with Eventually {

  override lazy val app: Application = new GuiceApplicationBuilder()
    .in(Environment.simple(mode = Mode.Dev))
    .disable[HousekeepingScheduler]
    .configure(
      "metrics.enabled"                                                                                       -> "false",
      "microservice.services.import-control-entry-declaration-eis.trafficSwitch.notFlowingStateRefreshPeriod" -> "1 minute",
      "microservice.services.import-control-entry-declaration-eis.trafficSwitch.flowingStateRefreshPeriod"    -> "1 minute"
    )
    .build()

  val httpClient: HttpClient            = inject[HttpClient]
  implicit val actorSystem: ActorSystem = inject[ActorSystem]
  implicit val scheduler: Scheduler     = actorSystem.scheduler

  // So that we can check that only those headers from the HeaderGenerator are included
  private val extraHeader = "extraHeader"
  private val otherHeader = "otherHeader"
  implicit val hc: HeaderCarrier =
    HeaderCarrier(extraHeaders = Seq(extraHeader -> "someValue"), otherHeaders = Seq(otherHeader -> "someOtherValue"))

  implicit val request: Request   = Request(0L)
  implicit val lc: LoggingContext = LoggingContext("eori", "corrId", "subId")

  private val wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort())

  val maxCallFailures                    = 5
  val defaultCallTimeout: FiniteDuration = 1.second
  val eisRetryStatusCodes: Set[Int]      = Set(BAD_GATEWAY, 499)

  var port: Int = _

  override def beforeAll(): Unit = {
    wireMockServer.start()
    port = wireMockServer.port()
    WireMock.configureFor("localhost", port)
  }

  override def afterAll(): Unit =
    wireMockServer.stop()

  class Test(callTimeout: FiniteDuration = defaultCallTimeout, retryDelays: List[FiniteDuration] = Nil) {
    val newUrl   = "/safetyandsecurity/newenssubmission/v1"
    val amendUrl = "/safetyandsecurity/amendsubmission/v1"

    MockAppConfig.eisHost returns s"http://localhost:$port" anyNumberOfTimes ()
    MockAppConfig.eisNewEnsUrlPath returns newUrl anyNumberOfTimes ()
    MockAppConfig.eisAmendEnsUrlPath returns amendUrl anyNumberOfTimes ()
    MockAppConfig.eisRetries returns retryDelays anyNumberOfTimes ()
    MockAppConfig.eisRetryStatusCodes returns eisRetryStatusCodes anyNumberOfTimes ()

    private val trafficSwitchConfig: TrafficSwitchConfig =
      TrafficSwitchConfig(maxCallFailures, callTimeout, 1.minute, 1.minute)

    MockTrafficSwitchService.getTrafficSwitchState returns Future.successful(TrafficSwitchState.Flowing) anyNumberOfTimes ()

    val trafficSwitch: TrafficSwitch =
      new TrafficSwitch(
        new TrafficSwitchActor.FactoryImpl(mockTrafficSwitchService, trafficSwitchConfig),
        trafficSwitchConfig)

    val connector =
      new EisConnectorImpl(httpClient, trafficSwitch, mockAppConfig, mockPagerDutyLogger, mockHeaderGenerator)
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

    def requestBuilder(amendment: Boolean): MappingBuilder =
      if (amendment) {
        put(urlPathEqualTo(amendUrl))
          .withRequestBody(equalToJson(Json.toJson(amendmentMetadata).toString()))
          .withHeader(expectedHeader, equalTo(expectedHeaderValue))
      } else {
        post(urlPathEqualTo(newUrl))
          .withRequestBody(equalToJson(Json.toJson(declarationMetadata).toString()))
          .withHeader(expectedHeader, equalTo(expectedHeaderValue))
      }

    def responseBuilder(statusCode: Int): ResponseDefinitionBuilder =
      aResponse()
        .withHeader(CONTENT_TYPE, MimeTypes.JSON)
        .withStatus(statusCode)

    def stubResponse(statusCode: Int): StubMapping =
      wireMockServer
        .stubFor(
          requestBuilder(amendment = false)
            .willReturn(responseBuilder(statusCode)))

    def stubResponseRetries(successiveStatusCodes: Seq[Int], amendment: Boolean = false): Unit = {
      def attempted(i: Int) = if (i == 0) Scenario.STARTED else s"Attempted $i"

      wireMockServer.resetScenarios()

      successiveStatusCodes.zipWithIndex.foreach {
        case (code, i) =>
          wireMockServer
            .stubFor(
              requestBuilder(amendment)
                .inScenario("Retry")
                .whenScenarioStateIs(attempted(i))
                .willReturn(responseBuilder(code))
                .willSetStateTo(attempted(i + 1)))
      }
    }

    def stubDelayedResponse(delay: FiniteDuration, statusCode: Int): StubMapping =
      wireMockServer.stubFor(
        post(urlPathEqualTo(newUrl))
          .willReturn(
            aResponse()
              .withFixedDelay(delay.toMillis.toInt)
              .withStatus(statusCode))
      )

    def checkTrafficFlowStops(expectedError: EISSendFailure): Int = {
      wireMockServer.resetRequests()

      val extraCalls = 10

      MockTrafficSwitchService.stopTrafficFlow returns Future.successful(())

      (0 until maxCallFailures) foreach { _ =>
        await(connector.submitMetadata(declarationMetadata, bypassTrafficSwitch = false)) shouldBe Some(expectedError)
      }

      (0 until extraCalls) foreach { _ =>
        await(connector.submitMetadata(declarationMetadata, bypassTrafficSwitch = false)) shouldBe Some(
          EISSendFailure.TrafficSwitchNotFlowing)
      }

      verifyRequestCount(maxCallFailures)

      maxCallFailures + extraCalls
    }

    def checkTrafficFlowDoesNotStop(
      expectedError: Option[EISSendFailure],
      bypassTrafficSwitch: Boolean = false): Int = {
      wireMockServer.resetRequests()

      val totalCalls = maxCallFailures + 10

      (0 until totalCalls) foreach { _ =>
        await(connector.submitMetadata(declarationMetadata, bypassTrafficSwitch)) shouldBe expectedError
      }

      verifyRequestCount(totalCalls)

      totalCalls
    }

    def verifyRequestCount(expected: Int, amendment: Boolean = false): Unit = {
      val patternBuilder = if (amendment) {
        putRequestedFor(urlEqualTo(amendUrl))
      } else {
        postRequestedFor(urlEqualTo(newUrl))
      }

      verify(new CountMatchingStrategy(CountMatchingStrategy.EQUAL_TO, expected), patternBuilder)
    }
  }

  "Calling .submitMetadata" when {

    def connectorSuccessBehaviour(bypassTrafficSwitch: Boolean): Unit =
      s"bypassing trafficSwitch is $bypassTrafficSwitch" when {
        "eis responds 202 (Accepted)" must {
          "return None" when {
            "executing a post for a declaration" in new Test {
              wireMockServer.stubFor(requestBuilder(amendment = false)
                .willReturn(responseBuilder(ACCEPTED)))

              await(connector.submitMetadata(declarationMetadata, bypassTrafficSwitch)) shouldBe None

              verify(
                postRequestedFor(urlPathEqualTo(newUrl))
                  .withoutHeader(extraHeader)
                  .withoutHeader(otherHeader))
            }

            "executing a put for an amendment" in new Test {
              wireMockServer.stubFor(requestBuilder(amendment = true)
                .willReturn(responseBuilder(ACCEPTED)))

              await(connector.submitMetadata(amendmentMetadata, bypassTrafficSwitch)) shouldBe None

              verify(
                putRequestedFor(urlPathEqualTo(amendUrl))
                  .withoutHeader(extraHeader)
                  .withoutHeader(otherHeader))
            }
          }
        }
      }

    def connectorErrorBehaviour(bypassTrafficSwitch: Boolean): Unit =
      s"bypassing trafficSwitch is $bypassTrafficSwitch" when {
        "eis responds with 400" must {
          "return the ErrorResponse failure" in new Test {
            stubResponse(BAD_REQUEST)

            await(connector.submitMetadata(declarationMetadata, bypassTrafficSwitch)) shouldBe Some(
              EISSendFailure.ErrorResponse(BAD_REQUEST))
          }
        }

        "eis responds with 500" must {
          "return the ErrorResponse failure" in new Test {
            stubResponse(INTERNAL_SERVER_ERROR)

            await(connector.submitMetadata(declarationMetadata, bypassTrafficSwitch)) shouldBe Some(
              EISSendFailure.ErrorResponse(INTERNAL_SERVER_ERROR))
          }
        }
      }

    behave like connectorSuccessBehaviour(bypassTrafficSwitch = false)
    behave like connectorSuccessBehaviour(bypassTrafficSwitch = true)

    behave like connectorErrorBehaviour(bypassTrafficSwitch = false)
    behave like connectorErrorBehaviour(bypassTrafficSwitch = true)

    "traffic switch is not bypassed" must {
      "stop traffic flow after multiple 5xx responses" in new Test {
        stubResponse(SERVICE_UNAVAILABLE)

        val totalCalls: Int = checkTrafficFlowStops(EISSendFailure.ErrorResponse(SERVICE_UNAVAILABLE))

        MockPagerDutyLogger.logEISFailure repeated maxCallFailures
        MockPagerDutyLogger.logEISTrafficSwitchFlowStopped repeated (totalCalls - maxCallFailures)
      }

      "not stop traffic flow after 202 responses" in new Test {
        stubResponse(ACCEPTED)

        checkTrafficFlowDoesNotStop(None)

        MockPagerDutyLogger.logEISFailure never ()
        MockPagerDutyLogger.logEISTrafficSwitchFlowStopped never ()
      }

      "not stop traffic flow after 200 responses" in new Test {
        stubResponse(OK)

        val totalCalls: Int = checkTrafficFlowDoesNotStop(Some(EISSendFailure.ErrorResponse(OK)))

        MockPagerDutyLogger.logEISFailure repeated totalCalls
        MockPagerDutyLogger.logEISTrafficSwitchFlowStopped never ()
      }

      "not stop traffic flow after 400 responses" in new Test {
        stubResponse(BAD_REQUEST)

        val totalCalls: Int = checkTrafficFlowDoesNotStop(Some(EISSendFailure.ErrorResponse(BAD_REQUEST)))

        MockPagerDutyLogger.logEISFailure repeated totalCalls
        MockPagerDutyLogger.logEISTrafficSwitchFlowStopped never ()
      }

      "stop traffic flow after multiple 4xx responses" in new Test {
        stubResponse(499)

        val totalCalls: Int = checkTrafficFlowStops(EISSendFailure.ErrorResponse(499))

        MockPagerDutyLogger.logEISFailure repeated maxCallFailures
        MockPagerDutyLogger.logEISTrafficSwitchFlowStopped repeated (totalCalls - maxCallFailures)
      }

      "stop traffic flow after multiple failed futures" in new Test {
        val extraCalls = 10

        // Wire mock doesn't give much control over exceptions thrown so test private method...
        (0 until maxCallFailures) foreach { _ =>
          await(connector.submit(bypassTrafficSwitch = false)(Future.failed(new IOException()))) shouldBe Some(
            EISSendFailure.ExceptionThrown)
        }
        (0 until extraCalls) foreach { _ =>
          await(connector.submit(bypassTrafficSwitch = false)(Future.failed(new IOException()))) shouldBe Some(
            EISSendFailure.TrafficSwitchNotFlowing)
        }

        MockPagerDutyLogger.logEISError repeated maxCallFailures
        MockPagerDutyLogger.logEISTrafficSwitchFlowStopped repeated extraCalls
      }

      "stop traffic flow after multiple timeouts" in new Test(callTimeout = 1.millis) {
        // Use success response code to prove that it's the timeout that triggers the circuit breaker...
        stubDelayedResponse(1.second, ACCEPTED)

        val totalCalls: Int = checkTrafficFlowStops(EISSendFailure.Timeout)

        MockPagerDutyLogger.logEISTimeout repeated maxCallFailures
        MockPagerDutyLogger.logEISTrafficSwitchFlowStopped repeated (totalCalls - maxCallFailures)
      }
    }

    "traffic switch is bypassed" must {
      "not stop traffic flow the traffic switch on failure" in new Test {
        stubResponse(SERVICE_UNAVAILABLE)

        val totalCalls: Int =
          checkTrafficFlowDoesNotStop(
            Some(EISSendFailure.ErrorResponse(SERVICE_UNAVAILABLE)),
            bypassTrafficSwitch = true)

        MockPagerDutyLogger.logEISFailure repeated totalCalls
        MockPagerDutyLogger.logEISTrafficSwitchFlowStopped never ()
      }

      "allow calls through even when the traffic switch is stopping traffic flow to EIS" in new Test {
        stubResponse(SERVICE_UNAVAILABLE)
        checkTrafficFlowStops(EISSendFailure.ErrorResponse(SERVICE_UNAVAILABLE))

        stubResponse(OK)
        checkTrafficFlowDoesNotStop(Some(EISSendFailure.ErrorResponse(OK)), bypassTrafficSwitch = true)
      }
    }

    "retrying is configured" must {
      val maxRetries = 5

      eisRetryStatusCodes.foreach(retryUntilSuccess)
      eisRetryStatusCodes.foreach(giveUpAfterMaxRetries)

      def retryDelays(numRetries: Int): List[FiniteDuration] = List.fill(numRetries)(10.millis)

      def retryUntilSuccess(failureStatusCode: Int): Unit =
        s"retry for status code $failureStatusCode" in new Test(retryDelays = retryDelays(maxRetries)) {
          val numRetries = 2

          wireMockServer.resetRequests()
          stubResponseRetries(List.fill(numRetries)(failureStatusCode) :+ ACCEPTED)

          await(connector.submitMetadata(declarationMetadata, bypassTrafficSwitch = false)) shouldBe None

          verifyRequestCount(numRetries + 1)
        }

      def giveUpAfterMaxRetries(failureStatusCode: Int): Unit =
        s"give up after all retries for status code $failureStatusCode" in new Test(
          retryDelays = retryDelays(maxRetries)) {
          wireMockServer.resetRequests()
          stubResponseRetries(List.fill(maxRetries + 1)(failureStatusCode))

          await(connector.submitMetadata(declarationMetadata, bypassTrafficSwitch = false)) shouldBe
            Some(EISSendFailure.ErrorResponse(failureStatusCode))

          verifyRequestCount(maxRetries + 1)
        }

      "retry for amendments" in new Test(retryDelays = retryDelays(maxRetries)) {
        // WLOG
        val failureStatusCode: Int = eisRetryStatusCodes.head
        val numRetries             = 2

        wireMockServer.resetRequests()
        stubResponseRetries(List.fill(numRetries)(failureStatusCode) :+ ACCEPTED, amendment = true)

        await(connector.submitMetadata(amendmentMetadata, bypassTrafficSwitch = false)) shouldBe None

        verifyRequestCount(numRetries + 1, amendment = true)
      }

      "not trip the traffic switch due to retries" in new Test(retryDelays = retryDelays(maxCallFailures)) {
        // WLOG
        val failureStatusCode: Int = eisRetryStatusCodes.head

        wireMockServer.resetRequests()
        stubResponseRetries(List.fill(maxCallFailures + 1)(failureStatusCode))
        await(connector.submitMetadata(declarationMetadata, bypassTrafficSwitch = false)) shouldBe
          Some(EISSendFailure.ErrorResponse(failureStatusCode))

        // Check still ok...
        stubResponse(ACCEPTED)
        await(connector.submitMetadata(declarationMetadata, bypassTrafficSwitch = false)) shouldBe None

        verifyRequestCount(maxCallFailures + 2)
      }

      "not retry other status codes" in new Test(retryDelays = retryDelays(maxRetries)) {
        val failureStatusCode: Int = INTERNAL_SERVER_ERROR
        eisRetryStatusCodes shouldNot contain(failureStatusCode)

        wireMockServer.resetRequests()

        stubResponseRetries(List.fill(1)(failureStatusCode))
        await(connector.submitMetadata(declarationMetadata, bypassTrafficSwitch = false)) shouldBe
          Some(EISSendFailure.ErrorResponse(failureStatusCode))

        verifyRequestCount(1)
      }
    }
  }
}
