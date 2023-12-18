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

package uk.gov.hmrc.entrydeclarationstore.validation.business

import org.scalamock.handlers.CallHandler
import org.scalamock.scalatest.MockFactory
import uk.gov.hmrc.entrydeclarationstore.validation.ValidationErrors

import scala.xml.NodeSeq

trait MockRuleValidator extends MockFactory {
  val mockRuleValidator313: RuleValidator = mock[RuleValidator]
  val mockRuleValidator315: RuleValidator = mock[RuleValidator]
  val mockRuleValidator313New: RuleValidator = mock[RuleValidator]
  val mockRuleValidator315New: RuleValidator = mock[RuleValidator]

  case class MockRuleValidator(mockRuleValidator: RuleValidator) {
    def validate(payload: NodeSeq): CallHandler[Either[ValidationErrors, Unit]] =
      mockRuleValidator.validate _ expects payload
  }

  val MockRuleValidator313: MockRuleValidator = MockRuleValidator(mockRuleValidator313)
  val MockRuleValidator315: MockRuleValidator = MockRuleValidator(mockRuleValidator315)
  val MockRuleValidator313New: MockRuleValidator = MockRuleValidator(mockRuleValidator313)
  val MockRuleValidator315New: MockRuleValidator = MockRuleValidator(mockRuleValidator315)
}
