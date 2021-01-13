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

package uk.gov.hmrc.entrydeclarationstore.orchestrators

import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.{Assertion, BeforeAndAfterAll, Matchers, WordSpec}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Injecting
import play.api.{Application, Environment, Mode}
import uk.gov.hmrc.entrydeclarationstore.housekeeping.HousekeepingScheduler
import uk.gov.hmrc.entrydeclarationstore.repositories.LockRepositoryProvider

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class ReplayLockISpec
    extends WordSpec
    with Matchers
    with GuiceOneAppPerSuite
    with BeforeAndAfterAll
    with Injecting
    with Eventually
    with ScalaFutures
    with IntegrationPatience {

  override def beforeAll(): Unit =
    lockRepositoryProvider.lockRepository.removeAll().futureValue

  val lockDuration: FiniteDuration = 500.millis

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .in(Environment.simple(mode = Mode.Dev))
    .configure("metrics.enabled" -> "false", "replay.lockDuration" -> s"${lockDuration.toMillis} millis")
    .disable[HousekeepingScheduler]
    .build()

  val lockRepositoryProvider: LockRepositoryProvider = inject[LockRepositoryProvider]
  val lock: ReplayLock                               = inject[ReplayLock]

  val replayId = "someReplayId"

  def isLockedTo(replayId: String): Boolean =
    lockRepositoryProvider.lockRepository.isLocked("replay_lock", replayId).futureValue

  def checkUnlocksAutomatically: Assertion =
    eventually {
      isLockedTo(replayId) shouldBe false
    }

  "ReplayLock" when {

    "unlocked" must {
      trait Scenario {
        lockRepositoryProvider.lockRepository.removeAll().futureValue
      }

      "allow lock" in new Scenario {
        lock.lock(replayId).futureValue shouldBe true
        isLockedTo(replayId)            shouldBe true
      }

      "ignore renew" in new Scenario {
        lock.renew(replayId).futureValue
        isLockedTo(replayId) shouldBe false
      }

      "ignore unlock" in new Scenario {
        lock.unlock(replayId).futureValue
        isLockedTo(replayId) shouldBe false
      }
    }

    "locked" must {
      trait Scenario {
        lockRepositoryProvider.lockRepository.removeAll().futureValue
        lock.lock(replayId).futureValue
      }

      "not allow lock again" in new Scenario {
        lock.lock(replayId).futureValue shouldBe false
        isLockedTo(replayId)            shouldBe true
      }

      "stays locked after renewal" in new Scenario {
        val renewalCount             = 10
        val keepRenewedFor: Duration = 1.5 * lockDuration
        val renewalSleep: Duration   = keepRenewedFor / renewalCount

        for (_ <- 1 to renewalCount) {
          lock.renew(replayId).futureValue
          Thread.sleep(renewalSleep.toMillis)
        }

        isLockedTo(replayId) shouldBe true
        checkUnlocksAutomatically
      }

      "no allow renew for a different replayId" in new Scenario {
        lock.renew("otherId").futureValue
        isLockedTo("otherId") shouldBe false
        isLockedTo(replayId)  shouldBe true
      }

      "allow unlock" in new Scenario {
        lock.unlock(replayId).futureValue
        isLockedTo(replayId) shouldBe false
      }

      "automatically unlock after configured time" in new Scenario {
        isLockedTo(replayId) shouldBe true
        checkUnlocksAutomatically
      }
    }
  }
}
