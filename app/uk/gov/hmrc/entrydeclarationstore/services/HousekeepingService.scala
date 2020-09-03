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

package uk.gov.hmrc.entrydeclarationstore.services

import java.time.Clock

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.entrydeclarationstore.config.AppConfig
import uk.gov.hmrc.entrydeclarationstore.models.HousekeepingStatus
import uk.gov.hmrc.entrydeclarationstore.repositories.EntryDeclarationRepo

import scala.concurrent.Future

@Singleton
class HousekeepingService @Inject()(repo: EntryDeclarationRepo, clock: Clock, appConfig: AppConfig) {
  def enableHousekeeping(value: Boolean): Future[Boolean] = repo.enableHousekeeping(value)
  def getHousekeepingStatus: Future[HousekeepingStatus]   = repo.getHousekeepingStatus

  def setShortTtl(submissionId: String): Future[Boolean] =
    repo.setHousekeepingAt(submissionId, clock.instant().plusMillis(appConfig.shortTtl.toMillis))

  def setShortTtl(eori: String, correlationId: String): Future[Boolean] =
    repo.setHousekeepingAt(eori, correlationId, clock.instant().plusMillis(appConfig.shortTtl.toMillis))
}
