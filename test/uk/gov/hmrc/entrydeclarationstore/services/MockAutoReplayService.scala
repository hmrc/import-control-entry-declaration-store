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

package uk.gov.hmrc.entrydeclarationstore.services

import org.scalamock.handlers.CallHandler
import uk.gov.hmrc.entrydeclarationstore.models.AutoReplayStatus
import uk.gov.hmrc.entrydeclarationstore.utils.TestHarness

import scala.concurrent.{ExecutionContext, Future}

trait MockAutoReplayService extends TestHarness {
  val mockAutoReplayService: AutoReplayService = mock[AutoReplayService]

  object MockAutoReplayService {
    def start(): CallHandler[Future[Unit]] =
      (() => mockAutoReplayService.start()).expects()

    def stop(): CallHandler[Future[Unit]] =
      (() => mockAutoReplayService.stop()).expects ()

    def getStatus(): CallHandler[Future[AutoReplayStatus]] =
      (mockAutoReplayService.getStatus()(_: ExecutionContext)) expects (*)

    def replay(replaySequenceCount: Int): CallHandler[Future[Boolean]] =
      (mockAutoReplayService.replay(_: Int)(_: ExecutionContext)).expects(replaySequenceCount, *)

  }
}
