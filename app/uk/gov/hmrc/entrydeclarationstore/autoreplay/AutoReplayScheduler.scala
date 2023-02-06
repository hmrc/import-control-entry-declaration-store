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

package uk.gov.hmrc.entrydeclarationstore.autoreplay

import akka.actor.Scheduler
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration
import play.api.Logging
import uk.gov.hmrc.mongo.lock._
import uk.gov.hmrc.entrydeclarationstore.config.AppConfig
import uk.gov.hmrc.entrydeclarationstore.repositories.LockRepositoryProvider
import javax.inject.{Inject, Singleton}
import scala.concurrent.{Future, ExecutionContext}
import scala.util.Failure

@Singleton
class AutoReplayScheduler @Inject()(
  scheduler: Scheduler,
  autoReplayer: AutoReplayer,
  lockProvider: LockRepositoryProvider,
  appConfig: AppConfig
)(implicit ec: ExecutionContext) extends Logging {

  private val exclusiveTimePeriodLock: TimePeriodLockService =
    TimePeriodLockService(lockProvider.lockRepository,
                          lockId = "auto_replay_lock",
                          ttl = Duration(appConfig.autoReplayLockDuration.toMillis, TimeUnit.MILLISECONDS))

  scheduler.scheduleWithFixedDelay(appConfig.autoReplayRunInterval, appConfig.autoReplayRunInterval)(() => autoReplay())

  private def autoReplay(replayCount: Int = 1): Future[Unit] = {
    logger.warn(s"Running AutoReplay sequence with replayCount = $replayCount")
    exclusiveTimePeriodLock
      .withRenewedLock(autoReplayer.replay())
      .flatMap{
        case Some(true) if replayCount < appConfig.maxConsecutiveAutoReplays => autoReplay(replayCount + 1)
        case _ => Future.successful(logger.warn(s"AutoReplay sequence terminating on replayCount = $replayCount"))
      }
      .andThen {
        case Failure(e) => logger.error("Failed auto-replay scheduling", e)
      }
  }
}
