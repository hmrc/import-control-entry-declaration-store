/*
 * Copyright 2022 HM Revenue & Customs
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
import uk.gov.hmrc.entrydeclarationstore.validation.{ValidationError, ValidationErrors}

import scala.xml.Elem

class RuleValidatorSpec extends AnyWordSpec {

  val xml: Elem =
    <a>
      <b>
        <b1>b1value1</b1>
        <b1>b1value2</b1>
        <b2>b2value1</b2>
      </b>
    </a>

  "RuleValidator" when {
    "no validation errors" must {
      "return Right" in {
        val validator = new RuleValidatorImpl(
          None,
          Seq(
            Rule("/a/b", "p1", Seq(Assert("""count("b1") == 2""", "msg1", "local", "101")))
          ))

        validator.validate(xml) shouldBe Right(())
      }
    }

    "a single error" must {
      "return a Left containing the error" in {
        val validator = new RuleValidatorImpl(
          None,
          Seq(
            Rule("/a", "p2", Seq(Assert("""getValue("b/b2") == "b2value1" """, "msg1", "local", "101"))),
            Rule("/a/b", "p2", Seq(Assert("""count("b1") == 3""", "msg2", "local", "102")))
          )
        )

        validator.validate(xml) shouldBe Left(
          ValidationErrors(Seq(ValidationError("msg2", "business", "102", "/a[1]/b[1]"))))
      }
    }

    "context is multiple elements" must {
      "validate each element" in {
        val validator = new RuleValidatorImpl(
          None,
          Seq(
            Rule("/a/b/b1", "p2", Seq(Assert("""getValue(".") == "xxx"""", "msg1", "local", "101")))
          ))

        validator.validate(xml) shouldBe Left(
          ValidationErrors(
            Seq(
              ValidationError("msg1", "business", "101", "/a[1]/b[1]/b1[1]"),
              ValidationError("msg1", "business", "101", "/a[1]/b[1]/b1[2]")
            )))
      }
    }

    "multiple errors from the same rule" must {
      "return all errors" in {
        val validator = new RuleValidatorImpl(
          None,
          Seq(
            Rule(
              "/a/b",
              "p1",
              Seq(
                Assert("""count("b1") == 3""", "msg1", "local", "101"),
                Assert("""getValue("b2") == "xxx"""", "msg2", "local", "102")
              ))))

        validator.validate(xml) shouldBe Left(
          ValidationErrors(
            Seq(
              ValidationError("msg1", "business", "101", "/a[1]/b[1]"),
              ValidationError("msg2", "business", "102", "/a[1]/b[1]")
            )))
      }
    }

    "multiple errors from the multiple rules" must {
      "return all errors" in {
        val validator = new RuleValidatorImpl(
          None,
          Seq(
            Rule("/a/b", "p1", Seq(Assert("""count("b1") == 3""", "msg1", "local", "101"))),
            Rule("/a/b", "p2", Seq(Assert("""getValue("b2") == "xxx"""", "msg2", "local", "102")))
          )
        )

        validator.validate(xml) shouldBe Left(
          ValidationErrors(
            Seq(
              ValidationError("msg1", "business", "101", "/a[1]/b[1]"),
              ValidationError("msg2", "business", "102", "/a[1]/b[1]")
            )))
      }
    }
  }
}
