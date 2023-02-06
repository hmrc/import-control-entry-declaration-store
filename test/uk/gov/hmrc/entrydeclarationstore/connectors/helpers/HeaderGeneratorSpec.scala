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

package uk.gov.hmrc.entrydeclarationstore.connectors.helpers

import java.time.{Clock, LocalDateTime, ZoneOffset, ZonedDateTime}

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.http.{HeaderNames, MimeTypes}
import uk.gov.hmrc.entrydeclarationstore.config.MockAppConfig
import uk.gov.hmrc.http.HeaderCarrier

class HeaderGeneratorSpec extends AnyWordSpec with Matchers with HeaderNames with MockAppConfig {

  val clock: Clock = {
    val t = ZonedDateTime.of(LocalDateTime.of(2015, 8, 13, 13, 28, 22), ZoneOffset.UTC)
    Clock.fixed(t.toInstant, ZoneOffset.UTC)
  }

  val testHeaderGenerator = new HeaderGenerator(clock, mockAppConfig)
  val submissionId        = "someId"
  val authToken           = "someToken"
  val env                 = "someEnv"

  "HeaderGenerator.headersForEIS" must {

    "create correct headers with CONTENT_TYPE" in {
      MockAppConfig.eisBearerToken returns authToken
      MockAppConfig.eisEnvironment returns env
      MockAppConfig.headerAllowlist returns Seq()
      implicit val hc: HeaderCarrier = HeaderCarrier()
      val headers                    = testHeaderGenerator.headersForEIS(submissionId).toMap

      headers("X-Correlation-ID") shouldBe submissionId
      headers(CONTENT_TYPE)       shouldBe MimeTypes.JSON
      headers(ACCEPT)             shouldBe MimeTypes.JSON
      headers(AUTHORIZATION)      shouldBe s"Bearer $authToken"
      headers("Environment")      shouldBe env
      headers(DATE)               shouldBe "Thu, 13 Aug 2015 13:28:22 GMT"
    }

    "include the Authorization header if it is supplied in AppConf" in {
      MockAppConfig.eisBearerToken returns authToken
      MockAppConfig.eisEnvironment returns env
      MockAppConfig.headerAllowlist returns Seq()
      implicit val hc: HeaderCarrier = HeaderCarrier()
      val headers                    = testHeaderGenerator.headersForEIS(submissionId).toMap

      headers(AUTHORIZATION) shouldBe s"Bearer $authToken"
    }

    "not include the Authorization header if it is empty string in AppConf" in {
      MockAppConfig.eisBearerToken returns ""
      MockAppConfig.eisEnvironment returns env
      MockAppConfig.headerAllowlist returns Seq()
      implicit val hc: HeaderCarrier = HeaderCarrier()
      val headers                    = testHeaderGenerator.headersForEIS(submissionId).toMap

      headers.contains(AUTHORIZATION) shouldBe false
    }

    "include allowListed headers" in {
      MockAppConfig.eisBearerToken returns authToken
      MockAppConfig.eisEnvironment returns env
      MockAppConfig.headerAllowlist returns Seq("latencyInMs", "testHeader") anyNumberOfTimes ()
      implicit val hc: HeaderCarrier =
        HeaderCarrier(extraHeaders = Seq("TESTHEADER" -> "test", "denylistheader" -> "200", "latencyInMs" -> "100"))
      val headers = testHeaderGenerator.headersForEIS(submissionId).toMap

      headers("latencyInMs")         shouldBe "100"
      headers("TESTHEADER")          shouldBe "test"
      headers.get("denylistheader") shouldBe None
    }
  }
}
