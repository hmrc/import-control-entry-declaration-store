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

package uk.gov.hmrc.entrydeclarationstore.validation

import cats.syntax.all._
import com.lucidchart.open.xtract.XmlReader._
import com.lucidchart.open.xtract.{XmlReader, __}
import uk.gov.hmrc.entrydeclarationstore.utils.{ReaderUtils, SchemaErrorCodeMapper, XmlFormatConfig, XmlFormats}

import scala.xml.{Node, SAXParseException}

case class ValidationError(errorText: String, errorType: String, errorNumber: String, errorLocation: String)

object ValidationError {
  def apply(exception: SAXParseException, errorLocation: String): ValidationError = {
    val exceptionMessage = exception.getMessage
    val colon            = exceptionMessage.indexOf(':')

    val (errorNumber, error) =
      if (colon < 0) {
        (SchemaErrorCodeMapper.catchAllErrorCode, exceptionMessage)
      } else {
        val saxErrorCode = exceptionMessage.take(colon)
        val message      = exceptionMessage.drop(colon + 1).trim

        (SchemaErrorCodeMapper.getErrorCodeFromParserFailure(saxErrorCode), message)
      }

    ValidationError(
      errorText     = error,
      errorType     = "schema",
      errorNumber   = errorNumber.toString,
      errorLocation = errorLocation
    )
  }

  implicit val reader: XmlReader[ValidationError] = (
    (__ \ "Text").read[String],
    (__ \ "Type").read[String],
    (__ \ "Number").read[String],
    (__ \ "Location").read[String]
  ).mapN(apply)

  implicit val ordering: Ordering[ValidationError] = Ordering.by(err => (err.errorLocation, err.errorNumber))
}

case class ValidationErrors(errors: Seq[ValidationError])

object ValidationErrors extends ReaderUtils {
  implicit def xmlFormats(implicit xmlFormatConfig: XmlFormatConfig): XmlFormats[ValidationErrors] =
    new XmlFormats[ValidationErrors] {
      override def toXml(a: ValidationErrors): Node = {
        val errorCount = xmlFormatConfig.responseMaxErrors min a.errors.size

        // @formatter:off
      <err:ErrorResponse xmlns:err="http://www.govtalk.gov.uk/CM/errorresponse" xmlns:dsl="http://decisionsoft.com/rim/errorExtension" SchemaVersion="2.0">
        <err:Application>
          <err:MessageCount>{errorCount}</err:MessageCount>
        </err:Application>
        {a.errors.take(errorCount).map { error =>
        <err:Error>
          <err:RaisedBy>HMRC</err:RaisedBy>
          <err:Number>{error.errorNumber}</err:Number>
          <err:Type>{error.errorType}</err:Type>
          <err:Text>{error.errorText}</err:Text>
          <err:Location>{error.errorLocation}</err:Location>
        </err:Error>
      }}
      </err:ErrorResponse>
    // @formatter:on
      }
    }

  implicit val reader: XmlReader[ValidationErrors] = (__ \ "Error").read[Seq[ValidationError]].map(apply)

}
