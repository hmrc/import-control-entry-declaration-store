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
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.AsyncTestSuite
import uk.gov.hmrc.entrydeclarationstore.validation.ValidationErrors

import scala.xml.NodeSeq

trait MockRuleValidator extends AsyncTestSuite with AsyncMockFactory {
  val mockRuleValidator: RuleValidator = mock[RuleValidator]

  case class MockRuleValidatorImpl(mockRuleValidator: RuleValidator) {
    def validate(payload: NodeSeq): CallHandler[Either[ValidationErrors, Unit]] =
      mockRuleValidator.validate _ expects payload
  }

  val MockRuleValidator: MockRuleValidatorImpl = MockRuleValidatorImpl(mockRuleValidator)
}
