/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.entrydeclarationstore.services

import play.api.Logging
import uk.gov.hmrc.entrydeclarationstore.models.{ReplayResult, AutoReplayStatus, AutoReplayRepoStatus, ReplayInitializationResult, ReplayState, LastReplay}
import uk.gov.hmrc.entrydeclarationstore.repositories.{EntryDeclarationRepo, AutoReplayRepository, ReplayStateRepo}
import uk.gov.hmrc.entrydeclarationstore.autoreplay.AutoReplayer
import uk.gov.hmrc.entrydeclarationstore.orchestrators.ReplayOrchestrator
import uk.gov.hmrc.http.HeaderCarrier
import play.api.http.HeaderNames._
import javax.inject.{Inject, Singleton}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import java.time.Clock
import ReplayResult._
import ReplayInitializationResult._

@Singleton
class AutoReplayService @Inject()(
  orchestrator: ReplayOrchestrator,
  repository: AutoReplayRepository,
  submissionsRepo: EntryDeclarationRepo,
  replayRepo: ReplayStateRepo,
  clock: Clock
) extends AutoReplayer with Logging {

  val DefaultOtherHeaders: Seq[(String, String)] = Seq((USER_AGENT, "import-cont rol-entry-declaration-store"))
  def start(): Future[Unit] = repository.startAutoReplay()
  def stop(): Future[Unit] = repository.stopAutoReplay()

  def getStatus()(implicit ec: ExecutionContext): Future[AutoReplayStatus] = repository.getAutoReplayStatus().flatMap{
    _.fold[Future[AutoReplayStatus]](Future.successful(AutoReplayStatus.Unavailable)){ status =>
        getLastReplayState(status.lastReplay).map{ replay =>
          status match {
            case AutoReplayRepoStatus(true, _) => AutoReplayStatus.On(replay)
            case AutoReplayRepoStatus(false, _) => AutoReplayStatus.Off(replay)
            case _ => AutoReplayStatus.Unavailable
          }
        }
    }
  }

  def replay()(implicit ec: ExecutionContext): Future[Boolean] = {
    implicit val defaultHeaderCarrier: HeaderCarrier = HeaderCarrier(otherHeaders = DefaultOtherHeaders)
    getStatusAndUndeliveredCount().flatMap{
      case (true, Some(count)) if count > 0 =>
        logger.info(s"Attempting to replay $count undelivered submissions ... ")

        val (initResult, replayResult) = orchestrator.startReplay(Some(count))
        replayResult.flatMap {
          case Completed(replayed) =>
            logger.info(s"Succesfully replayed $replayed undelivered submissions" )
            initResult.map{
              case Started(replayId) =>
                repository.setLastReplay(Some(replayId))
                true
              case AlreadyRunning(Some(replayId)) =>
                repository.setLastReplay(Some(replayId))
                true
              case AlreadyRunning(None) =>
                logger.error(s"Unable to recover replay Id of successful replay")
                repository.setLastReplay(None)
                true
            }
          case Aborted(t) =>
            logger.error(s"Replay aborted with error ${t.getMessage()}")
            Future.successful(false)
        }

      case _ => Future.successful(false)
    }
  }

  private def getStatusAndUndeliveredCount()(implicit ec: ExecutionContext): Future[(Boolean, Option[Int])] =
    repository.getAutoReplayStatus().flatMap{
      _.map{
        case AutoReplayRepoStatus(true, _) => submissionsRepo.totalUndeliveredMessages(clock.instant).map(count => (true, Some(count)))
        case AutoReplayRepoStatus(false, _) => Future.successful((false, None))
      }.getOrElse(Future.successful((false, None)))
    }

  private def getLastReplayState(lastReplay: Option[LastReplay]): Future[Option[ReplayState]] =
    lastReplay.fold[Future[Option[ReplayState]]](Future.successful(None)){lr =>
      lr.id.fold[Future[Option[ReplayState]]](Future.successful(None))(replayRepo.lookupState(_))
    }

}
