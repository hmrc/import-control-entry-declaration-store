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

package uk.gov.hmrc.entrydeclarationstore.utils

import org.scalatest.matchers.must.Matchers
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.entrydeclarationstore.reporting.{ClientInfo, ClientType}

class IdGeneratorSpec extends AnyWordSpec with Matchers {

  val idGenerator = new IdGenerator

  val clientInfoWithCSP: ClientInfo = ClientInfo(ClientType.CSP, Some("FOURJdnR590oacIXerAZPaVpoIk3"), Some("wTne7a1e-1111-2222-3333-d77f17chpOsw"))
  val clientInfoWithGGW: ClientInfo = ClientInfo(ClientType.GGW, None, None)

  "IdGenerator" when {
    "generating submissionIds" when {
      "generate ids that are different" in {
        val num = 1000000
        val ids = for (_ <- 1 to num) yield idGenerator.generateSubmissionId

        ids.toSet.size shouldBe num
      }

      "generate ids that are uuids" in {
        idGenerator.generateSubmissionId must fullyMatch regex """([a-f0-9]{8}(-[a-f0-9]{4}){4}[a-f0-9]{8})""".r
      }
    }

    "generating correlationIds" when {
      "generated ids for the same CSP user are different" in {
        val num = 1000000
        val ids = for (_ <- 1 to num) yield idGenerator.generateCorrelationIdFor(clientInfoWithCSP)

        val expectedPrefix: String = clientInfoWithCSP.clientId.getOrElse("").take(ClientInfo.cspPrefixLength)

        ids.toSet.size shouldBe num
        ids.map(_.substring(10,14)).toSet mustBe Set(expectedPrefix)
      }

      "generate ids for the GGW user are different" in {
        val num = 1000000
        val ids = for (_ <- 1 to num) yield idGenerator.generateCorrelationIdFor(clientInfoWithGGW)

        ids.toSet.size shouldBe num
      }

      "generate ids that are 14 characters alphanumeric" in {
        idGenerator.generateCorrelationIdFor(clientInfoWithCSP) must fullyMatch regex """[0-9A-Za-z]{14}""".r
        idGenerator.generateCorrelationIdFor(clientInfoWithGGW) must fullyMatch regex """[0-9A-Za-z]{14}""".r
      }

      "generate ids that are alphanumeric" in {
        for (_ <- 1 to 1000) idGenerator.generateCorrelationIdFor(clientInfoWithCSP).forall(c => c.isLetterOrDigit)
        for (_ <- 1 to 1000) idGenerator.generateCorrelationIdFor(clientInfoWithGGW).forall(c => c.isLetterOrDigit)
      }

      "a RuntimeException exception is thrown in case clientId is not present for CSP" in  {
        val cspClientInfoWithNoClientId = ClientInfo(ClientType.CSP, None, None)
        val exception = intercept[RuntimeException](idGenerator.generateCorrelationIdFor(cspClientInfoWithNoClientId))

        exception shouldBe a[RuntimeException]
        exception.getMessage shouldBe "Missing clientId for CSP user"
      }

      "a RuntimeException is thrown in case clientId is too short for CSP" in  {
        val cspClientInfoWithClientIdTooShort = ClientInfo(ClientType.CSP, Some("bee"), Some("wTne7a1e-1111-2222-3333-d77f17chpOsw"))
        val exception = intercept[RuntimeException](idGenerator.generateCorrelationIdFor(cspClientInfoWithClientIdTooShort))

        exception shouldBe a[RuntimeException]
        exception.getMessage shouldBe "clientId too short for CSP user"
      }
    }

    "generating using correlationIdFrom" when {
      "generate 00000000000000 for all zero bytes" in {
        idGenerator.randomStringFrom(Array.fill(14)(0.toByte)) shouldBe "00000000000000"
      }
      "generate 00000000000001 for all zero bytes except last as 1" in {
        idGenerator.randomStringFrom(Array.fill[Byte](13)(0) :+ 1.toByte) shouldBe "00000000000001"
      }

      "generate 0000000000000A for all zero bytes except last as 10" in {
        idGenerator.randomStringFrom(Array.fill[Byte](13)(0) :+ 10.toByte) shouldBe "0000000000000A"
      }

      "generate 0000000000000Z for all zero bytes except last as 35" in {
        idGenerator.randomStringFrom(Array.fill[Byte](13)(0) :+ 35.toByte) shouldBe "0000000000000Z"
      }

      "generate 0000000000000a for all zero bytes except last as 36" in {
        idGenerator.randomStringFrom(Array.fill[Byte](13)(0) :+ 36.toByte) shouldBe "0000000000000a"
      }

      "generate 0000000000000z for all zero bytes except last as 61" in {
        idGenerator.randomStringFrom(Array.fill[Byte](13)(0) :+ 61.toByte) shouldBe "0000000000000z"
      }

      "generate zzzzzzzzzzzzzz for bytes 60" in {
        idGenerator.randomStringFrom(Array.fill[Byte](14)(61)) shouldBe "zzzzzzzzzzzzzz"
      }

      "generate for all bytes values" in {
        for (i <- 0 to 255) {
          idGenerator.randomStringFrom(Array.fill[Byte](13)(0) :+ i.toByte) should startWith("0000000000000")
        }
      }

      "generate different values for all bytes in range 0 to 61" in {
        val ids = for (i <- 0 to 61) yield idGenerator.randomStringFrom(Array(i.toByte))

        ids.toSet.size shouldBe 62
      }
    }
  }
}
