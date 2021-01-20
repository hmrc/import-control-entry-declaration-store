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

package uk.gov.hmrc.entrydeclarationstore.models.json

import com.lucidchart.open.xtract.ParseSuccess
import uk.gov.hmrc.play.test.UnitSpec

class TraderSpec extends UnitSpec {

  "Trader" when {
    "read from XML" when {

      val reader = Trader.reader(
        "NAME",
        Address.reader("STREET", "CITY", "POSTCODE", "COUNTRY"),
        "LANGUAGE",
        "EORI"
      )

      "address has no fields" must {
        "make Address None" in {
          val xml = <trader>
              <NAME>name</NAME>
              <LANGUAGE>lang</LANGUAGE>
              <EORI>eori</EORI>
            </trader>

          reader.read(xml) shouldBe ParseSuccess(Trader(Some("name"), address = None, Some("lang"), Some("eori")))
        }
      }

      "address has only a street" must {
        "include an Address object" in {
          val xml = <trader>
            <NAME>name</NAME>
            <STREET>street</STREET>
            <LANGUAGE>lang</LANGUAGE>
            <EORI>eori</EORI>
          </trader>

          reader.read(xml) shouldBe ParseSuccess(
            Trader(Some("name"), address = Some(Address(Some("street"), None, None, None)), Some("lang"), Some("eori")))
        }
      }

      "address has only a city" must {
        "include an Address object" in {
          val xml = <trader>
            <NAME>name</NAME>
            <CITY>city</CITY>
            <LANGUAGE>lang</LANGUAGE>
            <EORI>eori</EORI>
          </trader>

          reader.read(xml) shouldBe ParseSuccess(
            Trader(Some("name"), address = Some(Address(None, Some("city"), None, None)), Some("lang"), Some("eori")))
        }
      }

      "address has only a postcode" must {
        "include an Address object" in {
          val xml = <trader>
            <NAME>name</NAME>
            <POSTCODE>postCode</POSTCODE>
            <LANGUAGE>lang</LANGUAGE>
            <EORI>eori</EORI>
          </trader>

          reader.read(xml) shouldBe ParseSuccess(
            Trader(
              Some("name"),
              address = Some(Address(None, None, Some("postCode"), None)),
              Some("lang"),
              Some("eori")))
        }
      }

      "address has only a countrycode" must {
        "include an Address object" in {
          val xml = <trader>
            <NAME>name</NAME>
            <COUNTRY>country</COUNTRY>
            <LANGUAGE>lang</LANGUAGE>
            <EORI>eori</EORI>
          </trader>

          reader.read(xml) shouldBe ParseSuccess(
            Trader(
              Some("name"),
              address = Some(Address(None, None, None, Some("country"))),
              Some("lang"),
              Some("eori")))
        }
      }
    }
  }
}
