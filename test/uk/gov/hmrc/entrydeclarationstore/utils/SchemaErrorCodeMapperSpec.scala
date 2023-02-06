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

import org.scalatest.Inside
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.entrydeclarationstore.repositories.MockEntryDeclarationRepo

class SchemaErrorCodeMapperSpec extends AnyWordSpec with MockEntryDeclarationRepo with Inside with ScalaFutures {

  "Error Mapper" must {
    "return 4000" when {
      "cvc-attribute.3 is supplied as the validator error code" in {
        SchemaErrorCodeMapper.getErrorCodeFromParserFailure("cvc-attribute.3") shouldBe 4000
      }
    }
    "return 4000" when {
      "cvc-attribute.4 is supplied as the validator error code" in {
        SchemaErrorCodeMapper.getErrorCodeFromParserFailure("cvc-attribute.4") shouldBe 4000
      }
    }
    "return 4057" when {
      "cvc-elt.1 is supplied as the validator error code" in {
        SchemaErrorCodeMapper.getErrorCodeFromParserFailure("cvc-elt.1") shouldBe 4057
      }
    }
    "return 4086" when {
      "cvc-totalDigits-valid is supplied as the validator error code" in {
        SchemaErrorCodeMapper.getErrorCodeFromParserFailure("cvc-totalDigits-valid") shouldBe 4086
      }
    }
    "return 4999" when {
      "an unrecognised validator error code is supplied" in {
        SchemaErrorCodeMapper.getErrorCodeFromParserFailure("swayze") shouldBe 4999
      }
    }

  }
}
