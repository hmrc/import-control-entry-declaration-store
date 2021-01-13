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

import org.joda.time.{Duration => JodaDuration}
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.entrydeclarationstore.config.AppConfig
import uk.gov.hmrc.entrydeclarationstore.repositories.LockRepositoryProvider

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

trait ReplayLock {
  def lock(replayId: String): Future[Boolean]

  def renew(replayId: String): Future[Unit]

  def unlock(replayId: String): Future[Unit]
}

@Singleton
class ReplayLockImpl @Inject()(lockRepositoryProvider: LockRepositoryProvider, appConfig: AppConfig)(
  implicit reactiveMongoComponent: ReactiveMongoComponent,
  ec: ExecutionContext)
    extends ReplayLock {
  private def forceReleaseAfter: JodaDuration = JodaDuration.millis(appConfig.replayLockDuration.toMillis)

  private val lockId: String = "replay_lock"

  private def repo = lockRepositoryProvider.lockRepository

  def lock(replayId: String): Future[Boolean] =
    repo.lock(reqLockId = lockId, reqOwner = replayId, forceReleaseAfter)

  def renew(replayId: String): Future[Unit] =
    repo.renew(reqLockId = lockId, reqOwner = replayId, forceReleaseAfter).map(_ => ())

  def unlock(replayId: String): Future[Unit] =
    repo.releaseLock(reqLockId = lockId, reqOwner = replayId)
}
