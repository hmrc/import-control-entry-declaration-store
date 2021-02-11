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

package uk.gov.hmrc.entrydeclarationstore.validation

import uk.gov.hmrc.entrydeclarationstore.models.RawPayload
import uk.gov.hmrc.entrydeclarationstore.utils.{XmlFormatConfig, XmlFormats}
import uk.gov.hmrc.entrydeclarationstore.validation.schema.{SchemaType, SchemaValidator}
import uk.gov.hmrc.play.test.UnitSpec

import scala.xml.Node

class ValidationErrorsSpec extends UnitSpec {

  val validator = new SchemaValidator

  val maxErrors                                 = 100
  implicit val xmlFormatConfig: XmlFormatConfig = XmlFormatConfig(responseMaxErrors = maxErrors)

  val errorResponseSchemaType: SchemaType = new SchemaType {
    private[validation] val schema = schemaFor("xsds/errorresponse-v2.0.xsd")
  }

  "ValidationErrors" when {

    def validationErrors(numErrors: Int): ValidationErrors =
      ValidationErrors(
        Seq.fill(numErrors)(
          ValidationError(
            errorText     = "text1",
            errorType     = "type2",
            errorNumber   = "1234",
            errorLocation = "/location1"
          )))

    val errors = validationErrors(2)

    "serialized to XML" must {
      val format = implicitly[XmlFormats[ValidationErrors]]

      def getMessageCountElement(responseXml: Node): Option[Int] =
        (responseXml \ "Application" \ "MessageCount").headOption.map(_.text.toInt)

      def getErrorElementCount(responseXml: Node): Int = (responseXml \ "Error").size

      "validate against the error response schema" in {

        val responseXml = format.toXml(errors)
        validator.validate(errorResponseSchemaType, RawPayload(responseXml)) shouldBe a[Right[_, _]]
      }

      "include the number of messages" in {
        val responseXml = format.toXml(errors)

        getMessageCountElement(responseXml) should contain(2)
        getErrorElementCount(responseXml)   shouldBe 2
      }

      "limit to errors" in {
        val numErrors   = maxErrors * 2
        val responseXml = format.toXml(validationErrors(numErrors))

        getMessageCountElement(responseXml) should contain(maxErrors)
        getErrorElementCount(responseXml)   shouldBe maxErrors
      }
    }
  }
}
