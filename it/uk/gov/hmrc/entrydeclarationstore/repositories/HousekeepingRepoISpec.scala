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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.{DefaultAwaitTimeout, FutureAwaits, Injecting}
import play.api.{Application, Environment, Mode}
import uk.gov.hmrc.entrydeclarationstore.housekeeping.HousekeepingScheduler
import uk.gov.hmrc.entrydeclarationstore.models.HousekeepingStatus

class HousekeepingRepoISpec
    extends AnyWordSpec
    with Matchers
    with FutureAwaits
    with DefaultAwaitTimeout
    with GuiceOneAppPerSuite
    with Injecting {

  override lazy val app: Application = new GuiceApplicationBuilder()
    .in(Environment.simple(mode = Mode.Dev))
    .disable[HousekeepingScheduler]
    .configure("metrics.enabled" -> "false")
    .build()

  lazy val repository: HousekeepingRepoImpl = inject[HousekeepingRepoImpl]

  "HousekeepingRepo" when {
    "off indicator document is not present" when {

      trait Scenario {
        await(repository.removeAll)
      }

      "getHousekeepingStatus" must {
        "return state as on" in new Scenario {
          await(repository.getHousekeepingStatus) shouldBe HousekeepingStatus(on = true)
        }
      }

      "turn on" must {
        "do nothing" in new Scenario {
          await(repository.enableHousekeeping(true))

          await(repository.getHousekeepingStatus) shouldBe HousekeepingStatus(on = true)
          await(repository.collection.countDocuments.toFuture)                 shouldBe 0
        }
      }

      "turn off" must {
        "add off indicator document" in new Scenario {
          await(repository.enableHousekeeping(false))

          await(repository.getHousekeepingStatus) shouldBe HousekeepingStatus(on = false)
          await(repository.collection.countDocuments.toFuture)                 shouldBe 1
        }
      }
    }

    "database has off indicator document" when {
      trait Scenario {
        await(repository.removeAll)
        await(repository.enableHousekeeping(false))
      }

      "getHousekeepingStatus" must {
        "report state as off" in new Scenario {
          await(repository.getHousekeepingStatus) shouldBe HousekeepingStatus(on = false)
        }
      }

      "turn off" must {
        "do nothing" in new Scenario {
          await(repository.enableHousekeeping(false))

          await(repository.getHousekeepingStatus) shouldBe HousekeepingStatus(on = false)
          await(repository.collection.countDocuments.toFuture)                 shouldBe 1
        }
      }

      "turn on" must {
        "remove the off indicator document" in new Scenario {
          await(repository.enableHousekeeping(true))

          await(repository.getHousekeepingStatus) shouldBe HousekeepingStatus(on = true)
          await(repository.collection.countDocuments.toFuture)                 shouldBe 0
        }
      }
    }
  }
}
