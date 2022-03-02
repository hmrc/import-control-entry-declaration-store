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
import java.time.Instant
import play.api.libs.json.Json

class AutoReplayStatusSpec extends AnyWordSpec {
  val now: Instant = Instant.parse("2022-03-02T10:26:38.559Z")

  implicit val formats = AutoReplayStatus.format
  "AutoReplayStatus" when {
    "On" must {
      val json = Json.parse("""
                              |{
                              |"autoReplay": true
                              |}
                              |""".stripMargin)

      "serialize to JSON correctly" in {
        val status: AutoReplayStatus = AutoReplayStatus.On(None)
        Json.toJson(status) shouldBe json
      }

      "deserialize from JSON correctly" in {
        json.as[AutoReplayStatus] shouldBe AutoReplayStatus.On(None)
      }
    }

    "Off" must {
      val json = Json.parse("""
                              |{
                              |"autoReplay": false
                              |}
                              |""".stripMargin)

      "serialize to JSON correctly" in {
        val status: AutoReplayStatus = AutoReplayStatus.Off(None)
        Json.toJson(status) shouldBe json
      }

      "deserialize from JSON correctly" in {
        json.as[AutoReplayStatus] shouldBe AutoReplayStatus.Off(None)
      }
    }

    "Unavailable" must {
      val json = Json.parse("""
                              |{
                              |}
                              |""".stripMargin)

      "serialize to JSON correctly" in {
        val status: AutoReplayStatus = AutoReplayStatus.Unavailable
        Json.toJson(status) shouldBe json
      }

      "deserialize from JSON correctly" in {
        json.as[AutoReplayStatus] shouldBe AutoReplayStatus.Unavailable
      }
    }

  }

  "AutoReplayStatus with ReplayState" when {
    val replayState: ReplayState = ReplayState(ReplayTrigger.Automatic, now, Some(now), true, 5, 0, 5)
    "On" must {
      val json = Json.parse("""
                              |{
                              |  "autoReplay": true,
                              |  "lastReplay": {
                              |    "trigger": "Automatic",
                              |    "startTime": "2022-03-02T10:26:38.559Z",
                              |    "endTime": "2022-03-02T10:26:38.559Z",
                              |    "completed": true,
                              |    "successCount": 5,
                              |    "failureCount": 0,
                              |    "totalToReplay": 5
                              |  }
                              |}
                              |""".stripMargin)

      "serialize to JSON correctly" in {
        val status: AutoReplayStatus = AutoReplayStatus.On(Some(replayState))
        Json.toJson(status) shouldBe json
      }

      "deserialize from JSON correctly" in {
        json.as[AutoReplayStatus] shouldBe AutoReplayStatus.On(Some(replayState))
      }
    }

    "Off" must {
      val json = Json.parse("""
                              |{
                              |"autoReplay": false,
                              |  "lastReplay": {
                              |    "trigger": "Automatic",
                              |    "startTime": "2022-03-02T10:26:38.559Z",
                              |    "endTime": "2022-03-02T10:26:38.559Z",
                              |    "completed": true,
                              |    "successCount": 5,
                              |    "failureCount": 0,
                              |    "totalToReplay": 5
                              |  }
                              |}
                              |""".stripMargin)

      "serialize to JSON correctly" in {
        val status: AutoReplayStatus = AutoReplayStatus.Off(Some(replayState))
        Json.toJson(status) shouldBe json
      }

      "deserialize from JSON correctly" in {
        json.as[AutoReplayStatus] shouldBe AutoReplayStatus.Off(Some(replayState))
      }
    }

    "Unavailable" must {
      val json = Json.parse("""
                              |{
                              |}
                              |""".stripMargin)

      "serialize to JSON correctly" in {
        val status: AutoReplayStatus = AutoReplayStatus.Unavailable
        Json.toJson(status) shouldBe json
      }

      "deserialize from JSON correctly" in {
        json.as[AutoReplayStatus] shouldBe AutoReplayStatus.Unavailable
      }
    }

  }

}
