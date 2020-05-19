/*
 * Copyright 2020 HM Revenue & Customs
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

sealed trait Path

object Path {

  case class Element(qualifiedName: String, namespace: Option[String], name: String)

  def parse(path: String): Path =
    if (path == ".") {
      Current
    } else if (path.startsWith("/")) {

      // Drop the leading '/' first...
      val pathElements = path.drop(1).split('/')

      Absolute(pathElements)
    } else {
      val (numGenerations, pathElements) = {
        val allPathElements = path.split('/')

        (allPathElements.takeWhile(_ == "..").length, allPathElements.dropWhile(_ == ".."))
      }

      Relative(numGenerations, pathElements)
    }

  def parseAbsolute(path: String): Path.Absolute =
    parse(path) match {
      case path: Absolute => path
      case _ =>
        throw new IllegalArgumentException(s"Not an absolute path: '$path'")
    }

  case class Absolute(rawPathElements: Seq[String]) extends Path {
    lazy val pathElements: Seq[Element] = rawPathElements.map(toElement)
  }

  case class Relative(numGenerations: Int, rawPathElements: Seq[String]) extends Path {
    lazy val pathElements: Seq[Element] = rawPathElements.map(toElement)
  }

  case object Current extends Path

  private def toElement(elementString: String): Element = {
    val idx = elementString.indexOf(':')

    if (idx < 0) {
      Element(elementString, None, elementString)
    } else {
      Element(elementString, Some(elementString.substring(0, idx)), elementString.substring(idx + 1))
    }
  }

}
