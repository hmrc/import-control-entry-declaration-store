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

package uk.gov.hmrc.entrydeclarationstore.models

import org.scalatest.Matchers.convertToAnyShouldWrapper
import uk.gov.hmrc.entrydeclarationstore.utils.XmlFormats
import org.scalatest.WordSpec

import scala.xml.Utility

class StandardErrorSpec extends WordSpec {
  "StandardError" must {
    "Serialize to xml correctly" in {
      val formatter = implicitly[XmlFormats[StandardError]]

      val ignoredStatus = 1234
      Utility.trim(formatter.toXml(StandardError(ignoredStatus, "someCode", "someErrorMessage"))) shouldBe
        Utility.trim(
          <error>
            <code>someCode</code>
            <message>someErrorMessage</message>
          </error>
        )
    }
  }
}
