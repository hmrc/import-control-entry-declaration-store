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

import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.entrydeclarationstore.validation.ValidationError
import uk.gov.hmrc.entrydeclarationstore.validation.business.Assert.CompilationContext

class AssertValidatorSpec extends AnyWordSpec {

  implicit val compilationContext: CompilationContext = CompilationContext("pXXX")

  "AssertValidator" must {
    "return None if the test is true" in {

      val assert = Assert("true", "msg", "localMsg", "8999")

      val validator = AssertValidator(assert)

      val context: ContextNode = new ContextNode {
        override def apply(path: Path): Nil.type = Nil

        override def children: Nil.type = Nil

        override def text: String = ???

        val location = "someLocation"
      }

      validator.validate(context) shouldBe None
    }

    "return an error if the test is false" in {

      val assert = Assert("false", "msg", "localMsg", "8999")

      val validator = AssertValidator(assert)

      val context: ContextNode = new ContextNode {
        override def apply(path: Path): Nil.type = Nil

        override def children: Nil.type = Nil

        override def text: String = ???

        val location = "someLocation"
      }

      validator.validate(context) shouldBe Some(ValidationError("msg", "business", "8999", "someLocation"))
    }

    "have access to functions and context" in {

      val assert = Assert(
        """
          |getValue("ignored").length() == 5
          |""".stripMargin,
        "msg",
        "localMsg",
        "8999")

      val validator = AssertValidator(assert)

      val child: ContextNode = new ContextNode {
        override def apply(path: Path): Nil.type = Nil

        override def children: Nil.type = Nil

        override def text = "value"

        val location = "someLocation"
      }

      val context: ContextNode = new ContextNode {
        override def apply(path: Path) = Seq(child)

        override def children = Seq(child)

        override def text: String = ???

        val location = "someLocation"
      }

      validator.validate(context) shouldBe None
    }
  }

}
