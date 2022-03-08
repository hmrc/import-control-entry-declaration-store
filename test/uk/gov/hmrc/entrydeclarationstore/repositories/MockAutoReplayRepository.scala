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

package uk.gov.hmrc.entrydeclarationstore.repositories

import org.scalamock.handlers.CallHandler
import org.scalamock.scalatest.MockFactory
import uk.gov.hmrc.entrydeclarationstore.models.AutoReplayRepoStatus
import java.time.Instant
import scala.concurrent.Future

trait MockAutoReplayRepository extends MockFactory {
  val mockAutoReplayRepository: AutoReplayRepository = mock[AutoReplayRepository]

  object MockAutoReplayRepository {
    def start(): CallHandler[Future[Unit]] =
      mockAutoReplayRepository.start _ expects ()

    def stop(): CallHandler[Future[Unit]] =
      mockAutoReplayRepository.stop _ expects ()

    def getStatus(): CallHandler[Future[Option[AutoReplayRepoStatus]]] =
      mockAutoReplayRepository.getStatus _ expects ()

    def setLastReplay(replayId: Option[String], when: Instant): CallHandler[Future[Option[AutoReplayRepoStatus]]] =
      (mockAutoReplayRepository.setLastReplay(_: Option[String], _: Instant)) expects (replayId, *)
  }
}