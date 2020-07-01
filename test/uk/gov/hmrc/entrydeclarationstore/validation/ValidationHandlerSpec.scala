/*
 * Copyright 2020 HM Revenue & Customs
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
import uk.gov.hmrc.entrydeclarationstore.models.ErrorWrapper
import uk.gov.hmrc.entrydeclarationstore.services.MRNMismatchError
import uk.gov.hmrc.entrydeclarationstore.utils.{MockMetrics, XmlFormatConfig}
import uk.gov.hmrc.entrydeclarationstore.validation.business.MockRuleValidator
import uk.gov.hmrc.entrydeclarationstore.validation.schema.{MockSchemaValidator, SchemaTypeE313, SchemaTypeE315}
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
  val mrn: Some[String]     = Some("MRN")
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
            <DocNumHEA5>{mrn.get}</DocNumHEA5>
            <TraModAtBorHEA76>42</TraModAtBorHEA76>
          </HEAHEA>
          <MesSenMES3>ABCDEF1234/1234567890</MesSenMES3>
          <MesRecMES6>messageRecipient</MesRecMES6>
        </ie:CC313A>
      </tns:Body>
  // @formatter:on

  val errors: ValidationErrors = ValidationErrors(Seq(ValidationError("errText", "errType", "123", "errLocation")))

  "ValidationHandler" when {
    "passed a 315" when {
      "all valid" must {
        "return the payload" in {
          MockSchemaValidator.validate(SchemaTypeE315, IE315payload.toString()).returns(Right(IE315payload))
          MockRuleValidator315.validate(IE315payload).returns(Right(()))

          validationHandler.handleValidation(IE315payload.toString(), None) shouldBe Right(IE315payload)
        }
      }

      "xml is not schema valid" must {
        "return an error" in {
          MockSchemaValidator.validate(SchemaTypeE315, IE315payload.toString()).returns(Left(errors))

          validationHandler.handleValidation(IE315payload.toString(), None) shouldBe Left(ErrorWrapper(errors))
        }
      }

      "xml has business rule errors" must {
        "return an error" in {
          MockSchemaValidator.validate(SchemaTypeE315, IE315payload.toString()).returns(Right(IE315payload))
          MockRuleValidator315.validate(IE315payload).returns(Left(errors))

          validationHandler.handleValidation(IE315payload.toString(), None) shouldBe Left(ErrorWrapper(errors))
        }
      }
    }

    "passed a 313" when {
      "all valid" must {
        "return the payload" in {
          MockSchemaValidator.validate(SchemaTypeE313, IE313payload.toString()).returns(Right(IE313payload))
          MockRuleValidator313.validate(IE313payload).returns(Right(()))

          validationHandler.handleValidation(IE313payload.toString(), mrn) shouldBe Right(IE313payload)
        }
      }

      "xml is not schema valid" must {
        "return an error" in {
          MockSchemaValidator.validate(SchemaTypeE313, IE313payload.toString()).returns(Left(errors))

          validationHandler.handleValidation(IE313payload.toString(), mrn) shouldBe Left(ErrorWrapper(errors))
        }
      }

      "mrn does not match that in payload" must {
        "return an error" in {
          MockSchemaValidator.validate(SchemaTypeE313, IE313payload.toString()).returns(Right(IE313payload))

          validationHandler.handleValidation(IE313payload.toString(), Some("otherMrn")) shouldBe Left(
            ErrorWrapper(MRNMismatchError))
        }
      }

      "xml has business rule errors" must {
        "return an error" in {
          MockSchemaValidator.validate(SchemaTypeE313, IE313payload.toString()).returns(Right(IE313payload))
          MockRuleValidator313.validate(IE313payload).returns(Left(errors))

          validationHandler.handleValidation(IE313payload.toString(), mrn) shouldBe Left(ErrorWrapper(errors))
        }
      }
    }
  }

}
