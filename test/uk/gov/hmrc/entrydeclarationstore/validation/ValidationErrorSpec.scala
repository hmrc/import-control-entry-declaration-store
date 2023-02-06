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

package uk.gov.hmrc.entrydeclarationstore.validation

import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.wordspec.AnyWordSpec

import scala.xml.SAXParseException

class ValidationErrorSpec extends AnyWordSpec {
  "ValidationError" when {
    "created from SaxParseException" must {

      "take error code from error message if there is one" in {
        ValidationError(new SAXParseException("cvc-attribute.3: message", null), "location") shouldBe
          ValidationError("message", "schema", "4000", "location")
      }

      "use error message on its own if there is no error code" in {
        ValidationError(new SAXParseException("message", null), "location") shouldBe
          ValidationError("message", "schema", "4999", "location")
      }
    }
  }
}
