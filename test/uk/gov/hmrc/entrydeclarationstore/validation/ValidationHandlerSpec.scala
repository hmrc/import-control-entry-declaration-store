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

import com.kenshoo.play.metrics.Metrics
import uk.gov.hmrc.entrydeclarationstore.config.MockAppConfig
import uk.gov.hmrc.entrydeclarationstore.logging.LoggingContext
import uk.gov.hmrc.entrydeclarationstore.models.{ErrorWrapper, RawPayload}
import uk.gov.hmrc.entrydeclarationstore.utils.{MockMetrics, XmlFormatConfig}
import uk.gov.hmrc.entrydeclarationstore.validation.business.MockRuleValidator
import uk.gov.hmrc.entrydeclarationstore.validation.schema.SchemaValidationResult._
import uk.gov.hmrc.entrydeclarationstore.validation.schema._
import uk.gov.hmrc.play.test.UnitSpec

import scala.xml.NodeSeq

class ValidationHandlerSpec extends UnitSpec with MockSchemaValidator with MockRuleValidator with MockAppConfig {

  val mockedMetrics: Metrics      = new MockMetrics
  implicit val lc: LoggingContext = LoggingContext("eori", "corrId", "subId")

  implicit val xmlFormatConfig: XmlFormatConfig = XmlFormatConfig(responseMaxErrors = 100)
  MockAppConfig.xmlFormatConfig returns xmlFormatConfig

  val validationHandler = new ValidationHandlerImpl(
    mockSchemaValidator,
    mockRuleValidator313,
    mockRuleValidator315,
    mockedMetrics,
    mockAppConfig
  )

  val mrn: String = "MRN"
  val eori        = "GB12345"

  def ie315payload(messageSender: Option[String] = Some(s"$eori/1234567890")): NodeSeq =
    // @formatter:off
      <tns:Header/>
      <tns:Body>
        <ie:CC315A>
          {for (value <- messageSender.toSeq) yield <MesSenMES3>{ value }</MesSenMES3>}
          <MesRecMES6>messageRecipient</MesRecMES6>
          <TraModAtBorHEA76>42</TraModAtBorHEA76>
        </ie:CC315A>
      </tns:Body>
  // @formatter:on

  def ie313payload(messageSender: Option[String] = Some(s"$eori/1234567890")): NodeSeq =
    // @formatter:off
      <tns:Header/>
      <tns:Body>
        <ie:CC313A>
          {for (value <- messageSender.toSeq) yield <MesSenMES3>{ value }</MesSenMES3>}
          <MesRecMES6>messageRecipient</MesRecMES6>
          <HEAHEA>
            <DocNumHEA5>{mrn}</DocNumHEA5>
            <TraModAtBorHEA76>42</TraModAtBorHEA76>
          </HEAHEA>
        </ie:CC313A>
      </tns:Body>
  // @formatter:on

  val errors: ValidationErrors = ValidationErrors(Seq(ValidationError("errText", "errType", "123", "errLocation")))

  def validationHandlerFor(schemaType: SchemaType, payload: NodeSeq, mrn: Option[String]): Unit = {
    val ruleValidator = mrn.map(_ => MockRuleValidator313).getOrElse(MockRuleValidator315)

    s"passed xml for $schemaType" when {
      "all valid" must {
        "return the payload" in {
          MockSchemaValidator.validate(schemaType, RawPayload(payload)) returns Valid(payload)
          ruleValidator.validate(payload) returns Right(())

          validationHandler.handleValidation(RawPayload(payload), eori, mrn) shouldBe Right(payload)
        }
      }

      "xml is not schema valid" must {
        "return an error" in {
          MockSchemaValidator.validate(schemaType, RawPayload(payload)) returns Invalid(payload, errors)

          validationHandler.handleValidation(RawPayload(payload), eori, mrn) shouldBe Left(ErrorWrapper(errors))
        }
      }

      "xml is malformed" must {
        "return an error" in {
          MockSchemaValidator.validate(schemaType, RawPayload(payload)) returns Malformed(errors)

          validationHandler.handleValidation(RawPayload(payload), eori, mrn) shouldBe Left(ErrorWrapper(errors))
        }
      }

      "xml has business rule errors" must {
        "return an error" in {
          MockSchemaValidator.validate(schemaType, RawPayload(payload)) returns Valid(payload)
          ruleValidator.validate(payload) returns Left(errors)

          validationHandler.handleValidation(RawPayload(payload), eori, mrn) shouldBe Left(ErrorWrapper(errors))
        }
      }
    }
  }

