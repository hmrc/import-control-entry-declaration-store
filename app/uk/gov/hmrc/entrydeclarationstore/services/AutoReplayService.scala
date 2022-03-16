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
import uk.gov.hmrc.entrydeclarationstore.trafficswitch.TrafficSwitchConfig
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
  trafficeSwitchConfig: TrafficSwitchConfig,
  orchestrator: ReplayOrchestrator,
  repository: AutoReplayRepository,
  submissionsRepo: EntryDeclarationRepo,
  replayService: ReplayStateRetrievalService,
  trafficSwitchService: TrafficSwitchService,
  clock: Clock
) extends AutoReplayer with Logging {

  val DefaultOtherHeaders: Seq[(String, String)] = Seq((USER_AGENT, "import-control-entry-declaration-store"))
  def start(): Future[Unit] = repository.start()
  def stop(): Future[Unit] = repository.stop()

  def getStatus()(implicit ec: ExecutionContext): Future[AutoReplayStatus] = repository.getStatus().flatMap{
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

    def replaySubmissions(undeliveredCount: Int): Future[(Boolean, Int, Int)] =
      if (undeliveredCount <= 0) Future.successful((false, 0, 0))
      else {
        logger.warn(s"Attempting to replay $undeliveredCount undelivered submissions ... ")
        val (initResult, replayResult) = orchestrator.startReplay(Some(undeliveredCount), ReplayTrigger.Automatic)
        replayResult.flatMap {
          case Completed(replayed, successful, failures) =>
            logger.warn(s"Succesfully replayed $successful undelivered submissions with $failures failures" )
            if (failures > 0) logger.warn(s"Failed to auto-replay $failures submissions")
            initResult.flatMap{
              case Started(replayId) => repository.setLastReplay(Some(replayId)).map(_ => (true, successful, failures))
              case running: AlreadyRunning => repository.setLastReplay(running.replayId).map(_ => (true, successful, failures))
            }
          case Aborted(t) =>
            logger.error(s"Replay aborted with error ${t.getMessage()}")
            Future.successful((false, 0, 0))
        }
      }

    def resetTrafficSwitchAndReplay(undeliveredCount: Int): Future[Boolean] =
      trafficSwitchService.startTrafficFlow.flatMap{_ =>
        val testSubmissionCount: Int = Math.min(trafficeSwitchConfig.maxFailures, undeliveredCount)
        replaySubmissions(testSubmissionCount).flatMap{
          case (true, successful, failures) if successful >= failures =>
            logger.warn(s"Initial replay after Traffic Switch reset, succeeded $successful, failures $failures")
            replaySubmissions(undeliveredCount - successful).map(_._1)
          case (_, successful, failures) if successful == 0 =>
            logger.error(s"Post TF-reset Submission failure, initial replay after Traffic Switch reset failed, succeeded $successful, failures $failures")
            Future.successful(false)
          case (result, successful, failures) =>
            logger.warn(s"Initial replay after Traffic Switch reset, succeeded $successful, failures $failures")
            Future.successful(result)
        }
      }

    logger.info(s"Checking for undelivered submissions ...")
    getServiceStatusIfEnabled().flatMap{
      _.fold(Future.successful(false)){
        case (TrafficSwitchState.NotFlowing, undeliveredCount) => resetTrafficSwitchAndReplay(undeliveredCount)
        case (TrafficSwitchState.Flowing, undeliveredCount) => replaySubmissions(undeliveredCount).map(_._1)
      }
    }
  }

  private def getServiceStatusIfEnabled()(implicit ec: ExecutionContext): Future[Option[(TrafficSwitchState, Int)]] =
    repository.getStatus().flatMap{
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
