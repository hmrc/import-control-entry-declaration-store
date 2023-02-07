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

import uk.gov.hmrc.entrydeclarationstore.validation.ValidationErrors
import uk.gov.hmrc.entrydeclarationstore.validation.business.Assert.CompilationContext

import scala.xml.NodeSeq

trait RuleValidator {
  def validate(xml: NodeSeq): Either[ValidationErrors, Unit]
}

class RuleValidatorImpl(elementBase: Option[String], rules: Seq[Rule]) extends RuleValidator {

  case class RuleEvaluator(
    path: Path.Absolute,
    ruleName: String,
    assertValidators: Seq[AssertValidator]
  )

  val ruleEvaluators: Seq[RuleEvaluator] = {
    rules.map { rule =>
      implicit val compilationContext: CompilationContext = CompilationContext(rule.name)
      val assertValidators                                = rule.asserts.map(AssertValidator(_))

      val element = elementBase.getOrElse("") + rule.element
      RuleEvaluator(Path.parseAbsolute(element), rule.name, assertValidators)
    }
  }

  def validate(xml: NodeSeq): Either[ValidationErrors, Unit] = {

    val xmlWrapper = XmlWrapper(xml)

    val errors = for {
      ruleEvaluator   <- ruleEvaluators
      context         <- ContextNode.allFor(xmlWrapper, ruleEvaluator.path)
      assertValidator <- ruleEvaluator.assertValidators
      error           <- assertValidator.validate(context)
    } yield error

    if (errors.isEmpty) Right(()) else Left(ValidationErrors(errors))
  }
}
