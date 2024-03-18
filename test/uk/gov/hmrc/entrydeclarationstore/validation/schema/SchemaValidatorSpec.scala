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

package uk.gov.hmrc.entrydeclarationstore.validation.schema

import org.scalatest.matchers.should.Matchers.{a, convertToAnyShouldWrapper}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{Assertion, Inside}
import uk.gov.hmrc.entrydeclarationstore.models.RawPayload
import uk.gov.hmrc.entrydeclarationstore.utils.ResourceUtils
import uk.gov.hmrc.entrydeclarationstore.validation.{ValidationError, ValidationErrors}

import javax.xml.parsers.SAXParserFactory
import scala.xml.XML

class SchemaValidatorSpec extends AnyWordSpec with Inside {

  val validator = new SchemaValidator()
  val factory = SAXParserFactory.newInstance()

  "Schema validator" when {
    "Validating a E313" when {

      factory.setNamespaceAware(true)
      factory.setSchema(SchemaTypeE313.schema)
      val saxParser = factory.newSAXParser()

      "passed valid sample" must {

        "return Valid containing the parsed xml" in {

          val resourceName       = "xmls/CC313A-schemaValidSample-v11-2.xml"
          val xml                = XML.withSAXParser(saxParser).load(ResourceUtils.url(resourceName))
          val rawXml: RawPayload = RawPayload(ResourceUtils.asByteArray(resourceName))

          validator.validate(SchemaTypeE313, rawXml) shouldBe SchemaValidationResult.Valid(xml)
        }
      }

      "passed invalid sample" must {
        "return Invalid containing the xml and the ValidationErrors" in {

          val resourceName       = "xmls/CC313A-schemaInvalidSample-v11-2.xml"
          val xml                = XML.withSAXParser(saxParser).load(ResourceUtils.url(resourceName))
          val rawXml: RawPayload = RawPayload(ResourceUtils.asByteArray(resourceName))

          validator.validate(SchemaTypeE313, rawXml) shouldBe SchemaValidationResult.Invalid(
            xml,
            ValidationErrors(Seq(
              ValidationError(
                errorText =
                  "Value 'XXX' is not facet-valid with respect to pattern '[0-9]{1,5}' for type 'Numeric_Max5'.",
                errorType     = "schema",
                errorNumber   = "4085",
                errorLocation = "/ie:CC313A[1]/HEAHEA[1]/TotNumOfIteHEA305[1]"
              ),
              ValidationError(
                errorText     = "The value 'XXX' of element 'TotNumOfIteHEA305' is not valid.",
                errorType     = "schema",
                errorNumber   = "4065",
                errorLocation = "/ie:CC313A[1]/HEAHEA[1]/TotNumOfIteHEA305[1]"
              ),
              ValidationError(
                errorText =
                  "Value 'XXX' is not facet-valid with respect to pattern '[0-9]{1,5}' for type 'Numeric_Max5'.",
                errorType     = "schema",
                errorNumber   = "4085",
                errorLocation = "/ie:CC313A[1]/GOOITEGDS[2]/PACGS2[1]/NumOfPacGS24[1]"
              ),
              ValidationError(
                errorText     = "The value 'XXX' of element 'NumOfPacGS24' is not valid.",
                errorType     = "schema",
                errorNumber   = "4065",
                errorLocation = "/ie:CC313A[1]/GOOITEGDS[2]/PACGS2[1]/NumOfPacGS24[1]"
              )
            ))
          )
        }
      }

      "passed valid E315" must {
        "return Invalid containing the xml and the ValidationErrors" in {

          val resourceName       = "xmls/CC315A-schemaValidSample-v11-2.xml"
          val xml                = XML.withSAXParser(saxParser).load(ResourceUtils.url(resourceName))
          val rawXml: RawPayload = RawPayload(ResourceUtils.asByteArray(resourceName))

          validator.validate(SchemaTypeE313, rawXml) shouldBe SchemaValidationResult.Invalid(
            xml,
            ValidationErrors(
              Seq(
                ValidationError(
                  errorText     = "Cannot find the declaration of element 'ie:CC315A'.",
                  errorType     = "schema",
                  errorNumber   = "4057",
                  errorLocation = "/"
                )
              ))
          )
        }
      }

      "passed a declaration with an envelope and body" must {
        "return Invalid containing the xml and the ValidationErrors" in {

          val resourceName       = "xmls/CC313A-schemaValidSampleWithEnvelope-v11-2.xml"
          val xml                = XML.withSAXParser(saxParser).load(ResourceUtils.url(resourceName))
          val rawXml: RawPayload = RawPayload(ResourceUtils.asByteArray(resourceName))

          validator.validate(SchemaTypeE313, rawXml) shouldBe SchemaValidationResult.Invalid(
            xml,
            ValidationErrors(
              Seq(
                ValidationError(
                  errorText     = "Cannot find the declaration of element 'tns:Envelope'.",
                  errorType     = "schema",
                  errorNumber   = "4057",
                  errorLocation = "/"
                )
              ))
          )
        }
      }
    }

    "Validating a E315" when {

      factory.setNamespaceAware(true)
      factory.setSchema(SchemaTypeE315.schema)
      val saxParser = factory.newSAXParser()

      "passed valid sample" must {
        "return Valid containing the parsed xml" in {

          val resourceName       = "xmls/CC315A-schemaValidSample-v11-2.xml"
          val xml                = XML.withSAXParser(saxParser)load(ResourceUtils.url(resourceName))
          val rawXml: RawPayload = RawPayload(ResourceUtils.asByteArray(resourceName))

          validator.validate(SchemaTypeE315, rawXml) shouldBe SchemaValidationResult.Valid(xml)
        }
      }

      "passed invalid sample" must {
        "return Invalid containing the xml and the ValidationErrors" in {

          val resourceName       = "xmls/CC315A-schemaInvalidSample-v11-2.xml"
          val xml                = XML.withSAXParser(saxParser).load(ResourceUtils.url(resourceName))
          val rawXml: RawPayload = RawPayload(ResourceUtils.asByteArray(resourceName))

          validator.validate(SchemaTypeE315, rawXml) shouldBe SchemaValidationResult.Invalid(
            xml,
            ValidationErrors(Seq(
              ValidationError(
                errorText =
                  "Value 'XXX' is not facet-valid with respect to pattern '[0-9]{1,5}' for type 'Numeric_Max5'.",
                errorType     = "schema",
                errorNumber   = "4085",
                errorLocation = "/ie:CC315A[1]/HEAHEA[1]/TotNumOfIteHEA305[1]"
              ),
              ValidationError(
                errorText     = "The value 'XXX' of element 'TotNumOfIteHEA305' is not valid.",
                errorType     = "schema",
                errorNumber   = "4065",
                errorLocation = "/ie:CC315A[1]/HEAHEA[1]/TotNumOfIteHEA305[1]"
              ),
              ValidationError(
                errorText =
                  "Value 'XXX' is not facet-valid with respect to pattern '[0-9]{1,5}' for type 'Numeric_Max5'.",
                errorType     = "schema",
                errorNumber   = "4085",
                errorLocation = "/ie:CC315A[1]/GOOITEGDS[2]/PACGS2[1]/NumOfPacGS24[1]"
              ),
              ValidationError(
                errorText     = "The value 'XXX' of element 'NumOfPacGS24' is not valid.",
                errorType     = "schema",
                errorNumber   = "4065",
                errorLocation = "/ie:CC315A[1]/GOOITEGDS[2]/PACGS2[1]/NumOfPacGS24[1]"
              )
            ))
          )
        }
      }

      "passed valid E313" must {
        "return Invalid containing the xml and the ValidationErrors" in {

          val resourceName       = "xmls/CC313A-schemaValidSample-v11-2.xml"
          val xml                = XML.withSAXParser(saxParser).load(ResourceUtils.url(resourceName))
          val rawXml: RawPayload = RawPayload(ResourceUtils.asByteArray(resourceName))

          validator.validate(SchemaTypeE315, rawXml) shouldBe SchemaValidationResult.Invalid(
            xml,
            ValidationErrors(
              Seq(
                ValidationError(
                  errorText     = "Cannot find the declaration of element 'ie:CC313A'.",
                  errorType     = "schema",
                  errorNumber   = "4057",
                  errorLocation = "/"
                )
              ))
          )
        }
      }

      "passed a declaration with an envelope and body" must {
        "return Invalid containing the xml and the ValidationErrors" in {

          val resourceName       = "xmls/CC315A-schemaValidSampleWithEnvelope-v11-2.xml"
          val xml                = XML.withSAXParser(saxParser).load(ResourceUtils.url(resourceName))
          val rawXml: RawPayload = RawPayload(ResourceUtils.asByteArray(resourceName))

          validator.validate(SchemaTypeE315, rawXml) shouldBe SchemaValidationResult.Invalid(
            xml,
            ValidationErrors(
              Seq(
                ValidationError(
                  errorText     = "Cannot find the declaration of element 'tns:Envelope'.",
                  errorType     = "schema",
                  errorNumber   = "4057",
                  errorLocation = "/"
                )
              ))
          )
        }
      }

      "passed malformed xml sample" must {
        "return Malformed containing Validation Errors" in {
          val xml = "<xml><hello"

          validator.validate(SchemaTypeE315, RawPayload(xml)) shouldBe a[SchemaValidationResult.Malformed]
        }
      }
    }

    "decoding special characters" when {
      val rawFileXmlBytes: Array[Byte] =
        ResourceUtils.asByteArray("xmls/CC313A-schemaValidSampleWithSpecialChars-v11-2.xml")

      val xmlString = ResourceUtils.asString("xmls/CC313A-schemaValidSampleWithSpecialChars-v11-2.xml")

      val byteOrderMark = '\uFEFF'
      val addressLine   = "1234 Avenue du Sacré-Cœur"

      "no explicit character encoding provided (from HTTP header)" must {
        def inferTheEncodingFrom(xmlBytes: Array[Byte]): Assertion =
          inside(validator.validate(SchemaTypeE313, RawPayload(xmlBytes))) {
            case SchemaValidationResult.Valid(xml) => (xml \\ "StrAndNumPLD1").text shouldBe addressLine
          }

        "infer the encoding" in {
          inferTheEncodingFrom(rawFileXmlBytes)
        }

        "infer the encoding when encoded in UTF-16BE with byte order marking" in {
          inferTheEncodingFrom(s"$byteOrderMark$xmlString".getBytes("UTF-16BE"))
        }

        "infer the encoding when encoded in UTF-16LE with byte order marking" in {
          inferTheEncodingFrom(s"$byteOrderMark$xmlString".getBytes("UTF-16LE"))
        }
      }

      "an explicit character encoding provided (from HTTP header)" must {
        "work" in {
          val charset  = "UTF-16BE"
          val xmlBytes = xmlString.getBytes(charset)

          inside(validator.validate(SchemaTypeE313, RawPayload(xmlBytes, Some(charset)))) {
            case SchemaValidationResult.Valid(xml) => (xml \\ "StrAndNumPLD1").text shouldBe addressLine
          }
        }

        "use in preference (as per RFC-3023) to what would be inferred" in {
          // The xml parser seems pretty good at getting the right character encoding on its own,
          // so one way to prove that it is using the encoding we are providing
          // deliberately give it the wrong one...
          inside(validator.validate(SchemaTypeE313, RawPayload(rawFileXmlBytes, Some("ISO-8859-1")))) {
            case SchemaValidationResult.Valid(xml) =>
              (xml \\ "StrAndNumPLD1").text shouldBe "1234 Avenue du SacrÃ©-CÅ\u0093ur"
          }
        }
      }
    }
  }
}
