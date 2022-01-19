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

import java.time.Instant

import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

class EntryDeclarationMetadataSpec extends AnyWordSpec {

  "EntryDeclarationMetadata" must {
    "serialize to the correct JSON for a declaration" in {
      val model = EntryDeclarationMetadata(
        submissionId    = "submissionId",
        messageType     = MessageType.IE315,
        modeOfTransport = "12",
        receivedDateTime = Instant.parse("2001-12-12T12:34:56.567Z"),
        None
      )

      Json.toJson(model) shouldBe Json.parse("""
                                               |{
                                               |  "submissionId": "submissionId",
                                               |  "messageType": "IE315",
                                               |  "modeOfTransport": "12",
                                               |  "receivedDateTime": "2001-12-12T12:34:56.567Z"
                                               |}
                                               |""".stripMargin)
    }

    "serialize to the correct JSON for an amendment" in {
      val model = EntryDeclarationMetadata(
        submissionId    = "submissionId",
        messageType     = MessageType.IE313,
        modeOfTransport = "12",
        receivedDateTime = Instant.parse("2001-12-12T12:34:56.567Z"),
        Some("123456789012345678")
      )

      Json.toJson(model) shouldBe Json.parse("""
                                               |{
                                               |  "submissionId": "submissionId",
                                               |  "messageType": "IE313",
                                               |  "movementReferenceNumber":"123456789012345678",
                                               |  "modeOfTransport": "12",
                                               |  "receivedDateTime": "2001-12-12T12:34:56.567Z"
                                               |}
                                               |""".stripMargin)
    }

    "serialize to the correct JSON and padd out the date to millis" in {
      val model = EntryDeclarationMetadata(
        submissionId    = "submissionId",
        messageType     = MessageType.IE315,
        modeOfTransport = "12",
        receivedDateTime = Instant.parse("2001-12-12T12:34:56Z"),
        None
      )

      Json.toJson(model) shouldBe Json.parse("""
                                               |{
                                               |  "submissionId": "submissionId",
                                               |  "messageType": "IE315",
                                               |  "modeOfTransport": "12",
                                               |  "receivedDateTime": "2001-12-12T12:34:56.000Z"
                                               |}
                                               |""".stripMargin)
    }
  }
}
