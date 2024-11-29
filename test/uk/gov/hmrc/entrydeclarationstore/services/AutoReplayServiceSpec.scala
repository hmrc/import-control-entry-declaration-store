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

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.entrydeclarationstore.models._
import uk.gov.hmrc.entrydeclarationstore.orchestrators.MockReplayOrchestrator
import uk.gov.hmrc.entrydeclarationstore.repositories.{MockAutoReplayRepository, MockEntryDeclarationRepo}
import uk.gov.hmrc.entrydeclarationstore.trafficswitch.TrafficSwitchConfig

import java.time._
import scala.concurrent.Future
import scala.concurrent.duration._

class AutoReplayServiceSpec
    extends AnyWordSpec
    with MockAutoReplayRepository
    with MockEntryDeclarationRepo
    with MockReplayStateRetrievalService
    with MockReplayOrchestrator
    with MockTrafficSwitchService
    with ScalaFutures {

  val now: Instant = Instant.now
  val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)
  val TrafficSwitchMaxFailures: Int = 5
  val config: TrafficSwitchConfig =
    TrafficSwitchConfig(TrafficSwitchMaxFailures, 200.millis, notFlowingStateRefreshPeriod = 1.minute, flowingStateRefreshPeriod = 1.minute)

  val service = new AutoReplayService(
    config,
    mockReplayOrchestrator,
    mockAutoReplayRepository,
    mockEntryDeclarationRepo,
    mockReplayStateRetrievalService,
    mockTrafficSwitchService,
    clock)

  "AutoReplayService" when {
    "getting autoReplay status" must {
      "get using the repo" in {
        val status = Some(AutoReplayRepoStatus(true))

        MockAutoReplayRepository.getStatus() returns Future.successful(status)
        MockReplayStateRetrievalService.mostRecentByTrigger(ReplayTrigger.Automatic) returns Future.successful(None)
        service.getStatus().futureValue shouldBe AutoReplayStatus.On(None)
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
        val result = (Future.successful(ReplayInitializationResult.Started("1")), Future.successful(ReplayResult.Completed(5, 5, 0)))
        MockTrafficSwitchService.getTrafficSwitchState returns Future.successful(TrafficSwitchState.Flowing)
        MockAutoReplayRepository.getStatus() returns Future.successful(Some(AutoReplayRepoStatus(true)))
        MockEntryDeclarationRepo.totalUndeliveredMessages(now) returns Future.successful(5)
        MockReplayOrchestrator.startReplay(Some(5), ReplayTrigger.Automatic) returns result
        service.replay(1).futureValue shouldBe true
      }

      "reset TS and replay undelivered submissions if enabled and there are undelivered submissions" in {
        val result1 = (Future.successful(ReplayInitializationResult.Started("1")), Future.successful(ReplayResult.Completed(TrafficSwitchMaxFailures, TrafficSwitchMaxFailures, 0)))
        val result2 = (Future.successful(ReplayInitializationResult.Started("1")), Future.successful(ReplayResult.Completed(2, 2, 0)))
        MockTrafficSwitchService.getTrafficSwitchState returns Future.successful(TrafficSwitchState.NotFlowing)
        MockAutoReplayRepository.getStatus() returns Future.successful(Some(AutoReplayRepoStatus(true)))
        MockEntryDeclarationRepo.totalUndeliveredMessages(now) returns Future.successful(7)
        MockTrafficSwitchService.startTrafficFlow returns Future.successful(())
        MockReplayOrchestrator.startReplay(Some(TrafficSwitchMaxFailures), ReplayTrigger.Automatic) returns result1
        MockReplayOrchestrator.startReplay(Some(2), ReplayTrigger.Automatic) returns result2
        service.replay(1).futureValue shouldBe true
      }

      "reset TS and not if enabled and there are no undelivered submissions" in {
        MockTrafficSwitchService.getTrafficSwitchState returns Future.successful(TrafficSwitchState.NotFlowing)
        MockAutoReplayRepository.getStatus() returns Future.successful(Some(AutoReplayRepoStatus(true)))
        MockEntryDeclarationRepo.totalUndeliveredMessages(now) returns Future.successful(0)
        MockTrafficSwitchService.startTrafficFlow returns Future.successful(())
        service.replay(1).futureValue shouldBe false
      }

      "Not replay if enabled but there are no undelivered submissions" in {
        MockAutoReplayRepository.getStatus() returns Future.successful(Some(AutoReplayRepoStatus(true)))
        MockTrafficSwitchService.getTrafficSwitchState returns Future.successful(TrafficSwitchState.Flowing)
        MockEntryDeclarationRepo.totalUndeliveredMessages(now) returns Future.successful(0)
        service.replay(1).futureValue shouldBe false
      }

      "Not replay undelivered submissions if not enabled" in {
        MockAutoReplayRepository.getStatus() returns Future.successful(Some(AutoReplayRepoStatus(false)))
        service.replay(1).futureValue shouldBe false
      }

      "Auto-replay fails (and fate logged) when ReplayOrchestrator aborts" in {
        val result = (Future.successful(ReplayInitializationResult.Started("1")), Future.successful(ReplayResult.Aborted(new Exception("Aborted"))))
        MockTrafficSwitchService.getTrafficSwitchState returns Future.successful(TrafficSwitchState.Flowing)
        MockAutoReplayRepository.getStatus() returns Future.successful(Some(AutoReplayRepoStatus(true)))
        MockEntryDeclarationRepo.totalUndeliveredMessages(now) returns Future.successful(5)
        MockReplayOrchestrator.startReplay(Some(5), ReplayTrigger.Automatic) returns result
        service.replay(1).futureValue shouldBe false
      }
    }
  }
}
