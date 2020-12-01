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

package uk.gov.hmrc.entrydeclarationstore.orchestrators

import java.time.{Clock, Instant, ZoneOffset}

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Injecting
import play.api.{Application, Environment, Mode}
import reactivemongo.core.errors.ConnectionException
import uk.gov.hmrc.entrydeclarationstore.config.MockAppConfig
import uk.gov.hmrc.entrydeclarationstore.models.{BatchReplayError, BatchReplayResult, ReplayResult}
import uk.gov.hmrc.entrydeclarationstore.repositories.{MockEntryDeclarationRepo, MockReplayStateRepo}
import uk.gov.hmrc.entrydeclarationstore.services.MockSubmissionReplayService
import uk.gov.hmrc.entrydeclarationstore.utils.MockIdGenerator
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import scala.util.control.NoStackTrace

class ReplayOrchestratorSpec
    extends UnitSpec
    with MockIdGenerator
    with MockEntryDeclarationRepo
    with MockReplayStateRepo
    with MockSubmissionReplayService
    with MockAppConfig
    with ScalaFutures
    with GuiceOneAppPerSuite
    with Injecting {

  implicit lazy val materializer: Materializer = inject[Materializer]
  implicit val hc: HeaderCarrier               = HeaderCarrier()

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .in(Environment.simple(mode = Mode.Dev))
    .configure("metrics.enabled" -> "false")
    .build()

  val time: Instant = Instant.now
  val replayId      = "someReplayId"
  val clock: Clock  = Clock.fixed(time, ZoneOffset.UTC)

  val replayOrchestrator = new ReplayOrchestrator(
    mockIdGenerator,
    mockEntryDeclarationRepo,
    mockReplayStateRepo,
    mockSubmissionReplayService,
    mockAppConfig,
    clock)

  private def willSetCompleted: Future[Unit] = {
    val completePromise = Promise[Unit]
    MockReplayStateRepo.setCompleted(replayId, time).onCall { _ =>
      completePromise.trySuccess(())
      Future.successful(true)
    }
    completePromise.future
  }

  private def willGetUndeliverablesAndInitState(replayLimit: Option[Int], submissionIds: String*) = {
    MockIdGenerator.generateUuid() returns replayId

    // totalToReplay should always be the same as the number of ids in the source
    // (as this should take into account the replayLimit - regardless of the
    // number of undeliverables in the database)
    MockReplayStateRepo.insert(replayId, totalToReplay = submissionIds.length, time) returns Future.unit

    MockEntryDeclarationRepo
      .getUndeliveredSubmissionIds(time, replayLimit)
      .returns(Source(submissionIds.toList))
  }

  private def willReplayBatchAndUpdateState(submissionIds: String*) = {

    // WLOG - these are the reposibility of the store to set - we
    // just increment with what we're told...
    val succeessIncrement = 123
    val failureIncrement  = 321

    MockSubmissionReplayService
      .replaySubmissions(submissionIds)
      .returns(Right(BatchReplayResult(successCount = succeessIncrement, failureCount = failureIncrement)))
    MockReplayStateRepo
      .incrementCounts(replayId, successesToAdd = succeessIncrement, failuresToAdd = failureIncrement)
      .returns(true)
  }

  private val databaseException = new ConnectionException("failure") with NoStackTrace

  "ReplayOrchestrator" when {
    "a single undelivered submission requires replay" when {
      "successful" must {
        "return the replay id and perform the replay" in {
          val submissionId = "submissionId"
          MockAppConfig.replayBatchSize returns 1

          MockEntryDeclarationRepo.totalUndeliveredMessages(time) returns 1
          willGetUndeliverablesAndInitState(None, submissionId)

          willReplayBatchAndUpdateState(submissionId)

          val completeFuture = willSetCompleted

          val (fReplayId, result) = replayOrchestrator.startReplay(None)
          fReplayId.futureValue shouldBe replayId
          result.futureValue    shouldBe ReplayResult.Completed(numBatches = 1)

          await(completeFuture)
        }
      }
    }

    "replayLimit number to replay but there are more undelivered submissions than this" must {
      "set the total to replay as the replayLimit" in {
        val submissionId     = "submissionId"
        val replayLimit      = Some(1)
        val totalUndelivered = 100
        MockAppConfig.replayBatchSize returns 1

        MockEntryDeclarationRepo.totalUndeliveredMessages(time) returns totalUndelivered
        willGetUndeliverablesAndInitState(replayLimit, submissionId)

        willReplayBatchAndUpdateState(submissionId)

        val completeFuture = willSetCompleted

        val (fReplayId, result) = replayOrchestrator.startReplay(replayLimit)
        fReplayId.futureValue shouldBe replayId
        result.futureValue    shouldBe ReplayResult.Completed(numBatches = 1)

        await(completeFuture)
      }
    }

    "replayLimit number to replay but there are fewer undelivered submissions than this" must {
      "set the total to replay as the number available" in {
        val submissionId     = "submissionId"
        val replayLimit      = Some(10)
        val totalUndelivered = 1
        MockAppConfig.replayBatchSize returns 1

        MockEntryDeclarationRepo.totalUndeliveredMessages(time) returns totalUndelivered
        willGetUndeliverablesAndInitState(replayLimit, submissionId)

        willReplayBatchAndUpdateState(submissionId)

        val completeFuture = willSetCompleted

        val (fReplayId, result) = replayOrchestrator.startReplay(replayLimit)
        fReplayId.futureValue shouldBe replayId
        result.futureValue    shouldBe ReplayResult.Completed(numBatches = 1)

        await(completeFuture)
      }
    }

    "fails to determine the number of submissions to replay" must {
      "return None" in {
        MockEntryDeclarationRepo.totalUndeliveredMessages(time) returns Future.failed(databaseException)

        replayOrchestrator.startReplay(None)._1.failed.futureValue shouldBe databaseException
      }
    }

    "fails to insert a replay state with a replay id" must {
      "fail" in {
        MockIdGenerator.generateUuid() returns replayId

        MockEntryDeclarationRepo.totalUndeliveredMessages(time) returns 1
        MockReplayStateRepo.insert(replayId, 1, time) throws databaseException

        replayOrchestrator.startReplay(None)._1.failed.futureValue shouldBe databaseException
      }
    }

    "replay starts but the incoming stream of submissionIds terminates with an error" must {
      "return the replay id and abort the replay and (attempt to) update the state" in {
        val totalUndelivered = 1
        MockAppConfig.replayBatchSize returns 1

        MockIdGenerator.generateUuid() returns replayId

        MockEntryDeclarationRepo.totalUndeliveredMessages(time) returns totalUndelivered

        MockReplayStateRepo.insert(replayId, totalUndelivered, time) returns Future.unit
        MockEntryDeclarationRepo.getUndeliveredSubmissionIds(time, None) returns Source.failed(databaseException)

        val completeFuture = willSetCompleted

        val (fReplayId, result) = replayOrchestrator.startReplay(None)
        fReplayId.futureValue shouldBe replayId
        result.futureValue    shouldBe ReplayResult.Aborted

        await(completeFuture)
      }
    }

    "multiple submissions require replay" when {
      "the batch size is one" must {
        "send replay each submission in a separate batch" in {
          val submissionIds = (1 to 3).map(i => s"subId$i")
          MockAppConfig.replayBatchSize returns 1

          MockEntryDeclarationRepo.totalUndeliveredMessages(time) returns submissionIds.length
          willGetUndeliverablesAndInitState(None, submissionIds: _*)

          for (submissionId <- submissionIds) willReplayBatchAndUpdateState(submissionId)

          val completeFuture = willSetCompleted

          val (fReplayId, result) = replayOrchestrator.startReplay(None)
          fReplayId.futureValue shouldBe replayId
          result.futureValue    shouldBe ReplayResult.Completed(numBatches = 3)

          await(completeFuture)
        }
      }

      "the batch size is greater than one" must {
        "replay in batches of that size" in {
          val submissionIds = (1 to 6).map(i => s"subId$i")
          MockAppConfig.replayBatchSize returns 2

          MockEntryDeclarationRepo.totalUndeliveredMessages(time) returns submissionIds.length
          willGetUndeliverablesAndInitState(None, submissionIds: _*)

          willReplayBatchAndUpdateState(submissionIds.slice(0, 2): _*)
          willReplayBatchAndUpdateState(submissionIds.slice(2, 4): _*)
          willReplayBatchAndUpdateState(submissionIds.slice(4, 6): _*)

          val completeFuture = willSetCompleted

          val (fReplayId, result) = replayOrchestrator.startReplay(None)
          fReplayId.futureValue shouldBe replayId
          result.futureValue    shouldBe ReplayResult.Completed(numBatches = 3)

          await(completeFuture)
        }
      }

      "there are incomplete batches" must {
        "send the incomplete batch at the end" in {
          val submissionIds = (1 to 5).map(i => s"subId$i")
          MockAppConfig.replayBatchSize returns 3

          MockEntryDeclarationRepo.totalUndeliveredMessages(time) returns submissionIds.length
          willGetUndeliverablesAndInitState(None, submissionIds: _*)

          willReplayBatchAndUpdateState(submissionIds.slice(0, 3): _*)
          willReplayBatchAndUpdateState(submissionIds.slice(3, 5): _*)

          val completeFuture = willSetCompleted

          val (fReplayId, result) = replayOrchestrator.startReplay(None)
          fReplayId.futureValue shouldBe replayId
          result.futureValue    shouldBe ReplayResult.Completed(numBatches = 2)

          await(completeFuture)
        }
      }

      "replay starts but updating progress fails" must {
        "return the replay id and abort the replay and (attempt to) update the state" in {
          val submissionIds = (1 to 10).map(i => s"subId$i")
          MockAppConfig.replayBatchSize returns 2

          MockEntryDeclarationRepo.totalUndeliveredMessages(time) returns submissionIds.length
          willGetUndeliverablesAndInitState(None, submissionIds: _*)

          willReplayBatchAndUpdateState(submissionIds.slice(0, 2): _*)

          // Fails with next batch
          MockSubmissionReplayService.replaySubmissions(submissionIds.slice(2, 4)) returns Right(
            BatchReplayResult(successCount = 123, failureCount = 321))

          MockReplayStateRepo.incrementCounts(replayId, successesToAdd = 123, failuresToAdd = 321) returns false

          val completeFuture = willSetCompleted

          val (fReplayId, result) = replayOrchestrator.startReplay(None)
          fReplayId.futureValue shouldBe replayId
          result.futureValue    shouldBe ReplayResult.Aborted

          await(completeFuture)
        }
      }

      Seq(BatchReplayError.MetadataRetrievalError, BatchReplayError.EISSubmitError, BatchReplayError.EISEventError)
        .foreach(testStoreReplayBatchFailure)

      def testStoreReplayBatchFailure(error: BatchReplayError): Unit =
        s"replay starts but the replaying a batch fails with $error" must {
          "return the replay id and abort the replay and update the state" in {
            val submissionIds = (1 to 10).map(i => s"subId$i")
            MockAppConfig.replayBatchSize returns 2

            MockEntryDeclarationRepo.totalUndeliveredMessages(time) returns submissionIds.length
            willGetUndeliverablesAndInitState(None, submissionIds: _*)

            willReplayBatchAndUpdateState(submissionIds.slice(0, 2): _*)

            // Fails with next batch
            MockSubmissionReplayService.replaySubmissions(submissionIds.slice(2, 4)) returns Left(error)

            val completeFuture = willSetCompleted

            val (fReplayId, result) = replayOrchestrator.startReplay(None)
            fReplayId.futureValue shouldBe replayId
            result.futureValue    shouldBe ReplayResult.Aborted

            await(completeFuture)
          }
        }

      "replay takes a while" must {
        "provide a replay id without waiting for replay to complete" in {
          val submissionId = "submissionId"
          MockAppConfig.replayBatchSize returns 1

          MockEntryDeclarationRepo.totalUndeliveredMessages(time) returns 1
          willGetUndeliverablesAndInitState(None, submissionId)

          val replayBatchCalled = Promise[Unit]
          MockSubmissionReplayService
            .replaySubmissions(Seq(submissionId))
            .onCall { _ =>
              replayBatchCalled.success(())
              Promise[Either[BatchReplayError, BatchReplayResult]].future
            }

          val (fReplayId, _) = replayOrchestrator.startReplay(None)
          fReplayId.futureValue shouldBe replayId

          await(replayBatchCalled.future)
        }
      }
    }
  }
}