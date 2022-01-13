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

import java.lang

import groovy.lang.Closure
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.entrydeclarationstore.validation.business.Assert.CompilationContext
import uk.gov.hmrc.entrydeclarationstore.validation.business.AssertEvaluator.ContextHelper

import scala.xml.Node

class ContextHelperSpec extends AnyWordSpec {

  implicit val compilationContext: CompilationContext = CompilationContext("pXXX")
  // @formatter:off
  val xmlWrapper: XmlWrapper = XmlWrapper(
    <hello>
      <world>original</world><moon>

        made  Of  Cheese

      </moon>
      <population>123456789</population>
       <numbers><num>1.00</num><num>0.10</num><num>4356271829</num><seq>1</seq><seq>2</seq></numbers>
      <empty></empty>
      <uppercase>ABC</uppercase>
      <anycase>AaBbCc</anycase>
      <alphanumeric>abcABC123</alphanumeric>
      <equalStrings>v1</equalStrings>
      <someDistinctStrings>v1</someDistinctStrings>
      <equalStrings>v1</equalStrings>
      <someDistinctStrings>v1</someDistinctStrings>
      <equalStrings>v1</equalStrings>
      <someDistinctStrings>v2</someDistinctStrings>
      <someDistinctStrings> v2 </someDistinctStrings>
      <sameNumChildren><c1/><c2/></sameNumChildren>
      <sameNumChildren><c3/><c4/></sameNumChildren>
      <diffNumChildren><c1/><c2/></diffNumChildren>
      <diffNumChildren><c3/><c4/><c5/></diffNumChildren>
    </hello>)
  // @formatter:on

  val xml: Node                = <world>original</world>
  val contextNode: ContextNode = ContextNode(xmlWrapper, xml, "/hello/world")

  object TestableContextualAssertEvaluator extends ContextHelper(contextNode)

  "ContextualHelper" must {

    "trim works on a String with whitespace" in {
      TestableContextualAssertEvaluator.trim("/hello/moon") shouldBe "made  Of  Cheese"
    }

    "String based numbers are cast correctly" in {
      TestableContextualAssertEvaluator.number("/hello/population")               shouldBe 123456789
      TestableContextualAssertEvaluator.number("/hello/moon").doubleValue().isNaN shouldBe true
      TestableContextualAssertEvaluator.number("/hello/mars").doubleValue().isNaN shouldBe true
    }

    "String based integers are cast correctly" in {
      TestableContextualAssertEvaluator.intOrElse("/hello/population", 123) shouldBe 123456789
      TestableContextualAssertEvaluator.intOrElse("/hello/moon", 123)       shouldBe 123
      TestableContextualAssertEvaluator.intOrElse("/hello/mars", 123)       shouldBe 123
    }

    "Substring after on a string" in {
      TestableContextualAssertEvaluator.substringAfter("fishfinger", "h") shouldBe "finger"
    }

    "Get value gets the text from an xml" in {
      TestableContextualAssertEvaluator.getValue("/hello/world") shouldBe "original"
    }

    "Sum numbers in a path" in {
      TestableContextualAssertEvaluator.sum("/hello/numbers/num") shouldBe 4356271830.1
    }

    "counting nodes" in {
      TestableContextualAssertEvaluator.count("/hello/numbers/num") shouldBe 3
    }

    //TODO rename this countIf
    "count if nodes equal a value" in {
      TestableContextualAssertEvaluator.countEquals("/hello/numbers/num", "0.10") shouldBe 1
    }

    "tests that a node exists" in {
      TestableContextualAssertEvaluator.exists("/hello/world") shouldBe true
      TestableContextualAssertEvaluator.exists("/hello/mars")  shouldBe false
    }

    "tests that a node does not exist" in {
      TestableContextualAssertEvaluator.not("/hello/world") shouldBe false
      TestableContextualAssertEvaluator.not("/hello/mars")  shouldBe true
    }

    "count the distinct number of the number of children" in {
      TestableContextualAssertEvaluator.countDistinctChildCount("/hello/sameNumChildren") shouldBe 1
      TestableContextualAssertEvaluator.countDistinctChildCount("/hello/diffNumChildren") shouldBe 2
    }

    "count distinct values" in {
      TestableContextualAssertEvaluator.countDistinct("/hello/equalStrings", trim        = false) shouldBe 1
      TestableContextualAssertEvaluator.countDistinct("/hello/someDistinctStrings", trim = false) shouldBe 3
    }

    "count distinct values after trimming" in {
      TestableContextualAssertEvaluator.countDistinct("/hello/someDistinctStrings", trim = true) shouldBe 2
    }

    "exists in the xml with a value specified" in {
      TestableContextualAssertEvaluator.existsForAll("/hello", "world", "original") shouldBe true
    }

    "checks that the values of nodes are in order" in {
      TestableContextualAssertEvaluator.areIndices("/hello/numbers/seq") shouldBe true
    }

    "apply regex to a string" in {
      TestableContextualAssertEvaluator.applyRegex("/hello/population", "[\\d]+")       shouldBe true
      TestableContextualAssertEvaluator.applyRegex("/hello/world", "[\\d]+")            shouldBe false
      TestableContextualAssertEvaluator.applyRegex("/hello/empty", "[\\d]+")            shouldBe false
      TestableContextualAssertEvaluator.applyRegex("/hello/uppercase", "[A-Z]+")        shouldBe true
      TestableContextualAssertEvaluator.applyRegex("/hello/anycase", "[a-zA-Z]+")       shouldBe true
      TestableContextualAssertEvaluator.applyRegex("/hello/alphanumeric", "[\\w]+")     shouldBe true
      TestableContextualAssertEvaluator.applyRegex("/hello/alphanumeric", "[\\w]{1,9}") shouldBe true
      TestableContextualAssertEvaluator.applyRegex("/hello/alphanumeric", "[\\w]{1,5}") shouldBe false
    }

    "exists funtion" must {
      "return true when function always true" in {
        TestableContextualAssertEvaluator.exists("/hello/numbers/seq", toClosure((_, _) => true)) shouldBe true
      }

      "return false when function always false" in {
        TestableContextualAssertEvaluator.exists("/hello/numbers/seq", toClosure((_, _) => false)) shouldBe false
      }

      "return true when function always true once" in {
        TestableContextualAssertEvaluator.exists("/hello/numbers/seq", toClosure { (_, local) =>
          local.getValue(".") == "2"
        }) shouldBe true
      }

      "give access to local and current nodes" in {
        TestableContextualAssertEvaluator.exists("/hello/numbers/seq", toClosure { (current, _) =>
          current.getValue(".") == "original"
        }) shouldBe true
      }
    }
  }

  // Utility to convert a Scala function to a Groovy closure...
  def toClosure(f: (ContextHelper, ContextHelper) => Boolean): Closure[java.lang.Boolean] =
    new Closure[java.lang.Boolean](null) {
      protected def doCall(args: Array[ContextHelper]): lang.Boolean = f(args(0), args(1))
    }
}
