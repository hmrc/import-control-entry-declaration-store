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

package uk.gov.hmrc.entrydeclarationstore.models

import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.play.test.UnitSpec

class ReplayStartResultSpec extends UnitSpec {
  "ReplayStartResult.Started" must {
    "correctly serialize to JSON" in {
      Json.toJson(ReplayStartResult.Started("someId")) shouldBe
        Json.parse("""{
                     |  "replayId": "someId", 
                     |  "alreadyStarted": false
                     |}""".stripMargin)
    }
  }

  "ReplayStartResult.AlreadyRunning" when {
    "the latest replayId can be determined" must {
      "correctly serialize to JSON" in {
        Json.toJson(ReplayStartResult.AlreadyRunning(Some("someId"))) shouldBe
          Json.parse("""{
                       |  "replayId": "someId", 
                       |  "alreadyStarted": true
                       |}""".stripMargin)
      }
    }

    "the latest replayId cannot be determined" must {
      "correctly serialize to JSON" in {
        Json.toJson(ReplayStartResult.AlreadyRunning(None)) shouldBe
          Json.parse("""{
                       |  "alreadyStarted": true
                       |}""".stripMargin)
      }
    }
  }
}
