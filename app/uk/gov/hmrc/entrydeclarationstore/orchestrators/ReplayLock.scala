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

package uk.gov.hmrc.entrydeclarationstore.orchestrators

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration
import uk.gov.hmrc.entrydeclarationstore.config.AppConfig
import uk.gov.hmrc.mongo.lock._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

trait ReplayLock {
  def lock(replayId: String): Future[Option[Lock]]
  def renew(replayId: String): Future[Unit]
  def unlock(replayId: String): Future[Unit]
}

@Singleton
class ReplayLockImpl @Inject()(repo: MongoLockRepository, appConfig: AppConfig)(
  implicit ec: ExecutionContext)
    extends ReplayLock {
  private val forceReleaseAfter: Duration = Duration(appConfig.replayLockDuration.toMillis, TimeUnit.MILLISECONDS)

  private val lockId: String = "replay_lock"

  def lock(replayId: String): Future[Option[Lock]] = repo.takeLock(lockId, replayId, forceReleaseAfter)

  def renew(replayId: String): Future[Unit] =
    repo.refreshExpiry(lockId, replayId, forceReleaseAfter).map(_ => ())

  def unlock(replayId: String): Future[Unit] = repo.releaseLock(lockId, replayId)
}
