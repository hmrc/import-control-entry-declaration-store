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

package uk.gov.hmrc.entrydeclarationstore.models.json

import com.lucidchart.open.xtract.{ParseFailure, ParseSuccess, PartialParseSuccess, XmlReader}
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.entrydeclarationstore.logging.{ContextLogger, LoggingContext}
import uk.gov.hmrc.entrydeclarationstore.models.{ErrorWrapper, ServerError}
import uk.gov.hmrc.entrydeclarationstore.utils.JsonSchemaValidator

import scala.xml.NodeSeq

class DeclarationToJsonConverter {
  def convertToJson(xml: NodeSeq, inputParameters: InputParameters)(
    implicit lc: LoggingContext): Either[ErrorWrapper[_], JsValue] =
    XmlReader.of(EntrySummaryDeclaration.reader(inputParameters)).read(xml) match {
      case ParseSuccess(entrySummaryDeclaration) => Right(Json.toJson(entrySummaryDeclaration))
      case ParseFailure(errors) =>
        ContextLogger.error("Failed to convert to JSON " + errors)
        Left(ErrorWrapper(ServerError))
      case PartialParseSuccess(_, errors) =>
        ContextLogger.error("Failed to convert to JSON (PartialParseSuccess) " + errors)
        Left(ErrorWrapper(ServerError))
    }

  def convertToModel(xml: NodeSeq, inputParameters: InputParameters)(
    implicit lc: LoggingContext): Either[ErrorWrapper[_], EntrySummaryDeclaration] =
    XmlReader.of(EntrySummaryDeclaration.reader(inputParameters)).read(xml) match {
      case ParseSuccess(entrySummaryDeclaration) => Right(entrySummaryDeclaration)
      case ParseFailure(errors) =>
        ContextLogger.error("Failed to convert to model " + errors)
        Left(ErrorWrapper(ServerError))
      case PartialParseSuccess(_, errors) =>
        ContextLogger.error("Failed to convert to model (PartialParseSuccess) " + errors)
        Left(ErrorWrapper(ServerError))
    }

  def validateJson(entrySummaryDeclaration: JsValue)(implicit lc: LoggingContext): Boolean =
    JsonSchemaValidator.validateJSONAgainstSchema(entrySummaryDeclaration)
}
