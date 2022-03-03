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
import uk.gov.hmrc.entrydeclarationstore.models.{ReplayResult, ReplayTrigger, AutoReplayStatus, AutoReplayRepoStatus}
import uk.gov.hmrc.entrydeclarationstore.models.{ReplayInitializationResult, ReplayState, LastReplay, TrafficSwitchState}
import uk.gov.hmrc.entrydeclarationstore.repositories.{EntryDeclarationRepo, AutoReplayRepository}
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
  replayService: ReplayStateRetrievalService,
  trafficSwitchService: TrafficSwitchService,
  clock: Clock
) extends AutoReplayer with Logging {

  val DefaultOtherHeaders: Seq[(String, String)] = Seq((USER_AGENT, "import-control-entry-declaration-store"))
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
    def replayUndeliveredSubmisstions(undeliveredCount: Int): Future[Boolean] =
      if (undeliveredCount == 0) Future.successful(false)
      else {
        implicit val defaultHeaderCarrier: HeaderCarrier = HeaderCarrier(otherHeaders = DefaultOtherHeaders)
        logger.info(s"Attempting to replay $undeliveredCount undelivered submissions ... ")
        val (initResult, replayResult) = orchestrator.startReplay(Some(undeliveredCount), ReplayTrigger.Automatic)
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
      }

    getServiceStatusIfEnabled().flatMap{
      _.fold(Future.successful(false)){
        case (TrafficSwitchState.NotFlowing, undeliveredCount) =>
          logger.warn(s"Resetting TrafficSwitch ....")
          // Reset traffic switch if found to have been triggered
          trafficSwitchService.startTrafficFlow.flatMap{_ =>
            replayUndeliveredSubmisstions(undeliveredCount)
          }

        case (TrafficSwitchState.Flowing, undeliveredCount) =>
          replayUndeliveredSubmisstions(undeliveredCount)
      }
    }
  }

  private def getServiceStatusIfEnabled()(implicit ec: ExecutionContext): Future[Option[(TrafficSwitchState, Int)]] =
    repository.getAutoReplayStatus().flatMap{
      case Some(AutoReplayRepoStatus(true, _)) =>
        trafficSwitchService.getTrafficSwitchState.flatMap{ trafficeSwitchState =>
          submissionsRepo.totalUndeliveredMessages(clock.instant).map(count => Some((trafficeSwitchState, count)))
        }

      case _ => Future.successful(None)
    }

  private def getLastReplayState(lastReplay: Option[LastReplay]): Future[Option[ReplayState]] =
    lastReplay.fold[Future[Option[ReplayState]]](Future.successful(None)){lr =>
      lr.id.fold[Future[Option[ReplayState]]](Future.successful(None))(replayService.retrieveReplayState(_))
    }

}
