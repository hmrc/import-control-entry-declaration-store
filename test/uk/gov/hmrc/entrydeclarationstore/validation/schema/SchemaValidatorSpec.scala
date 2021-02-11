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

package uk.gov.hmrc.entrydeclarationstore.validation.schema

import org.scalatest.Inside
import uk.gov.hmrc.entrydeclarationstore.models.RawPayload
import uk.gov.hmrc.entrydeclarationstore.utils.ResourceUtils
import uk.gov.hmrc.entrydeclarationstore.validation.{ValidationError, ValidationErrors}
import uk.gov.hmrc.play.test.UnitSpec

import scala.xml.XML

class SchemaValidatorSpec extends UnitSpec with Inside {

  val validator = new SchemaValidator()

  "Schema validator" when {
    "Validating a E313" when {

      "passed valid sample" must {
        "return Right containing the parsed xml" in {
          val resourceName       = "xmls/CC313A-schemaValidSample-v11-1.xml"
          val xml                = XML.load(ResourceUtils.url(resourceName))
          val rawXml: RawPayload = RawPayload(ResourceUtils.asByteArray(resourceName))

          validator.validate(SchemaTypeE313, rawXml) shouldBe Right(xml)
        }
      }

      "passed invalid sample" must {
        "return Left(ValidationErrors)" in {
          val resourceName       = "xmls/CC313A-schemaInvalidSample-v11-1.xml"
          val rawXml: RawPayload = RawPayload(ResourceUtils.asByteArray(resourceName))

          validator.validate(SchemaTypeE313, rawXml) shouldBe Left(
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
            )))
        }
      }

      "passed valid E315" must {
        "return a Left" in {
          val resourceName       = "xmls/CC315A-schemaValidSample-v11-1.xml"
          val rawXml: RawPayload = RawPayload(ResourceUtils.asByteArray(resourceName))

          validator.validate(SchemaTypeE313, rawXml) shouldBe Left(
            ValidationErrors(
              Seq(
                ValidationError(
                  errorText     = "Cannot find the declaration of element 'ie:CC315A'.",
                  errorType     = "schema",
                  errorNumber   = "4057",
                  errorLocation = "/"
                )
              )))
        }
      }

      "passed a declaration with an envelope and body" must {
        "return Left(ValidationErrors)" in {
          val resourceName       = "xmls/CC313A-schemaValidSampleWithEnvelope-v11-1.xml"
          val rawXml: RawPayload = RawPayload(ResourceUtils.asByteArray(resourceName))

          validator.validate(SchemaTypeE313, rawXml) shouldBe
            Left(
              ValidationErrors(
                Seq(
                  ValidationError(
                    errorText     = "Cannot find the declaration of element 'tns:Envelope'.",
                    errorType     = "schema",
                    errorNumber   = "4057",
                    errorLocation = "/"
                  )
                )))
        }
      }
    }

    "Validating a E315" when {
      "passed valid sample" must {
        "return Right containing the parsed xml" in {
          val resourceName       = "xmls/CC315A-schemaValidSample-v11-1.xml"
          val xml                = XML.load(ResourceUtils.url(resourceName))
          val rawXml: RawPayload = RawPayload(ResourceUtils.asByteArray(resourceName))

          validator.validate(SchemaTypeE315, rawXml) shouldBe Right(xml)
        }
      }

      "passed invalid sample" must {
        "return Left(ValidationErrors)" in {
          val resourceName       = "xmls/CC315A-schemaInvalidSample-v11-1.xml"
          val rawXml: RawPayload = RawPayload(ResourceUtils.asByteArray(resourceName))

          validator.validate(SchemaTypeE315, rawXml) shouldBe Left(
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
            )))
        }
      }

      "passed valid E313" must {
        "return a Left" in {
          val resourceName       = "xmls/CC313A-schemaValidSample-v11-1.xml"
          val rawXml: RawPayload = RawPayload(ResourceUtils.asByteArray(resourceName))

          validator.validate(SchemaTypeE315, rawXml) shouldBe
            Left(
              ValidationErrors(
                Seq(
                  ValidationError(
                    errorText     = "Cannot find the declaration of element 'ie:CC313A'.",
                    errorType     = "schema",
                    errorNumber   = "4057",
                    errorLocation = "/"
                  )
                )))
        }
      }

      "passed a declaration with an envelope and body" must {
        "return Left(ValidationErrors)" in {
          val resourceName       = "xmls/CC315A-schemaValidSampleWithEnvelope-v11-1.xml"
          val rawXml: RawPayload = RawPayload(ResourceUtils.asByteArray(resourceName))

          validator.validate(SchemaTypeE315, rawXml) shouldBe
            Left(
              ValidationErrors(
                Seq(
                  ValidationError(
                    errorText     = "Cannot find the declaration of element 'tns:Envelope'.",
                    errorType     = "schema",
                    errorNumber   = "4057",
                    errorLocation = "/"
                  )
                )))
        }
      }

      "passed malformed xml sample" must {
        "return Left(Validation`Errors)" in {
          val xml = "<xml><hello"

          validator.validate(SchemaTypeE315, RawPayload(xml)) shouldBe a[Left[_, _]]
        }
      }
    }

    "decoding special characters" when {
      val xmlBytes: Array[Byte] =
        ResourceUtils.asByteArray("xmls/CC313A-schemaValidSampleWithSpecialChars-v11-1.xml")

      "no character encoding is present with the payload" must {
        "infer the encoding" in {
          inside(validator.validate(SchemaTypeE313, RawPayload(xmlBytes))) {
            case Right(xml) => (xml \\ "StrAndNumPLD1").text shouldBe "1234 Avenue du Sacré-Cœur"
          }
        }
      }

      "an character encoding present with the payload" must {
        "use that encoding" in {
          // Deliberately use the wrong encoding (that used by default by BodyParsers.tolerantText)
          // to prove that it has he correct effect...
          inside(validator.validate(SchemaTypeE313, RawPayload(xmlBytes, Some("ISO-8859-1")))) {
            case Right(xml) => (xml \\ "StrAndNumPLD1").text shouldBe "1234 Avenue du SacrÃ©-CÅ\u0093ur"
          }
        }
      }
    }
  }
}
