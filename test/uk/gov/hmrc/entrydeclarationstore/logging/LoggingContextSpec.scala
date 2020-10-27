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

package uk.gov.hmrc.entrydeclarationstore.logging

import uk.gov.hmrc.play.test.UnitSpec

class LoggingContextSpec extends UnitSpec {
  "LoggingContext" when {
    "not including message type" must {
      "include mandatory properties" in {
        LoggingContext(eori = "eori1", correlationId = "correlationId1", submissionId = "submissionId1").context shouldBe
          "(eori=eori1 correlationId=correlationId1 submissionId=submissionId1)"
      }

      "include properties specified" in {
        LoggingContext(eori = Some("eori1"), correlationId = Some("correlationId1")).context shouldBe
          "(eori=eori1 correlationId=correlationId1)"
      }
    }

    "including message type" must {
      "use 313 message type if no mrn specified" in {
        LoggingContext
          .withMessageType(
            eori          = "eori1",
            correlationId = "correlationId1",
            submissionId  = "submissionId1",
            mrn           = Some("mrn1")
          )
          .context shouldBe
          "CC313A (eori=eori1 correlationId=correlationId1 submissionId=submissionId1 movementReferenceNumber=mrn1)"
      }

      "use 315 message type if mrn specified" in {
        LoggingContext
          .withMessageType(
            eori          = "eori1",
            correlationId = "correlationId1",
            submissionId  = "submissionId1",
            mrn           = None
          )
          .context shouldBe
          "CC315A (eori=eori1 correlationId=correlationId1 submissionId=submissionId1)"
      }
    }
  }
}
