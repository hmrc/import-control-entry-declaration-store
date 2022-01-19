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

package uk.gov.hmrc.entrydeclarationstore.models

import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

class UndeliveredCountsSpec extends AnyWordSpec {

  "UndeliveredCounts" when {
    "there are undelivered submissions" must {
      val json = Json.parse("""
                              |{
                              |"totalCount": 10,
                              |"transportCounts": [
                              | { "transportMode": "01", "count": 7},
                              | { "transportMode": "10", "count": 3}
                              |]
                              |}
                              |""".stripMargin)
      val undeliveredCounts = UndeliveredCounts(
        totalCount = 10,
        transportCounts = Some(
          Seq(
            TransportCount(transportMode = "01", count = 7),
            TransportCount(transportMode = "10", count = 3)
          )))

      "serialize to JSON correctly" in {
        Json.toJson(undeliveredCounts) shouldBe json
      }

      "deserialize from JSON correctly" in {
        json.as[UndeliveredCounts] shouldBe undeliveredCounts
      }
    }

    "there are no undelivered submissions" must {
      val emptyJson         = Json.parse("""
                                   |{
                                   |"totalCount": 0
                                   |}
                                   |""".stripMargin)
      val undeliveredCounts = UndeliveredCounts(totalCount = 0, transportCounts = None)

      "serialize to JSON correctly" in {
        Json.toJson(undeliveredCounts) shouldBe emptyJson
      }

      "deserialize from JSON correctly" in {
        emptyJson.as[UndeliveredCounts] shouldBe undeliveredCounts
      }
    }
  }
}
