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

package uk.gov.hmrc.entrydeclarationstore.models.json

import java.time.Instant

import org.scalatest.Inside
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.entrydeclarationstore.logging.LoggingContext
import uk.gov.hmrc.entrydeclarationstore.models.{ErrorWrapper, ServerError}
import uk.gov.hmrc.entrydeclarationstore.utils.ResourceUtils

import scala.xml.{Elem, XML}

class DeclarationToJsonConverterSpec extends AnyWordSpec with Inside {
  val declarationToJsonConverter = new DeclarationToJsonConverter

  implicit val lc: LoggingContext = LoggingContext("eori", "corrId", "subId")

  "EntrySummaryDeclaration toJson " must {
    "for submission" must {
      val input: InputParameters = InputParameters(
        None,
        "1234567890-1234567890-1234567890-123",
        "correlationID1",
        Instant.parse("1234-12-10T00:34:17Z"))
      "work for xml with no optional fields" in {
        val xml: Elem     = ResourceUtils.withInputStreamFor("xmls/315NoOptional.xml")(XML.load)
        val json: JsValue = ResourceUtils.withInputStreamFor("jsons/315NoOptional.json")(Json.parse)
        inside(declarationToJsonConverter.convertToJson(xml, input)) {
          case Right(ie315) =>
            ie315                                          shouldBe json
            declarationToJsonConverter.validateJson(ie315) shouldBe true
          case Left(_) => fail()
        }
      }
      "work for xml with all optional fields" in {
        val xml: Elem     = ResourceUtils.withInputStreamFor("xmls/315AllOptional.xml")(XML.load)
        val json: JsValue = ResourceUtils.withInputStreamFor("jsons/315AllOptional.json")(Json.parse)
        inside(declarationToJsonConverter.convertToJson(xml, input)) {
          case Right(ie315) =>
            ie315                                          shouldBe json
            declarationToJsonConverter.validateJson(ie315) shouldBe true
          case Left(_) => fail()
        }
      }
    }
    "for amendment" must {
      val input: InputParameters = InputParameters(
        Some("mrn"),
        "1234567890-1234567890-1234567890-123",
        "correlationID1",
        Instant.parse("1234-12-10T00:34:17Z"))
      "work for xml with all amendment specific fields" in {
        val xml: Elem     = ResourceUtils.withInputStreamFor("xmls/313SpecificFields.xml")(XML.load)
        val json: JsValue = ResourceUtils.withInputStreamFor("jsons/313SpecificFields.json")(Json.parse)
        inside(declarationToJsonConverter.convertToJson(xml, input)) {
          case Right(ie313) =>
            ie313                                          shouldBe json
            declarationToJsonConverter.validateJson(ie313) shouldBe true
          case Left(_) => fail()
        }
      }
    }
    "for bad xml" must {
      "return Left(ServerError)" in {
        val input: InputParameters = InputParameters(
          None,
          "1234567890-1234567890-1234567890-123",
          "correlationID1",
          Instant.parse("1234-12-10T00:34:17Z"))
        val xml =
          //@formatter:off
          <ie:CC315A xmlns:ie="http://ics.dgtaxud.ec/CC315A"></ie:CC315A>
        //@formatter:on
        declarationToJsonConverter.convertToJson(xml, input) shouldBe Left(ErrorWrapper(ServerError))
      }
    }
  }
}
