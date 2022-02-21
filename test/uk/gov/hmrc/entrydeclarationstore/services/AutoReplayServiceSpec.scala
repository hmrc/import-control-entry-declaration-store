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
import uk.gov.hmrc.entrydeclarationstore.models.AutoReplayStatus
import uk.gov.hmrc.entrydeclarationstore.repositories.MockAutoReplayRepository
import scala.concurrent.Future

class AutoReplayServiceSpec
    extends AnyWordSpec
    with MockAppConfig
    with MockAutoReplayRepository
    with ScalaFutures {

  val service = new AutoReplayService(mockAutoReplayRepository)

  "AutoReplayService" when {
    "getting autoReplay status" must {
      "get using the repo" in {
        val status = AutoReplayStatus.On

        MockAutoReplayRepository.getAutoReplayStatus returns Future.successful(status)
        service.getStatus.futureValue shouldBe status
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

  }
}
