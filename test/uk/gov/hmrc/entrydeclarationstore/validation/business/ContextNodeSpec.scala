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

package uk.gov.hmrc.entrydeclarationstore.validation.business

import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.WordSpec

class ContextNodeSpec extends WordSpec {

  "ContextNode" when {

    "getting all context nodes" must {
      val xml =
        // @formatter:off
          <a>
            <b>
              <c/>
              <c/>
            </b>
          </a>
      // @formatter:on

      val xmlWrapper = XmlWrapper(xml)

      val xmlns =
        // @formatter:off
        <ns:a>
          <ns:b>
            <c/>
            <c/>
          </ns:b>
        </ns:a>
      // @formatter:on

      val xmlnsWrapper = XmlWrapper(xmlns)

      "work for context that is a single node" in {
        ContextNode.allFor(xmlWrapper, Path.parseAbsolute("/a/b")) shouldBe
          Seq(
            ContextNode(xmlWrapper, (xml \ "b").head, "/a[1]/b[1]")
          )
      }

      "work for context that is array of nodes" in {
        ContextNode.allFor(xmlWrapper, Path.parseAbsolute("/a/b/c")) shouldBe
          Seq(
            ContextNode(xmlWrapper, (xml \ "b" \ "c").head, "/a[1]/b[1]/c[1]"),
            ContextNode(xmlWrapper, (xml \ "b" \ "c")(1), "/a[1]/b[1]/c[2]")
          )
      }

      "work for context that is whole xml" in {
        ContextNode.allFor(xmlWrapper, Path.parseAbsolute("/a")) shouldBe
          Seq(
            ContextNode(xmlWrapper, xml.head, "/a[1]")
          )
      }

      "work when xml has namespaces" in {
        ContextNode.allFor(xmlnsWrapper, Path.parseAbsolute("/ns:a/ns:b")) shouldBe
          Seq(
            ContextNode(xmlnsWrapper, (xmlns \ "b").head, "/ns:a[1]/ns:b[1]")
          )
      }
    }
  }

  "LocalNode" when {
    "applying paths" when {

      val xml =
        // @formatter:off
        <a>
          <a1>a1value</a1>
          <a2>a2value</a2>
          <b>
            <b1>b1value</b1>
            <c>
              <c1>c11value</c1>
              <c2>c12value</c2>
              <d>d1value</d>
            </c>
            <c>
              <c1>c21value</c1>
              <c2>c22value</c2>
              <d>d2value</d>
            </c>
            <c>
              <!--reuse same value deliberately-->
              <c1>c11value</c1>
              <c2>c32value</c2>
            </c>
          </b>
        </a>
    // @formatter:on

      "the path is a child" must {
        val localNode = LocalNode(XmlWrapper(xml), (xml \ "b").head)

        "return the child's text value" in {
          localNode(Path.parse("b1")).map(_.text) shouldBe Seq("b1value")
        }

        "return empty if the child is missing" in {
          localNode(Path.parse("x1")) shouldBe Nil
        }

        "return all if there are multiple children" in {
          localNode(Path.parse("c/c1")).map(_.text) shouldBe Seq("c11value", "c21value", "c11value")
        }
      }

      "the path is '.' (the context node) and the context is a single node" must {
        val localNode = LocalNode(XmlWrapper(xml), (xml \ "b" \ "b1").head)

        "return the text value" in {
          localNode(Path.parse(".")).map(_.text) shouldBe Seq("b1value")
        }
      }

      "the path is absolute" must {
        val unusedContextNode = <x></x>
        val context           = LocalNode(XmlWrapper(xml), unusedContextNode)

        "return the text value if present" in {
          context(Path.parse("/a/a1")).map(_.text) shouldBe Seq("a1value")
        }

        "return empty if missing" in {
          context(Path.parse("/a/x1")) shouldBe Nil
        }

        "return all if there are multiple children" in {
          context(Path.parse("/a/b/c/c1")).map(_.text) shouldBe Seq("c11value", "c21value", "c11value")
        }
      }

      "the path is a parent" must {
        val localNode = LocalNode(XmlWrapper(xml), (xml \ "b" \ "b1").head)

        "return the text value if present" in {
          localNode(Path.parse("../../a1")).map(_.text) shouldBe Seq("a1value")
        }

        "allow going up to the root" in {
          localNode(Path.parse("../../../a/a1")).map(_.text) shouldBe Seq("a1value")
        }

        "return empty if leaf missing" in {
          localNode(Path.parse("../../x1")) shouldBe Nil
        }

        "return empty if path goes up beyond root" in {
          localNode(Path.parse("../../../../../x")) shouldBe Nil
        }

        "return all if multiple" in {
          localNode(Path.parse("../c/c1")).map(_.text) shouldBe Seq("c11value", "c21value", "c11value")
        }

        "differentiate parents for equal nodes" in {
          val xmlWrapper = XmlWrapper(xml)
          ContextNode
            .allFor(xmlWrapper, Path.parseAbsolute("/a/b/c/c1"))
            .flatMap(context => context(Path.parse("../c2")).map(_.text)) shouldBe Seq(
            "c12value",
            "c22value",
            "c32value")
        }
      }
    }
  }
}
