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

package uk.gov.hmrc.entrydeclarationstore.validation.schema

import uk.gov.hmrc.entrydeclarationstore.utils.ResourceUtils

import javax.xml.XMLConstants
import javax.xml.validation.{Schema, SchemaFactory}

trait SchemaType {
  private[validation] val schema: Schema

  protected def schemaFor(xsdPath: String): Schema = {
    val schemaLang = XMLConstants.W3C_XML_SCHEMA_NS_URI
    val resource   = ResourceUtils.url(xsdPath)

    SchemaFactory.newInstance(schemaLang).newSchema(resource)
  }
}

case object SchemaTypeE313 extends SchemaType {
  private[validation] val schema = schemaFor("xsds/CC313A-v11-2.xsd")
}

case object SchemaTypeE315 extends SchemaType {
  private[validation] val schema = schemaFor("xsds/CC315A-v11-2.xsd")
}
