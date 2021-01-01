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

package uk.gov.hmrc.entrydeclarationstore.utils

import uk.gov.hmrc.play.test.UnitSpec

class IdGeneratorSpec extends UnitSpec {

  val idGenerator = new IdGenerator

  "IdGenerator" when {
    "generating submissionIds" must {
      "generate ids that are different" in {
        val num = 1000000
        val ids = for (_ <- 1 to num) yield idGenerator.generateSubmissionId

        ids.toSet.size shouldBe num
      }

      "generate ids that are uuids" in {
        idGenerator.generateSubmissionId should fullyMatch regex """([a-f0-9]{8}(-[a-f0-9]{4}){4}[a-f0-9]{8})""".r
      }
    }

    "generating correlationIds" must {
      "generate ids that are different" in {
        val num = 1000000
        val ids = for (_ <- 1 to num) yield idGenerator.generateCorrelationId

        ids.toSet.size shouldBe num
      }

      "generate ids that are 14 characters alphanumeric" in {
        idGenerator.generateCorrelationId should fullyMatch regex """[0-9A-Za-z]{14}""".r
      }

      "generate ids that are alphanumeric" in {
        for (_ <- 1 to 1000) idGenerator.generateCorrelationId.forall(c => c.isLetterOrDigit)
      }
    }

    "generating using correlationIdFrom" must {
      "generate 00000000000000 for all zero bytes" in {
        idGenerator.correlationIdFrom(Array.fill(14)(0.toByte)) shouldBe "00000000000000"
      }
      "generate 00000000000001 for all zero bytes except last as 1" in {
        idGenerator.correlationIdFrom(Array.fill[Byte](13)(0) :+ 1.toByte) shouldBe "00000000000001"
      }

      "generate 0000000000000A for all zero bytes except last as 10" in {
        idGenerator.correlationIdFrom(Array.fill[Byte](13)(0) :+ 10.toByte) shouldBe "0000000000000A"
      }

      "generate 0000000000000Z for all zero bytes except last as 35" in {
        idGenerator.correlationIdFrom(Array.fill[Byte](13)(0) :+ 35.toByte) shouldBe "0000000000000Z"
      }

      "generate 0000000000000a for all zero bytes except last as 36" in {
        idGenerator.correlationIdFrom(Array.fill[Byte](13)(0) :+ 36.toByte) shouldBe "0000000000000a"
      }

      "generate 0000000000000z for all zero bytes except last as 61" in {
        idGenerator.correlationIdFrom(Array.fill[Byte](13)(0) :+ 61.toByte) shouldBe "0000000000000z"
      }

      "generate zzzzzzzzzzzzzz for bytes 60" in {
        idGenerator.correlationIdFrom(Array.fill[Byte](14)(61)) shouldBe "zzzzzzzzzzzzzz"
      }

      "generate for all bytes values" in {
        for (i <- 0 to 255) {
          idGenerator.correlationIdFrom(Array.fill[Byte](13)(0) :+ i.toByte) should startWith("0000000000000")
        }
      }

      "generate different values for all bytes in range 0 to 61" in {
        val ids = for (i <- 0 to 61) yield idGenerator.correlationIdFrom(Array(i.toByte))

        ids.toSet.size shouldBe 62
      }
    }
  }
}
