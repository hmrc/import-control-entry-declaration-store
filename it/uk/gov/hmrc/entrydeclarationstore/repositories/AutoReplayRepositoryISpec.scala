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
import play.api.test.{DefaultAwaitTimeout, FutureAwaits, Injecting}
import uk.gov.hmrc.entrydeclarationstore.models.AutoReplayStatus

class AutoReplayRepositoryISpec
    extends AnyWordSpec
    with Matchers
    with FutureAwaits
    with DefaultAwaitTimeout
    with GuiceOneAppPerSuite
    with Injecting {

  lazy val repository: AutoReplayRepositoryImpl = inject[AutoReplayRepositoryImpl]

  "AutoReplayRepository" when {
    "off indicator document is not present" when {

      trait Scenario {
        await(repository.removeAll)
      }

      "getAutoReplayStatus" must {
        "return state as on" in new Scenario {
          await(repository.getAutoReplayStatus) shouldBe AutoReplayStatus.On
        }
      }

      "turn on" must {
        "do nothing" in new Scenario {
          await(repository.setAutoReplayStatus(AutoReplayStatus.On))

          await(repository.getAutoReplayStatus) shouldBe AutoReplayStatus.On
          await(repository.collection.countDocuments.toFuture)                 shouldBe 0
        }
      }

      "turn off" must {
        "add off indicator document" in new Scenario {
          await(repository.setAutoReplayStatus(AutoReplayStatus.Off))

          await(repository.getAutoReplayStatus) shouldBe AutoReplayStatus.Off
          await(repository.collection.countDocuments.toFuture)                 shouldBe 1
        }
      }
    }

    "database has off indicator document" when {
      trait Scenario {
        await(repository.removeAll)
        await(repository.setAutoReplayStatus(AutoReplayStatus.Off))
      }

      "getAutoReplayStatus" must {
        "report state as off" in new Scenario {
          await(repository.getAutoReplayStatus) shouldBe AutoReplayStatus.Off
        }
      }

      "turn off" must {
        "do nothing" in new Scenario {
          await(repository.setAutoReplayStatus(AutoReplayStatus.Off))

          await(repository.getAutoReplayStatus) shouldBe AutoReplayStatus.Off
          await(repository.collection.countDocuments.toFuture)                 shouldBe 1
        }
      }

      "turn on" must {
        "remove the off indicator document" in new Scenario {
          await(repository.setAutoReplayStatus(AutoReplayStatus.On))

          await(repository.getAutoReplayStatus) shouldBe AutoReplayStatus.On
          await(repository.collection.countDocuments.toFuture)                 shouldBe 0
        }
      }
    }
  }
}
