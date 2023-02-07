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

package uk.gov.hmrc.entrydeclarationstore.controllers

import java.time.Instant

import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.wordspec.AnyWordSpec
import play.api.http.MimeTypes
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.entrydeclarationstore.models.{ReplayTrigger, ReplayState}
import uk.gov.hmrc.entrydeclarationstore.services.MockReplayStateRetrievalService
import ReplayState.Implicits._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ReplayStateRetrievalControllerSpec extends AnyWordSpec with MockReplayStateRetrievalService {
  val controller =
    new ReplayStateRetrievalController(Helpers.stubControllerComponents(), mockReplayStateRetrievalService)

  val replayId = "replayId"

  "ReplayStateRetrievalController" when {
    "state exists for a replayId" must {
      "return it" in {
        val state = ReplayState(replayId, Instant.now, 2, ReplayTrigger.Manual, None, None, 0, 1)

        MockReplayStateRetrievalService.retrieveReplayState(replayId) returns Future.successful(Some(state))

        val result = controller.retrieveReplayState(replayId)(FakeRequest())

        status(result)        shouldBe OK
        contentType(result)   shouldBe Some(MimeTypes.JSON)
        contentAsJson(result) shouldBe Json.toJson(state)
      }
    }
    "state does not exist for a replayId" must {
      "return a 404" in {
        MockReplayStateRetrievalService.retrieveReplayState(replayId) returns Future.successful(None)

        val result = controller.retrieveReplayState(replayId)(FakeRequest())

        status(result)      shouldBe NOT_FOUND
        contentType(result) shouldBe None
      }
    }
  }

}
