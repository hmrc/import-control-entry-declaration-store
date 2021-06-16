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

package uk.gov.hmrc.entrydeclarationstore.nrs

import org.scalatest.Matchers.convertToAnyShouldWrapper
import play.api.libs.json.Json
import uk.gov.hmrc.entrydeclarationstore.models.RawPayload
import org.scalatest.WordSpec

class NRSSubmissionSpec extends WordSpec with NRSMetadataTestData {

  "NRSSubmission" must {
    "format to the correct JSON with base64 encoded payload" in {
      val nrsSubmission = NRSSubmission(RawPayload("somePayload"), nrsMetadata)

      val nrsSubmissionJson =
        Json.parse(s"""{
                      | "payload": "c29tZVBheWxvYWQ=",
                      | "metadata": ${nrsMetadataJson.toString()}
                      |}""".stripMargin)

      Json.toJson(nrsSubmission) shouldBe nrsSubmissionJson
    }
  }
}
