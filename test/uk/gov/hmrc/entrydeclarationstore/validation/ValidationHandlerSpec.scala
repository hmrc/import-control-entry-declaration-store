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
import uk.gov.hmrc.entrydeclarationstore.services.MRNMismatchError
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
  val mrn: String           = "MRN"
  val IE315payload: NodeSeq =
    // @formatter:off
      <tns:Header/>
      <tns:Body>
        <ie:CC315A>
          <MesSenMES3>ABCDEF1234/1234567890</MesSenMES3>
          <MesRecMES6>messageRecipient</MesRecMES6>
          <TraModAtBorHEA76>42</TraModAtBorHEA76>
        </ie:CC315A>
      </tns:Body>
  // @formatter:on

  val IE313payload: NodeSeq =
    // @formatter:off
      <tns:Header/>
      <tns:Body>
        <ie:CC313A>
          <HEAHEA>
            <DocNumHEA5>{mrn}</DocNumHEA5>
            <TraModAtBorHEA76>42</TraModAtBorHEA76>
          </HEAHEA>
          <MesSenMES3>ABCDEF1234/1234567890</MesSenMES3>
          <MesRecMES6>messageRecipient</MesRecMES6>
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

          validationHandler.handleValidation(RawPayload(payload), mrn) shouldBe Right(payload)
        }
      }

      "xml is not schema valid" must {
        "return an error" in {
          MockSchemaValidator.validate(schemaType, RawPayload(payload)) returns Invalid(payload, errors)

          validationHandler.handleValidation(RawPayload(payload), mrn) shouldBe Left(ErrorWrapper(errors))
        }
      }

      "xml is malformed" must {
        "return an error" in {
          MockSchemaValidator.validate(schemaType, RawPayload(payload)) returns Malformed(errors)

          validationHandler.handleValidation(RawPayload(payload), mrn) shouldBe Left(ErrorWrapper(errors))
        }
      }

      "xml has business rule errors" must {
        "return an error" in {
          MockSchemaValidator.validate(schemaType, RawPayload(payload)) returns Valid(payload)
          ruleValidator.validate(payload) returns Left(errors)

          validationHandler.handleValidation(RawPayload(payload), mrn) shouldBe Left(ErrorWrapper(errors))
        }
      }
    }
  }

  "ValidationHandler" when {
    "passed a 315" when {
      behave like validationHandlerFor(SchemaTypeE315, IE315payload, None)
    }

    "passed a 313" when {
      behave like validationHandlerFor(SchemaTypeE313, IE313payload, Some(mrn))

      "mrn does not match that in payload" must {
        "return an error" in {
          MockSchemaValidator.validate(SchemaTypeE313, RawPayload(IE313payload)) returns Valid(IE313payload)

          validationHandler.handleValidation(RawPayload(IE313payload), Some("otherMrn")) shouldBe Left(
            ErrorWrapper(MRNMismatchError))
        }
      }
    }
  }
}
