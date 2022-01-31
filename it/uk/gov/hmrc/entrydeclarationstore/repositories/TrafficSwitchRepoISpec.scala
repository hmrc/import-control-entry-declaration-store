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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.{DefaultAwaitTimeout, FutureAwaits, Injecting}
import play.api.{Application, Environment, Mode}
import uk.gov.hmrc.entrydeclarationstore.housekeeping.HousekeepingScheduler
import uk.gov.hmrc.entrydeclarationstore.models.{TrafficSwitchState, TrafficSwitchStatus}

class TrafficSwitchRepoISpec
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

  lazy val repository: TrafficSwitchRepoImpl = inject[TrafficSwitchRepoImpl]

  "TrafficSwitchRepo" when {
    "database is empty" when {

      trait Scenario {
        await(repository.removeAll)
      }

      "getTrafficSwitchStatus" must {
        "return state as flowing" in new Scenario {
          await(repository.getTrafficSwitchStatus) shouldBe TrafficSwitchStatus(TrafficSwitchState.Flowing, None, None)
        }
      }

      "set flowing" must {
        "do nothing" in new Scenario {
          await(repository.setTrafficSwitchState(TrafficSwitchState.Flowing)) shouldBe None

          await(repository.getTrafficSwitchStatus) shouldBe TrafficSwitchStatus(TrafficSwitchState.Flowing, None, None)
        }
      }

      "set not flowing" must {
        "set not flowing and update the last traffic stopped date" in new Scenario {
          val setResult: Option[TrafficSwitchStatus] = await(repository.setTrafficSwitchState(TrafficSwitchState.NotFlowing))

          val getResult: TrafficSwitchStatus = await(repository.getTrafficSwitchStatus)
          getResult.isTrafficFlowing   shouldBe TrafficSwitchState.NotFlowing
          getResult.lastTrafficStopped should not be empty
          getResult.lastTrafficStarted shouldBe empty
          setResult shouldBe Some(getResult)
        }
      }

      "resetToDefault" must {
        "do nothing" in new Scenario {
          await(repository.resetToDefault)

          await(repository.collection.countDocuments.toFuture)                  shouldBe 0
          await(repository.getTrafficSwitchStatus) shouldBe repository.defaultStatus
        }
      }
    }

    "database state is explicitly not flowing" when {
      trait Scenario {
        await(repository.removeAll)
        await(repository.setTrafficSwitchState(TrafficSwitchState.NotFlowing))

        val initialStatus: TrafficSwitchStatus = await(repository.getTrafficSwitchStatus)
      }

      "getTrafficSwitchStatus" must {
        "return state as not flowing" in new Scenario {
          initialStatus.isTrafficFlowing   shouldBe TrafficSwitchState.NotFlowing
          initialStatus.lastTrafficStarted shouldBe empty
          initialStatus.lastTrafficStopped should not be empty
        }
      }

      "set not flowing" must {
        "do nothing" in new Scenario {
          await(repository.setTrafficSwitchState(TrafficSwitchState.NotFlowing)) shouldBe None

          await(repository.getTrafficSwitchStatus) shouldBe initialStatus
        }
      }

      "set flowing" must {
        "set flowing and update the last traffic started date" in new Scenario {
          val setResult: Option[TrafficSwitchStatus] = await(repository.setTrafficSwitchState(TrafficSwitchState.Flowing))

          val getResult: TrafficSwitchStatus = await(repository.getTrafficSwitchStatus)
          getResult.isTrafficFlowing   shouldBe TrafficSwitchState.Flowing
          getResult.lastTrafficStarted should not be empty
          getResult.lastTrafficStopped shouldBe initialStatus.lastTrafficStopped
          setResult shouldBe Some(getResult)
        }
      }

      "resetToDefault" must {
        "set state to the default" in new Scenario {
          await(repository.resetToDefault) shouldBe ((): Unit)

          await(repository.collection.countDocuments.toFuture)                  shouldBe 0
          await(repository.getTrafficSwitchStatus) shouldBe repository.defaultStatus
        }
      }
    }

    "database state is flowing" when {
      trait Scenario {
        await(repository.removeAll)
        await(repository.setTrafficSwitchState(TrafficSwitchState.NotFlowing))
        await(repository.setTrafficSwitchState(TrafficSwitchState.Flowing))

        val initialStatus: TrafficSwitchStatus = await(repository.getTrafficSwitchStatus)
      }

      "getTrafficSwitchStatus" must {
        "return state as flowing" in new Scenario {
          initialStatus.isTrafficFlowing   shouldBe TrafficSwitchState.Flowing
          initialStatus.lastTrafficStarted should not be empty
          initialStatus.lastTrafficStopped should not be empty
        }
      }

      "set flowing" must {
        "do nothing" in new Scenario {
          await(repository.setTrafficSwitchState(TrafficSwitchState.Flowing)) shouldBe None

          await(repository.getTrafficSwitchStatus) shouldBe initialStatus
        }
      }

      "set not flowing" must {
        "set not flowing and update the last traffic stopped date" in new Scenario {
          val setResult: Option[TrafficSwitchStatus] = await(repository.setTrafficSwitchState(TrafficSwitchState.NotFlowing))

          val getResult: TrafficSwitchStatus = await(repository.getTrafficSwitchStatus)
          getResult.isTrafficFlowing   shouldBe TrafficSwitchState.NotFlowing
          getResult.lastTrafficStarted shouldBe initialStatus.lastTrafficStarted
          getResult.lastTrafficStopped shouldBe >(initialStatus.lastTrafficStopped)
          setResult shouldBe Some(getResult)
        }
      }

      "resetToDefault" must {
        "set state to the default" in new Scenario {
          await(repository.resetToDefault)

          await(repository.collection.countDocuments.toFuture)                  shouldBe 0
          await(repository.getTrafficSwitchStatus) shouldBe repository.defaultStatus
        }
      }
    }

    // Case where both lastTrafficStarted and lastTrafficStopped dates are set initially
    "traffic switch flow is stopped after previous traffic flow start" must {
      trait Scenario {
        await(repository.removeAll)
        await(repository.setTrafficSwitchState(TrafficSwitchState.NotFlowing))
        await(repository.setTrafficSwitchState(TrafficSwitchState.Flowing))
        await(repository.setTrafficSwitchState(TrafficSwitchState.NotFlowing))

        val initialStatus: TrafficSwitchStatus = await(repository.getTrafficSwitchStatus)
      }

      "set not flowing" must {
        "do nothing" in new Scenario {
          await(repository.setTrafficSwitchState(TrafficSwitchState.NotFlowing)) shouldBe None

          await(repository.getTrafficSwitchStatus) shouldBe initialStatus
        }
      }

      "set flowing" must {
        "set flowing and update the last traffic started date" in new Scenario {
          val setResult: Option[TrafficSwitchStatus] = await(repository.setTrafficSwitchState(TrafficSwitchState.Flowing))

          val getResult: TrafficSwitchStatus = await(repository.getTrafficSwitchStatus)
          getResult.isTrafficFlowing   shouldBe TrafficSwitchState.Flowing
          getResult.lastTrafficStarted shouldBe >(initialStatus.lastTrafficStarted)
          getResult.lastTrafficStopped shouldBe initialStatus.lastTrafficStopped
          setResult shouldBe Some(getResult)
        }
      }

      "resetToDefault" must {
        "set state to the default" in new Scenario {
          await(repository.resetToDefault)

          await(repository.collection.countDocuments.toFuture)                  shouldBe 0
          await(repository.getTrafficSwitchStatus) shouldBe repository.defaultStatus
        }
      }
    }
  }
}
