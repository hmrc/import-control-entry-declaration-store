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

import java.time.Instant

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.entrydeclarationstore.models.{ReplayTrigger, ReplayState}
import uk.gov.hmrc.entrydeclarationstore.repositories.MockReplayStateRepo

import scala.concurrent.Future

class ReplayStateRetrievalServiceSpec extends AnyWordSpec with MockReplayStateRepo with ScalaFutures {

  val service  = new ReplayStateRetrievalService(mockReplayStateRepo)
  val replayId = "replayId"

  "SubmissionStateRetrievalService" when {
    "submission exists for a submissionId" must {
      "return it" in {
        val state = ReplayState(replayId, Instant.now, 2, ReplayTrigger.Automatic, None, None, 0, 1)

        MockReplayStateRepo.lookupState(replayId) returns Future.successful(Some(state))

        service.retrieveReplayState(replayId).futureValue shouldBe Some(state)
      }
    }

    "no submission exists for a submissionId" must {
      "return None" in {
        MockReplayStateRepo.lookupState(replayId) returns Future.successful(None)

        service.retrieveReplayState(replayId).futureValue shouldBe None
      }
    }
  }
}
