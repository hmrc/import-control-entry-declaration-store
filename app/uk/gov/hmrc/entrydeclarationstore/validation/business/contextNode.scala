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

import uk.gov.hmrc.entrydeclarationstore.validation.business.LocalNode.BaseLocalNodeImpl

import scala.xml.{Elem, Node, NodeSeq}

/**
  * Represents a node when a path has been applied to a ContextNode or other LocalNode
  */
trait LocalNode {
  def apply(path: String): Seq[LocalNode] = apply(Path.parse(path))

  def apply(path: Path): Seq[LocalNode]

  def children: Seq[LocalNode]

  def text: String
}

object LocalNode {
  def apply(xml: XmlWrapper, contextNode: Node): LocalNode =
    LocalNodeImpl(xml, contextNode)

  private[business] def find(nodeSeq: NodeSeq, label: String): Seq[Node] =
    for {
      node <- nodeSeq.to(LazyList)
      c    <- node.child.to(LazyList) if c.label == label
    } yield c

  private[business] trait BaseLocalNodeImpl extends LocalNode {

    protected def xml: XmlWrapper

    protected def contextNode: Node

    def apply(path: Path): Seq[LocalNode] = {
      val newNodes = path match {
        case Path.Current        => Seq(contextNode)
        case path: Path.Relative => applyRelativePath(xml, contextNode, path)
        case path: Path.Absolute => applyAbsolutePath(xml, path)
      }
      newNodes.map(LocalNode.LocalNodeImpl(xml, _))
    }

    def text: String = contextNode.text

    def children: Seq[LocalNode] = contextNode.child.collect { case e: Elem => e }.map(LocalNode.LocalNodeImpl(xml, _)).toSeq

    private def applyRelativePath(root: XmlWrapper, xml: Node, path: Path.Relative) =
      for {
        ancestor <- root.findAncestor(path.numGenerations, xml).to(LazyList)
        element  <- applyPathElements(ancestor, path.pathElements)
      } yield element

    private def applyAbsolutePath(xml: XmlWrapper, path: Path.Absolute) =
      applyPathElements(xml.root, path.pathElements)

    private def applyPathElements(from: Node, pathElements: Seq[Path.Element]) =
      pathElements.foldLeft(LazyList(from)) { (acc, element) =>
        acc.flatMap(node => find(node, element.name))
      }
  }

  private[business] case class LocalNodeImpl(protected val xml: XmlWrapper, protected val contextNode: Node)
      extends BaseLocalNodeImpl

}

/**
  * Represents the current node when processing a rule
  */
trait ContextNode extends LocalNode {
  def location: Location
}

object ContextNode {
  def apply(xml: XmlWrapper, contextNode: Node, location: Location): ContextNode =
    ContextNodeImpl(xml, contextNode, location)

  private[business] case class ContextNodeImpl(
    protected val xml: XmlWrapper,
    protected val contextNode: Node,
    location: Location)
      extends BaseLocalNodeImpl
      with ContextNode

  /**
    * Get all context nodes for a given context path.
    *
    * @param xml the root xml (which context paths are applied to)
    */
  def allFor(xml: XmlWrapper, path: Path.Absolute): Seq[ContextNode] =
    applyAbsolutePathWithLocations(xml, path)
      .map {
        case (loc, node) => ContextNodeImpl(xml, node, loc)
      }

  private def applyAbsolutePathWithLocations(xml: XmlWrapper, path: Path.Absolute): Seq[(Location, Node)] =
    path.pathElements.foldLeft(Seq(("", xml.root))) { (acc, element) =>
      acc.flatMap {
        case (loc, node) =>
          val children = LocalNode.find(node, element.name)
          children.zipWithIndex.map { case (child, i) => (s"$loc/${element.qualifiedName}[${i + 1}]", child) }
      }
    }
}
