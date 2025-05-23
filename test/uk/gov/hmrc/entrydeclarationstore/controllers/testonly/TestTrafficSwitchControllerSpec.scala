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

package uk.gov.hmrc.entrydeclarationstore.controllers.testonly

import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.wordspec.AnyWordSpec
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.entrydeclarationstore.services.MockTrafficSwitchService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TestTrafficSwitchControllerSpec extends AnyWordSpec with MockTrafficSwitchService {

  val controller = new TestTrafficSwitchController(Helpers.stubControllerComponents(), mockTrafficSwitchService)

  "TestTrafficSwitchController" when {
    "stopping the EIS traffic switch" must {
      "return 204" when {
        "successful" in {
          MockTrafficSwitchService.stopTrafficFlow returns Future.successful((): Unit)

          val result = controller.stopTrafficFlow()(FakeRequest())

          status(result) shouldBe NO_CONTENT
        }
      }
    }
  }
  "resetting the traffic switch" must {
    "return 204" when {
      "successful" in {
        MockTrafficSwitchService.resetTrafficSwitch returns Future.successful((): Unit)

        val result = controller.resetTrafficSwitch()(FakeRequest())

        status(result) shouldBe NO_CONTENT
      }
    }
  }
}
