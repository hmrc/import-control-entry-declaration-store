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

import uk.gov.hmrc.entrydeclarationstore.models.AutoReplayStatus
import uk.gov.hmrc.entrydeclarationstore.repositories.AutoReplayRepository
import uk.gov.hmrc.entrydeclarationstore.autoreplay.AutoReplayer
import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton
class AutoReplayService @Inject()(repository: AutoReplayRepository) extends AutoReplayer {

  def start(): Future[Unit] = repository.startAutoReplay()
  def stop(): Future[Unit] = repository.stopAutoReplay()
  def getStatus(): Future[AutoReplayStatus] = repository.getAutoReplayStatus()

  def replay(): Future[Boolean] = {
    Future.successful(false)
  }
}