  def eoriCheckerSchemaValid(
    schemaType: SchemaType,
    payloadBuilder: Option[String] => NodeSeq,
    mrn: Option[String]): Unit = {
    def validate(payload: NodeSeq) = {
      MockSchemaValidator.validate(schemaType, RawPayload(payload)) returns Valid(payload)

      validationHandler.handleValidation(RawPayload(payload), eori, mrn)
    }

    s"passed xml for $schemaType" when {
      "xml is valid" when {
        "value is missing from the MesSenMES3 element" must {
          "return EORIMismatchError" in {
            validate(payloadBuilder(Some(""))) shouldBe Left(ErrorWrapper(EORIMismatchError))
          }
        }

        "eori part is missing from the MesSenMES3 element" must {
          "return EORIMismatchError" in {
            validate(payloadBuilder(Some("/abcde"))) shouldBe Left(ErrorWrapper(EORIMismatchError))
          }
        }

        "eori does not match specified eori" must {
          "return EORIMismatchError" in {
            validate(payloadBuilder(Some("otherEori"))) shouldBe Left(ErrorWrapper(EORIMismatchError))
          }
        }
      }
    }
  }

  def eoriCheckerSchemaInvalid(
    schemaType: SchemaType,
    payloadBuilder: Option[String] => NodeSeq,
    mrn: Option[String]): Unit = {
    def validate(payload: NodeSeq) = {
      MockSchemaValidator.validate(schemaType, RawPayload(payload)) returns Invalid(payload, errors)

      validationHandler.handleValidation(RawPayload(payload), eori, mrn)
    }

    s"passed xml for $schemaType" when {
      "xml is invalid" when {
        "value is missing from the MesSenMES3 element" must {
          "return EORIMismatchError" in {
            validate(payloadBuilder(Some(""))) shouldBe Left(ErrorWrapper(EORIMismatchError))
          }
        }

        "eori part is missing from the MesSenMES3 element" must {
          "return EORIMismatchError" in {
            validate(payloadBuilder(Some("/abcde"))) shouldBe Left(ErrorWrapper(EORIMismatchError))
          }
        }

        "MesSenMES3 element is missing" must {
          "return EORIMismatchError" in {
            validate(payloadBuilder(None))
          }
        }

        "eori does not match specified eori" must {
          "return EORIMismatchError" in {
            validate(payloadBuilder(Some("otherEori"))) shouldBe Left(ErrorWrapper(EORIMismatchError))
          }
        }
      }
    }
  }

  "ValidationHandler" when {
    "passed a 315" when {
      behave like validationHandlerFor(SchemaTypeE315, ie315payload(), None)
      behave like eoriCheckerSchemaValid(SchemaTypeE315, ie315payload, None)
      behave like eoriCheckerSchemaInvalid(SchemaTypeE315, ie315payload, None)
    }

    "passed a 313" when {
      behave like validationHandlerFor(SchemaTypeE313, ie313payload(), Some(mrn))
      behave like eoriCheckerSchemaValid(SchemaTypeE313, ie313payload, Some(mrn))
      behave like eoriCheckerSchemaInvalid(SchemaTypeE313, ie313payload, Some(mrn))

      "mrn does not match that in payload" must {
        "return an error" in {
          MockSchemaValidator.validate(SchemaTypeE313, RawPayload(ie313payload())) returns Valid(ie313payload())

          validationHandler.handleValidation(RawPayload(ie313payload()), eori, Some("otherMrn")) shouldBe Left(
            ErrorWrapper(MRNMismatchError))
        }
      }
    }
  }
}
