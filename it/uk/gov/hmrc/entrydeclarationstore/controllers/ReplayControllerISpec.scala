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

import java.time.Instant
import java.util.UUID

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.http.Fault
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.{Assertion, BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.http.Status._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{Json, _}
import play.api.libs.ws.WSClient
import play.api.test.Injecting
import play.api.{Application, Environment, Mode}
import uk.gov.hmrc.entrydeclarationstore.housekeeping.HousekeepingScheduler
import uk.gov.hmrc.entrydeclarationstore.logging.LoggingContext
import uk.gov.hmrc.entrydeclarationstore.models.{EisSubmissionState, EntryDeclarationModel, ReplayState}
import uk.gov.hmrc.entrydeclarationstore.repositories.EntryDeclarationRepoImpl
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class ReplayControllerISpec
    extends UnitSpec
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with GuiceOneServerPerSuite
    with Injecting
    with Eventually
    with IntegrationPatience {

  val mockPort: Int = 11111

  val batchSize = 10

  override lazy val app: Application = new GuiceApplicationBuilder()
    .in(Environment.simple(mode = Mode.Dev))
    .disable[HousekeepingScheduler]
    .configure(
      "replay.batchSize"                                                -> batchSize.toString,
      "microservice.services.import-control-entry-declaration-eis.port" -> mockPort.toString
    )
    .build()

  private val wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig.port(mockPort))

  val client: WSClient                               = app.injector.instanceOf[WSClient]
  val entryDeclarationRepo: EntryDeclarationRepoImpl = app.injector.instanceOf[EntryDeclarationRepoImpl]

  implicit val lc: LoggingContext = LoggingContext("eori", "corrId", "subId")

  override def beforeAll(): Unit =
    wireMockServer.start()

  override def afterAll(): Unit =
    wireMockServer.stop()

  override def beforeEach(): Unit = {
    await(entryDeclarationRepo.removeAll())
    wireMockServer.resetAll()
  }

  def replayRequestBody(limit: Option[Int]): JsValue =
    limit match {
      case Some(lim) => Json.obj("limit" -> lim)
      case None      => JsObject.empty
    }

  def newEntryDeclarationInError: EntryDeclarationModel = {
    val submissionId = UUID.randomUUID.toString

    val payload: JsValue = Json.parse(s"""{
                                         |  "submissionId": "$submissionId",
                                         |  "metadata": {
                                         |    "senderEORI": "myeori",
                                         |    "senderBranch": "mybranch",
                                         |    "preparationDateTime": "2003-02-11T01:23:00.000Z",
                                         |    "messageType": "IE315",
                                         |    "receivedDateTime": "1234-12-10T00:34:17.000Z",
                                         |    "correlationId": "correlationID1",
                                         |    "messageIdentification": "messageID"
                                         |  },
                                         |  "itinerary": {
                                         |    "modeOfTransportAtBorder": "2",
                                         |    "officeOfFirstEntry": {
                                         |      "reference": "AB3C4D5E",
                                         |      "expectedDateTimeOfArrival": "2003-02-11T12:34:00.000Z"
                                         |    }
                                         |  }
                                         |}""".stripMargin)

    EntryDeclarationModel(
      correlationId = UUID.randomUUID.toString,
      submissionId  = submissionId,
      "GB1234",
      payload,
      None,
      receivedDateTime   = Instant.now(),
      eisSubmissionState = EisSubmissionState.Error
    )
  }

  def createEntryDeclarationInError(): String = {
    val decl = newEntryDeclarationInError
    await(entryDeclarationRepo.save(decl)) shouldBe true
    decl.submissionId
  }

  def startReplay(limit: Option[Int] = None): String =
    (startReplayRawJson(limit) \ "replayId").as[String]

  def startReplayRawJson(limit: Option[Int] = None): JsValue = {
    val response = await(client.url(s"http://localhost:$port/import-control/replays").post(replayRequestBody(limit)))
    response.status shouldBe ACCEPTED
    response.json
  }

  def getReplayState(replayId: String): ReplayState = {
    val response = await(client.url(s"http://localhost:$port/import-control/replays/$replayId").get())
    response.status shouldBe OK
    response.json.as[ReplayState]
  }

  private def totalToReplay(replayId: String) =
    getReplayState(replayId).totalToReplay

  def willSubmitMetadataToEis(submissionId: String, statusCode: Int): StubMapping =
    wireMockServer
      .stubFor(
        post(urlPathEqualTo("/safetyandsecurity/newenssubmission/v1"))
          .withRequestBody(containing(submissionId))
          .willReturn(aResponse()
            .withStatus(statusCode)))

  def willSubmitMetadataToEisSlow(submissionId: String, statusCode: Int): StubMapping =
    wireMockServer
      .stubFor(
        post(urlPathEqualTo("/safetyandsecurity/newenssubmission/v1"))
          .withRequestBody(containing(submissionId))
          .willReturn(aResponse()
            .withFixedDelay(1000)
            .withStatus(statusCode)))

  def willSubmitMetadataToEisFault(submissionId: String): StubMapping =
    wireMockServer
      .stubFor(
        post(urlPathEqualTo("/safetyandsecurity/newenssubmission/v1"))
          .withRequestBody(containing(submissionId))
          .willReturn(aResponse()
            .withFault(Fault.CONNECTION_RESET_BY_PEER)))

  def replaySuccessfully(numDeclarations: Int, limit: Option[Int]): Assertion = {
    val numToReplay = numDeclarations min limit.getOrElse(Int.MaxValue)

    val submissionIds = (1 to numDeclarations).map(_ => createEntryDeclarationInError())

    await(entryDeclarationRepo.totalUndeliveredMessages(Instant.now)) shouldBe numDeclarations

    submissionIds.foreach(id => willSubmitMetadataToEis(id, ACCEPTED))

    val replayId = startReplay(limit)

    totalToReplay(replayId) shouldBe numToReplay

    eventually {
      await(entryDeclarationRepo.totalUndeliveredMessages(Instant.now)) shouldBe numDeclarations - numToReplay

      val replayState = getReplayState(replayId)

      replayState.completed     shouldBe true
      replayState.failureCount  shouldBe 0
      replayState.totalToReplay shouldBe numToReplay
      replayState.successCount  shouldBe numToReplay
    }
  }

  "ReplayController" when {
    "submissions to replay" when {
      "they are replayable" must {
        "be able to replay fewer than a full batch" in {
          replaySuccessfully(numDeclarations = batchSize / 2, limit = None)
        }

        "be able to replay multiple batches" in {
          replaySuccessfully(numDeclarations = batchSize * 2, limit = None)
        }

        "be able to limit the number replayed" in {
          replaySuccessfully(numDeclarations = batchSize, limit = Some(batchSize / 2))
        }
      }

      "some submissions cannot be replayed" must {
        "replay those that can" in {
          val numDeclarations = 6
          val failEvery       = 2
          val numFailures     = numDeclarations / failEvery
          val submissionIds   = (1 to numDeclarations).map(_ => createEntryDeclarationInError())

          await(entryDeclarationRepo.totalUndeliveredMessages(Instant.now)) shouldBe numDeclarations

          submissionIds.zipWithIndex.foreach {
            case (id, i) =>
              val status = if (i % failEvery == 0) BAD_REQUEST else ACCEPTED

              willSubmitMetadataToEis(id, status)
          }

          val replayId = startReplay()
          totalToReplay(replayId) shouldBe numDeclarations

          eventually {
            await(entryDeclarationRepo.totalUndeliveredMessages(Instant.now)) shouldBe numFailures

            val replayState = getReplayState(replayId)

            replayState.completed     shouldBe true
            replayState.failureCount  shouldBe numFailures
            replayState.totalToReplay shouldBe numDeclarations
            replayState.successCount  shouldBe numDeclarations - numFailures
          }
        }
      }

      "some submissions cannot be replayed because EIS responds with 5xx" must {
        "abort the replay" in {
          val submissionId1 = createEntryDeclarationInError()
          val submissionId2 = createEntryDeclarationInError()
          val submissionId3 = createEntryDeclarationInError()

          await(entryDeclarationRepo.totalUndeliveredMessages(Instant.now)) shouldBe 3

          // Fail and abort on the second
          willSubmitMetadataToEis(submissionId1, ACCEPTED)
          willSubmitMetadataToEis(submissionId2, INTERNAL_SERVER_ERROR)
          willSubmitMetadataToEis(submissionId3, ACCEPTED)

          val replayId = startReplay()
          totalToReplay(replayId) shouldBe 3

          eventually {
            val replayState = getReplayState(replayId)
            replayState.completed shouldBe true

            // Should have replayed one successfully
            // (but note that we don't have information about the number of
            // successes/failures because the batch as a whole failed)
            await(entryDeclarationRepo.totalUndeliveredMessages(Instant.now)) shouldBe 2
          }
        }
      }

      "some submissions cannot be replayed because EIS disconnects before responding" must {
        "abort the replay" in {
          val submissionId1 = createEntryDeclarationInError()
          val submissionId2 = createEntryDeclarationInError()
          val submissionId3 = createEntryDeclarationInError()

          await(entryDeclarationRepo.totalUndeliveredMessages(Instant.now)) shouldBe 3

          // Fail and abort on the second
          willSubmitMetadataToEis(submissionId1, ACCEPTED)
          willSubmitMetadataToEisFault(submissionId2)
          willSubmitMetadataToEis(submissionId3, ACCEPTED)

          val replayId = startReplay()
          totalToReplay(replayId) shouldBe 3

          eventually {
            val replayState = getReplayState(replayId)
            replayState.completed shouldBe true

            // Should have replayed one successfully
            // (but note that we don't have information about the number of
            // successes/failures because the batch as a whole failed)
            await(entryDeclarationRepo.totalUndeliveredMessages(Instant.now)) shouldBe 2
          }
        }
      }

      "a replay is already in progress" must {
        "return ACCEPT with the latest replayId" in {
          val submissionId1 = createEntryDeclarationInError()

          await(entryDeclarationRepo.totalUndeliveredMessages(Instant.now)) shouldBe 1

          willSubmitMetadataToEisSlow(submissionId1, ACCEPTED)

          val replayId = startReplay()

          val response2 = startReplayRawJson()
          (response2 \ "replayId").as[String]        shouldBe replayId
          (response2 \ "alreadyStarted").as[Boolean] shouldBe true

          eventually {
            val replayState = getReplayState(replayId)
            replayState.completed shouldBe true
          }
        }
      }
    }
  }
}
