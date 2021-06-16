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

package uk.gov.hmrc.entrydeclarationstore.reporting

import org.scalatest.Matchers.convertToAnyShouldWrapper
import play.api.mvc.Headers
import org.scalatest.WordSpec

class ClientInfoSpec extends WordSpec {
  "ClientInfo" when {
    "creating from implicit headers" must {
      val applicationId = "someAppId"
      val clientId      = "someClientId"

      "work when headers present" in {
        implicit val headers: Headers = Headers("X-Application-Id" -> applicationId, "X-Client-Id" -> clientId)

        ClientInfo(ClientType.GGW) shouldBe ClientInfo(
          ClientType.GGW,
          clientId      = Some(clientId),
          applicationId = Some(applicationId))
      }

      "work when headers missing" in {
        implicit val headers: Headers = Headers()

        ClientInfo(ClientType.GGW) shouldBe ClientInfo(ClientType.GGW, clientId = None, applicationId = None)
      }

      "be case insensitive to header names" in {
        implicit val headers: Headers = Headers("x-application-ID" -> applicationId, "x-client-ID" -> clientId)

        ClientInfo(ClientType.GGW) shouldBe ClientInfo(
          ClientType.GGW,
          clientId      = Some(clientId),
          applicationId = Some(applicationId))
      }
    }
  }
}
