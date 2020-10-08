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

package uk.gov.hmrc.entrydeclarationstore.housekeeping

import akka.actor.Scheduler
import javax.inject.{Inject, Singleton}
import org.joda.time.{Duration => JodaDuration}
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.entrydeclarationstore.config.AppConfig
import uk.gov.hmrc.lock.{ExclusiveTimePeriodLock, LockRepository}

import scala.concurrent.ExecutionContext

@Singleton
class HousekeepingScheduler @Inject()(
  scheduler: Scheduler,
  housekeeper: Housekeeper,
  appConfig: AppConfig
)(implicit reactiveMongoComponent: ReactiveMongoComponent, ec: ExecutionContext) {

  private val lockRepository = new LockRepository()(reactiveMongoComponent.mongoConnector.db)

  private val exclusiveTimePeriodLock: ExclusiveTimePeriodLock = new ExclusiveTimePeriodLock {
    override def repo: LockRepository = lockRepository

    override def lockId: String = "housekeeping_lock"

    override val holdLockFor: JodaDuration = JodaDuration.millis(appConfig.housekeepingLockDuration.toMillis)
  }

  scheduler.schedule(appConfig.housekeepingRunInterval, appConfig.housekeepingRunInterval) {
    exclusiveTimePeriodLock
      .tryToAcquireOrRenewLock {
        housekeeper.housekeep()
      }
  }
}
