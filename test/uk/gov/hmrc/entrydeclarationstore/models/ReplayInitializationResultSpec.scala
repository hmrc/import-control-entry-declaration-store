/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.entrydeclarationstore.models

import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

class ReplayInitializationResultSpec extends AnyWordSpec {
  "ReplayStartResult.Started" must {
    "correctly serialize to JSON" in {
      Json.toJson(ReplayInitializationResult.Started("someId") : ReplayInitializationResult) shouldBe
        Json.parse("""{
                     |  "replayId": "someId", 
                     |  "alreadyStarted": false
                     |}""".stripMargin)
    }
  }

  "ReplayStartResult.AlreadyRunning" when {
    "the latest replayId can be determined" must {
      "correctly serialize to JSON" in {
        Json.toJson(ReplayInitializationResult.AlreadyRunning(Some("someId")) : ReplayInitializationResult) shouldBe
          Json.parse("""{
                       |  "replayId": "someId", 
                       |  "alreadyStarted": true
                       |}""".stripMargin)
      }
    }

    "the latest replayId cannot be determined" must {
      "correctly serialize to JSON" in {
        Json.toJson(ReplayInitializationResult.AlreadyRunning(None) : ReplayInitializationResult) shouldBe
          Json.parse("""{
                       |  "alreadyStarted": true
                       |}""".stripMargin)
      }
    }
  }
}
