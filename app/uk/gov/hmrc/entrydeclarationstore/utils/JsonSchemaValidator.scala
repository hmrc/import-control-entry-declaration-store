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

package uk.gov.hmrc.entrydeclarationstore.utils

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.networknt.schema.{Schema, SchemaRegistry, SpecificationVersion}
import play.api.libs.json.JsValue
import uk.gov.hmrc.entrydeclarationstore.logging.{ContextLogger, LoggingContext}
import uk.gov.hmrc.entrydeclarationstore.models.{ErrorWrapper, ServerError}

import java.io.FileInputStream
import scala.jdk.CollectionConverters.*

object JsonSchemaValidator {

  private val mapper: ObjectMapper = new ObjectMapper()
  
  private val schemaRegistry: SchemaRegistry =
    SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_4)

  val basePath: String = System.getProperty("user.dir")

  def validateJSONAgainstSchema(inputDoc: JsValue, schemaDoc: String = "conf/jsonschemas/EntrySummaryDeclaration.json")(
    using lc: LoggingContext): Either[ErrorWrapper[_], Unit] =
    try {
      val inputJson: JsonNode = mapper.readTree(inputDoc.toString())
      val schema: Schema      = loadSchema(schemaDoc)
      val errors              = schema.validate(inputJson).asScala.toSeq
      if (errors.nonEmpty) {
        ContextLogger.debug(s"Failed to validate $inputDoc: $errors")
        ContextLogger.error(s"Failed to validate JSON: $errors")
        Left(ErrorWrapper(ServerError))
      } else {
        Right(())
      }
    } catch {
      case e: Exception =>
        ContextLogger.debug(s"Failed to validate $inputDoc", e)
        ContextLogger.error(s"Failed to validate JSON", e)
        Left(ErrorWrapper(ServerError))
    }

  private def loadSchema(schemaDoc: String): Schema = {
    val schemaStream = new FileInputStream(s"$basePath/$schemaDoc")
    try schemaRegistry.getSchema(schemaStream)
    finally schemaStream.close()
  }
}
