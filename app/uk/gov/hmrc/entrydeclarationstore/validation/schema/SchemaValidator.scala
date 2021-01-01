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

import org.xml.sax._
import uk.gov.hmrc.entrydeclarationstore.validation.{ValidationError, ValidationErrors}

import java.io.StringReader
import javax.xml.parsers.{SAXParser, SAXParserFactory}
import scala.xml.factory.XMLLoader
import scala.xml.parsing.{FactoryAdapter, NoBindingFactoryAdapter}
import scala.xml.{Node, NodeSeq}

class SchemaValidator {

  trait LocationHolder {
    def locationAsString: String
  }

  trait LocationStoringContentHandler extends ContentHandler with LocationHolder {
    private var lastPoppedQname = Option.empty[(String, Int)]
    private var locationStack   = List.empty[(String, Int)]

    def locationAsString: String =
      locationStack.reverse
        .map {
          case (qname, idx) => s"$qname[$idx]"
        }
        .mkString("/", "/", "")

    abstract override def startElement(uri: String, localName: String, qName: String, atts: Attributes): Unit = {
      super.startElement(uri, localName, qName, atts)

      locationStack = lastPoppedQname match {
        case Some((lastQName, index)) =>
          if (qName == lastQName) (qName -> (index + 1)) :: locationStack else (qName -> 1) :: locationStack
        case None => (qName -> 1) :: locationStack
      }
    }

    abstract override def endElement(uri: String, localName: String, qName: String): Unit = {
      super.endElement(uri, localName, qName)

      lastPoppedQname = popLocation
    }

    private def popLocation: Option[(String, Int)] = {
      val (popped, newStack) = locationStack match {
        case x :: xs => (Some(x), xs)
        case _       => (None, locationStack)
      }

      locationStack = newStack
      popped
    }
  }

  trait ErrorCollector extends ErrorHandler {
    self: LocationHolder =>

    var errors = Vector.empty[ValidationError]

    // (Note we ignore warning(...) callback)
    override def error(exception: SAXParseException): Unit =
      addError(exception)

    override def fatalError(exception: SAXParseException): Unit =
      addError(exception)

    private def addError(exception: SAXParseException): Unit =
      errors :+= ValidationError(exception, locationAsString)
  }

  def validate(schemaType: SchemaType, payload: String): Either[ValidationErrors, NodeSeq] = {
    val factory = SAXParserFactory.newInstance()

    factory.setNamespaceAware(true)
    factory.setSchema(schemaType.schema)
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)

    val saxParser = factory.newSAXParser()

    val handler = new NoBindingFactoryAdapter with LocationStoringContentHandler with ErrorCollector

    try {
      val elem = new XMLLoader[Node] {
        override def parser: SAXParser = saxParser

        override def adapter: FactoryAdapter = handler

      }.load(new InputSource(new StringReader(payload)))

      if (handler.errors.isEmpty) {
        Right(elem)
      } else {
        Left(ValidationErrors(handler.errors))
      }
    } catch {
      case se: SAXParseException =>
        //Error handler already has the fault
        Left(ValidationErrors(handler.errors))
    }
  }
}
