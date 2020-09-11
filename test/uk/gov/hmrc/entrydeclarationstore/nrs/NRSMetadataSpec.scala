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

package uk.gov.hmrc.entrydeclarationstore.nrs

import java.time.Instant

import play.api.libs.json.Json
import play.api.mvc.Headers
import play.api.test.FakeRequest
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.entrydeclarationstore.utils.ChecksumUtils.StringWithSha256

class NRSMetadataSpec extends UnitSpec with NRSMetadataTestData {
  "NRSMetadata" must {
    "contain the auth token from the request" in {
      val token = "someToken"

      val request = FakeRequest().withHeaders("Authorization" -> token)

      NRSMetadata(Instant.now, "eori", identityData, request, request.body.toString.calculateSha256).userAuthToken shouldBe token
    }

    "contain the headers from the request" in {
      val request =
        FakeRequest().withHeaders(
          Headers("Header" -> "value", "MultiValueHeader" -> "value1", "MultiValueHeader" -> "value2"))

      NRSMetadata(Instant.now, "eori", identityData, request, request.body.toString.calculateSha256).headerData shouldBe
        Json.parse("""{
                     |  "Header": "value",
                     |  "MultiValueHeader":"value1,value2"
                     |}
                     |""".stripMargin)
    }

    "format correctly as JSON" in {
      Json.toJson(nrsMetadata) shouldBe nrsMetadataJson
    }
  }
}
