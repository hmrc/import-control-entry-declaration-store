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

package uk.gov.hmrc.entrydeclarationstore.orchestrators

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import play.api.Logger
import uk.gov.hmrc.entrydeclarationstore.config.AppConfig
import uk.gov.hmrc.entrydeclarationstore.models.{ReplayInitializationResult, ReplayResult}
import uk.gov.hmrc.entrydeclarationstore.repositories.{EntryDeclarationRepo, ReplayStateRepo}
import uk.gov.hmrc.entrydeclarationstore.services.SubmissionReplayService
import uk.gov.hmrc.entrydeclarationstore.utils.IdGenerator
import uk.gov.hmrc.http.HeaderCarrier

import java.time.{Clock, Instant}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class ReplayOrchestrator @Inject()(
  idGenerator: IdGenerator,
  submissionStateRepo: EntryDeclarationRepo,
  replayStateRepo: ReplayStateRepo,
  submissionReplayService: SubmissionReplayService,
  replayLock: ReplayLock,
  appConfig: AppConfig,
  clock: Clock)(implicit ec: ExecutionContext, mat: Materializer) {

  /**
    * Starts a replay
    * @param limit an optional limit on the number of submissions to replay
    * @return a tuple consisting of the initialization result and (mainly for testing) the overall final result of the replay
    */
  def startReplay(limit: Option[Int])(
    implicit hc: HeaderCarrier): (Future[ReplayInitializationResult], Future[ReplayResult]) = {

    val replayStartTime = clock.instant

    val initResult = initIfNotRunning(limit, replayStartTime)

    val futureReplayResult = initResult.flatMap {
      case ReplayInitializationResult.Started(replayId) =>
        startReplay(limit, replayStartTime, replayId)

      case ReplayInitializationResult.AlreadyRunning(_) =>
        // It's a coding error if this is used...
        Future.failed(new RuntimeException("Replay already running"))
    }

    (initResult, futureReplayResult)
  }

  private def initIfNotRunning(limit: Option[Int], replayStartTime: Instant): Future[ReplayInitializationResult] = {
    val replayId = idGenerator.generateUuid

    replayLock
      .lock(replayId)
      .flatMap { lockAcquired =>
        if (lockAcquired) {
          for {
            totalUndelivered <- submissionStateRepo.totalUndeliveredMessages(receivedNoLaterThan = replayStartTime)
            numToReplay = limit.map(lim => lim min totalUndelivered).getOrElse(totalUndelivered)
            _ <- insertState(replayId, numToReplay, replayStartTime)
          } yield ReplayInitializationResult.Started(replayId)
        } else {
          replayStateRepo.lookupIdOfLatest.map(ReplayInitializationResult.AlreadyRunning)
        }
      }
  }

  private def insertState(replayId: String, totalToReplay: Int, startTime: Instant): Future[Unit] =
    replayStateRepo
      .insert(replayId: String, totalToReplay: Int, startTime: Instant)

  private def startReplay(limit: Option[Int], replayStartTime: Instant, replayId: String)(
    implicit hc: HeaderCarrier): Future[ReplayResult] = {
    Logger.info(s"Starting replay for replayId $replayId")

    submissionStateRepo
      .getUndeliveredSubmissionIds(replayStartTime, limit)
      .grouped(appConfig.replayBatchSize)
      .mapAsync(parallelism = 1) { submissionIds =>
        for {
          batchResult <- submissionReplayService.replaySubmissions(submissionIds)
          _           <- replayLock.renew(replayId)
        } yield batchResult
      }
      .mapAsync(parallelism = 1) {
        case Right(counts) =>
          replayStateRepo.incrementCounts(replayId, counts.successCount, counts.failureCount)
        case Left(error) =>
          throw new RuntimeException(s"Unable to replay batch $error")
      }
      .fold(0) { (batchCount, _) =>
        Logger.debug(s"Completed replay of batch $batchCount")
        batchCount + 1
      }
      .map { numBatches =>
        Logger.info(s"Completed replay of $numBatches batches")
        setCompleteAndUnlock(replayId)
        ReplayResult.Completed(numBatches)
      }
      .recover {
        case NonFatal(e) =>
          Logger.error("Replay aborted", e)
          setCompleteAndUnlock(replayId)
          ReplayResult.Aborted(e)
      }
      .runWith(Sink.last)
  }

  private def setCompleteAndUnlock(replayId: String): Unit = {
    replayStateRepo
      .setCompleted(replayId, clock.instant)
      .failed
      .foreach(e => Logger.warn(s"Unable to set replay $replayId as complete", e))

    replayLock
      .unlock(replayId)
      .failed
      .foreach(e => Logger.warn(s"Unable to unlock replay $replayId", e))
  }
}
