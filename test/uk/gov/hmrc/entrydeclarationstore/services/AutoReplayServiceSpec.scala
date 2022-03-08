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
import uk.gov.hmrc.entrydeclarationstore.orchestrators.MockReplayOrchestrator
import uk.gov.hmrc.entrydeclarationstore.repositories.{MockAutoReplayRepository, MockEntryDeclarationRepo}
import uk.gov.hmrc.entrydeclarationstore.models.{AutoReplayStatus, AutoReplayRepoStatus, ReplayInitializationResult}
import uk.gov.hmrc.entrydeclarationstore.models.{ReplayResult, LastReplay, ReplayTrigger, TrafficSwitchState}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import java.time._

class AutoReplayServiceSpec
    extends AnyWordSpec
    with MockAppConfig
    with MockAutoReplayRepository
    with MockEntryDeclarationRepo
    with MockReplayStateRetrievalService
    with MockReplayOrchestrator
    with MockTrafficSwitchService
    with ScalaFutures {

  val now: Instant = Instant.now
  val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

  val service = new AutoReplayService(
    mockAppConfig,
    mockReplayOrchestrator,
    mockAutoReplayRepository,
    mockEntryDeclarationRepo,
    mockReplayStateRetrievalService,
    mockTrafficSwitchService,
    clock)

  "AutoReplayService" when {
    "getting autoReplay status" must {
      "get using the repo" in {
        val status = Some(AutoReplayRepoStatus(true, None))

        MockAutoReplayRepository.getStatus returns Future.successful(status)
        service.getStatus.futureValue shouldBe AutoReplayStatus.On(None)
      }
    }

    "Start autoReplay" must {
      "set using the repo" in {

        MockAutoReplayRepository.start() returns Future.unit
        service.start().futureValue
      }
    }


    "Stop autoReplay" must {
      "set using the repo" in {

        MockAutoReplayRepository.stop() returns Future.unit
        service.stop().futureValue
      }
    }

    "replay" must {
      "replay undelivered submissions if enabled and there are undelivered submissions" in {
        val result = (Future.successful(ReplayInitializationResult.Started("1")), Future.successful(ReplayResult.Completed(5, 0, 0)))
        MockTrafficSwitchService.getTrafficSwitchState returns Future.successful(TrafficSwitchState.Flowing)
        MockAutoReplayRepository.getStatus() returns Future.successful(Some(AutoReplayRepoStatus(true, None)))
        MockEntryDeclarationRepo.totalUndeliveredMessages(now) returns Future.successful(5)
        MockReplayOrchestrator.startReplay(Some(5), ReplayTrigger.Automatic) returns result
        MockAutoReplayRepository.setLastReplay(Some("1"), now) returns
          Future.successful(Some(AutoReplayRepoStatus(true, Some(LastReplay(Some("1"), now)))))
        service.replay().futureValue shouldBe true
      }

      "reset TS and replay undelivered submissions if enabled and there are undelivered submissions" in {
        val result1 = (Future.successful(ReplayInitializationResult.Started("1")), Future.successful(ReplayResult.Completed(3, 3, 0)))
        val result2 = (Future.successful(ReplayInitializationResult.Started("1")), Future.successful(ReplayResult.Completed(2, 2, 0)))
        MockTrafficSwitchService.getTrafficSwitchState returns Future.successful(TrafficSwitchState.NotFlowing)
        MockAutoReplayRepository.getStatus() returns Future.successful(Some(AutoReplayRepoStatus(true, None)))
        MockEntryDeclarationRepo.totalUndeliveredMessages(now) returns Future.successful(5)
        MockTrafficSwitchService.startTrafficFlow returns Future.successful(())
        MockAppConfig.replayCountAfterTrafficSwitchReset returns 3
        MockReplayOrchestrator.startReplay(Some(3), ReplayTrigger.Automatic) returns result1
        MockAutoReplayRepository.setLastReplay(Some("1"), now) returns
          Future.successful(Some(AutoReplayRepoStatus(true, Some(LastReplay(Some("1"), now)))))
        MockReplayOrchestrator.startReplay(Some(2), ReplayTrigger.Automatic) returns result2
        MockAutoReplayRepository.setLastReplay(Some("1"), now) returns
          Future.successful(Some(AutoReplayRepoStatus(true, Some(LastReplay(Some("1"), now)))))
        service.replay().futureValue shouldBe true
      }

      "reset TS and not if enabled and there are no undelivered submissions" in {
        MockTrafficSwitchService.getTrafficSwitchState returns Future.successful(TrafficSwitchState.NotFlowing)
        MockAutoReplayRepository.getStatus() returns Future.successful(Some(AutoReplayRepoStatus(true, None)))
        MockEntryDeclarationRepo.totalUndeliveredMessages(now) returns Future.successful(0)
        MockAppConfig.replayCountAfterTrafficSwitchReset returns 3
        MockTrafficSwitchService.startTrafficFlow returns Future.successful(())
        service.replay().futureValue shouldBe false
      }

      "Not replay if enabled but there are no undelivered submissions" in {
        MockAutoReplayRepository.getStatus() returns Future.successful(Some(AutoReplayRepoStatus(true, None)))
        MockTrafficSwitchService.getTrafficSwitchState returns Future.successful(TrafficSwitchState.Flowing)
        MockEntryDeclarationRepo.totalUndeliveredMessages(now) returns Future.successful(0)
        service.replay().futureValue shouldBe false
      }

      "Not replay undelivered submissions if not enabled" in {
        MockAutoReplayRepository.getStatus() returns Future.successful(Some(AutoReplayRepoStatus(false, None)))
        service.replay().futureValue shouldBe false
      }

    }

  }
}
