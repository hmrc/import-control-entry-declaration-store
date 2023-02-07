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

package uk.gov.hmrc.entrydeclarationstore.models.json

import cats.syntax.all._
import com.lucidchart.open.xtract.XmlReader._
import com.lucidchart.open.xtract.{XmlReader, __}
import play.api.libs.json.{Json, Writes}
import uk.gov.hmrc.entrydeclarationstore.utils.HasEmpty

// Note: amalgamation of two types: #/definitions/traderContactDetails and #/definitions/tin
case class Trader(
  name: Option[String],
  address: Option[Address],
  language: Option[String],
  eori: Option[String]
) extends HasEmpty {
  def isEmpty: Boolean =
    name.isEmpty && address.isEmpty && language.isEmpty && eori.isEmpty
}

object Trader {
  def reader(
    namePath: String,
    addressReader: XmlReader[Address],
    languagePath: String,
    eoriPath: String
  ): XmlReader[Trader] =
    (
      (__ \ namePath).read[String].optional,
      addressReader.optional,
      (__ \ languagePath).read[String].optional,
      (__ \ eoriPath).read[String].optional
    ).mapN(apply)

  implicit val writes: Writes[Trader] = Json.writes[Trader]
}
