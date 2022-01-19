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

import java.util
import scala.annotation.tailrec
import scala.xml.{Node, NodeSeq}

/**
  * Wrapper for an XML node that provides a map of nodes to parent nodes to enable efficient
  * ancestry traversal.
  *
  * @param xml
  */
case class XmlWrapper(xml: NodeSeq) {
  // Wrap with a root node so that the xpath for the top-level elements works properly
  // for absolute paths (otherwise e.g. if xml is say <a>...</a> a path of "/a" will not find anything)
  // and relative paths that go back to the root...
  // @formatter:off
  val root : Node = <root>{xml}</root>
  // @formatter:on

  private lazy val parents: util.IdentityHashMap[Node, Node] = {

    def parents(acc: util.IdentityHashMap[Node, Node], parent: Node): util.IdentityHashMap[Node, Node] =
      parent.child.foldLeft(acc) { (acc, c) =>
        acc.put(c, parent)
        parents(acc, c)
        acc
      }
    parents(new util.IdentityHashMap, root)
  }

  @tailrec
  final def findAncestor(generations: Int, node: Node): Option[Node] =
    if (generations == 0) {
      Some(node)
    } else {
      Option(parents.get(node)) match {
        case None         => None
        case Some(parent) => findAncestor(generations - 1, parent)
      }
    }
}
