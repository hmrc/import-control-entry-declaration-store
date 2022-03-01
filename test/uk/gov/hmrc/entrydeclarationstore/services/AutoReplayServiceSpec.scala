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

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.entrydeclarationstore.config.MockAppConfig
import uk.gov.hmrc.entrydeclarationstore.repositories.MockAutoReplayRepository
import uk.gov.hmrc.entrydeclarationstore.orchestrators.MockReplayOrchestrator
import scala.concurrent.Future
import java.time._
import uk.gov.hmrc.entrydeclarationstore.repositories.{MockReplayStateRepo, MockEntryDeclarationRepo}
import uk.gov.hmrc.entrydeclarationstore.models.{AutoReplayStatus, AutoReplayRepoStatus, ReplayInitializationResult, ReplayResult, LastReplay}
import scala.concurrent.ExecutionContext.Implicits.global

class AutoReplayServiceSpec
    extends AnyWordSpec
    with MockAppConfig
    with MockAutoReplayRepository
    with MockEntryDeclarationRepo
    with MockReplayStateRepo
    with MockReplayOrchestrator
    with ScalaFutures {

  val now: Instant = Instant.now
  val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

  val service = new AutoReplayService(mockReplayOrchestrator, mockAutoReplayRepository, mockEntryDeclarationRepo, mockReplayStateRepo, clock)

  "AutoReplayService" when {
    "getting autoReplay status" must {
      "get using the repo" in {
        val status = Some(AutoReplayRepoStatus(true, None))

        MockAutoReplayRepository.getAutoReplayStatus returns Future.successful(status)
        service.getStatus.futureValue shouldBe AutoReplayStatus.On(None)
      }
    }

    "Start autoReplay" must {
      "set using the repo" in {

        MockAutoReplayRepository.startAutoReplay() returns Future.unit
        service.start().futureValue
      }
    }


    "Stop autoReplay" must {
      "set using the repo" in {

        MockAutoReplayRepository.stopAutoReplay() returns Future.unit
        service.stop().futureValue
      }
    }

    "replay" must {
      "replay undelivered submissions if enabled and there are undelivered submissions" in {
        val result = (Future.successful(ReplayInitializationResult.Started("1")), Future.successful(ReplayResult.Completed(5)))
        MockAutoReplayRepository.getAutoReplayStatus() returns Future.successful(Some(AutoReplayRepoStatus(true, None)))
        MockEntryDeclarationRepo.totalUndeliveredMessages(now) returns Future.successful(5)
        MockReplayOrchestrator.startReplay(Some(5)) returns result
        MockAutoReplayRepository.setLastReplay(Some("1"), now) returns
          Future.successful(Some(AutoReplayRepoStatus(true, Some(LastReplay(Some("1"), now)))))
        service.replay().futureValue shouldBe true
      }

      "Not replay if enabled but there are no undelivered submissions" in {
        MockAutoReplayRepository.getAutoReplayStatus() returns Future.successful(Some(AutoReplayRepoStatus(true, None)))
        MockEntryDeclarationRepo.totalUndeliveredMessages(now) returns Future.successful(0)
        service.replay().futureValue shouldBe false
      }

      "Not replay undelivered submissions if not enabled" in {

        MockAutoReplayRepository.getAutoReplayStatus() returns Future.successful(Some(AutoReplayRepoStatus(false, None)))
        service.replay().futureValue shouldBe false
      }

    }

  }
}
