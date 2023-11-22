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

import scala.util.matching.Regex

case class Amendment(movementReferenceNumber: String, place: Option[String], language: Option[String], dateTime: String)

object Amendment {
  implicit val reader: XmlReader[Amendment] = (
    (__ \ "DocNumHEA5").read[String],
    (__ \ "AmdPlaHEA598").read[String].optional,
    (__ \ "AmdPlaHEA598LNG").read[String].optional,
    (__ \ "DatTimAmeHEA113").read[String]
  ).mapN((movementReferenceNumber, place, placeLanguage, datetime) =>
    Amendment(movementReferenceNumber, place, placeLanguage, datetimeFormatter(datetime)))
  implicit val writes: Writes[Amendment] = Json.writes[Amendment]
  val datetimeRegex: Regex               = "([0-9]{4})([0-9]{2})([0-9]{2})([0-9]{2})([0-9]{2})".r

  def datetimeFormatter(datetime: String): String = datetime match {
    case datetimeRegex(year, month, day, hour, minute) => s"$year-$month-${day}T$hour:$minute:00.000Z"
    case _ => datetime
  }
}
