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

import org.scalatest.Assertion
import uk.gov.hmrc.entrydeclarationstore.models.RawPayload
import uk.gov.hmrc.entrydeclarationstore.utils.ResourceUtils
import uk.gov.hmrc.play.test.UnitSpec

import scala.util.Random
import scala.xml._
import scala.xml.transform.{RewriteRule, RuleTransformer}

/**
  * Tests that re-written schemas for E315 & E313 are equivalent to the originals
  */
class SchemaEquivalenceSpec extends UnitSpec {
  val schemaValidator = new SchemaValidator

  "E315" must {
    val resource = ResourceUtils.url("xmls/CC315A-schemaValidSample-v11-1.xml")

    val xml = XML.load(resource)

    val schema = SchemaTypeE315
    val schemaLegacy: SchemaType = new SchemaType {
      private[validation] val schema = schemaFor("xsds/CC315A-v11-1.xsd") // <-- FIXME change to old schema resource
    }

    testEquivalenceWith(xml, schema, schemaLegacy)
  }

  "E313" must {
    val resource = ResourceUtils.url("xmls/CC313A-schemaValidSample-v11-1.xml")

    val xml = XML.load(resource)

    val schema = SchemaTypeE313
    val schemaLegacy: SchemaType = new SchemaType {
      private[validation] val schema = schemaFor("xsds/CC313A-v11-1.xsd") // <-- FIXME change to old schema resource
    }

    testEquivalenceWith(xml, schema, schemaLegacy)
  }

  def testEquivalenceWith(xml: Elem, schema: SchemaType, schemaLegacy: SchemaType): Unit = {
    val labels = allLabels(xml)

    def assertSameFor(message: String)(xml: NodeSeq): Assertion = {
      val result1 = schemaValidator.validate(schema, RawPayload(xml))
      val result2 = schemaValidator.validate(schemaLegacy, RawPayload(xml))
      withClue(s"Scenario: $message:\n")(result1 shouldBe result2)
    }

    def transform(n: Node)(rewriteRule: RewriteRule): Seq[Node] =
      new RuleTransformer(rewriteRule).transform(n)

    def assertSameAlTransformed(transformDesc: String)(elementRewriteRule: String => RewriteRule): Unit =
      labels.foreach { label =>
        assertSameFor(s"'$label' is $transformDesc")(transform(xml)(elementRewriteRule(label)))
      }

    "be valid for both schemas" in {
      val result1 = schemaValidator.validate(schema, RawPayload(xml))
      val result2 = schemaValidator.validate(schemaLegacy, RawPayload(xml))

      result1 shouldBe result2
      result1 shouldBe a[Right[_, _]]
    }

    "validate equivalently for both schemas" when {
      "elements are removed" in {
        assertSameAlTransformed("removed")(replicate(0))
      }

      "elements are replicated" in {
        for (num <- (2 to 10) ++ (20 to 100 by 10)) assertSameAlTransformed(s"replicated $num times")(replicate(num))
      }

      "elements are renamed" in {
        assertSameAlTransformed("renamed")(rename("XXX"))
      }

      "elements texts are changed randomly" in {
        for (len <- 0 to 100) {
          val value = Random.alphanumeric.take(len).mkString
          assertSameFor(s"all text changed to $value")(transform(xml)(alterAllText(value)))
        }
      }

      "elements are reordered" in {
        assertSameFor("elements reordered")(transform(xml)(shuffleElements))
      }
    }
  }

  private def replicate(num: Int)(label: String) =
    new RewriteRule {
      override def transform(n: Node): Seq[Node] =
        n match {
          case n: Elem => if (n.label == label) Seq.fill(num)(n) else n
          case _       => n
        }
    }

  private def rename(newLabel: String)(label: String) = new RewriteRule {
    override def transform(n: Node): Seq[Node] =
      n match {
        case n: Elem => if (n.label == label) n.copy(label = newLabel) else n
        case _       => n
      }
  }

  private def alterAllText(newText: String) = new RewriteRule {
    override def transform(n: Node): Seq[Node] =
      n match {
        case t: Text if t.text.nonEmpty => Seq(Text(newText))
        case _                          => n
      }
  }

  private def shuffleElements = new RewriteRule {
    override def transform(n: Node): Seq[Node] =
      n match {
        case e: Elem => Seq(e.copy(child = Random.shuffle(e.child.toList)))
        case o: Node => Seq(o)
      }
  }

  private def allLabels(nodeSeq: NodeSeq) = {

    def elements(nodes: List[Node]): List[String] =
      nodes.foldLeft(List.empty[String]) { (a, n) =>
        n match {
          case e: Elem => e.label :: a ::: elements(n.child.toList)
          case _       => a ::: elements(n.child.toList)
        }
      }

    elements(nodeSeq.toList)
  }
}
