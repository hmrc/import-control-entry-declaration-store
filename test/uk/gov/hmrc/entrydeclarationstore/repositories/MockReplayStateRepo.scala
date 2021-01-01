/*
 * Copyright 2021 HM Revenue & Customs
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
import uk.gov.hmrc.entrydeclarationstore.models.ReplayState

import java.time.Instant
import scala.concurrent.Future

trait MockReplayStateRepo extends MockFactory {
  val mockReplayStateRepo: ReplayStateRepo = mock[ReplayStateRepo]

  object MockReplayStateRepo {
    def lookupState(replayId: String): CallHandler[Future[Option[ReplayState]]] =
      (mockReplayStateRepo.lookupState _).expects(replayId)

    def setState(replayId: String, replayState: ReplayState): CallHandler[Future[Unit]] =
      (mockReplayStateRepo.setState(_: String, _: ReplayState)).expects(replayId, replayState)

    def insert(replayId: String, totalToReplay: Int, startTime: Instant): CallHandler[Future[Unit]] =
      (mockReplayStateRepo.insert(_: String, _: Int, _: Instant)).expects(replayId, totalToReplay, startTime)

    def incrementCounts(replayId: String, successesToAdd: Int, failuresToAdd: Int): CallHandler[Future[Boolean]] =
      (mockReplayStateRepo.incrementCounts(_: String, _: Int, _: Int)).expects(replayId, successesToAdd, failuresToAdd)

    def setCompleted(replayId: String, endTime: Instant): CallHandler[Future[Boolean]] =
      (mockReplayStateRepo.setCompleted(_: String, _: Instant)).expects(replayId, endTime)
  }

}
