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

package uk.gov.hmrc.entrydeclarationstore.repositories

import org.scalamock.handlers.CallHandler
import org.scalamock.scalatest.MockFactory
import org.scalatest.TestSuite
import uk.gov.hmrc.entrydeclarationstore.models.{ReplayState, ReplayTrigger}

import java.time.Instant
import scala.concurrent.Future

trait MockReplayStateRepo extends TestSuite with MockFactory {
  val mockReplayStateRepo: ReplayStateRepo = mock[ReplayStateRepo]

  object MockReplayStateRepo {
    def list(count: Option[Int]): CallHandler[Future[List[ReplayState]]] =
      (mockReplayStateRepo.list _).expects(count)

    def mostRecentByTrigger(trigger: ReplayTrigger): CallHandler[Future[Option[ReplayState]]] =
      (mockReplayStateRepo.mostRecentByTrigger _).expects(trigger)

    def lookupState(replayId: String): CallHandler[Future[Option[ReplayState]]] =
      (mockReplayStateRepo.lookupState _).expects(replayId)

    def lookupIdOfLatest: CallHandler[Future[Option[String]]] =
      (() => mockReplayStateRepo.lookupIdOfLatest).expects()

    def insert(replayId: String, trigger: ReplayTrigger, totalToReplay: Int, startTime: Instant): CallHandler[Future[Unit]] =
      (mockReplayStateRepo.insert(_: String, _: ReplayTrigger, _: Int, _: Instant)).expects(replayId, trigger, totalToReplay, startTime)

    def incrementCounts(replayId: String, successesToAdd: Int, failuresToAdd: Int): CallHandler[Future[Boolean]] =
      (mockReplayStateRepo.incrementCounts(_: String, _: Int, _: Int)).expects(replayId, successesToAdd, failuresToAdd)

    def setCompleted(replayId: String, completed: Boolean, endTime: Instant): CallHandler[Future[Boolean]] =
      (mockReplayStateRepo.setCompleted(_: String, _: Boolean, _: Instant)).expects(replayId, completed, endTime)
  }

}
