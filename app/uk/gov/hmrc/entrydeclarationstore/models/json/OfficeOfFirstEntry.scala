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

package uk.gov.hmrc.entrydeclarationstore.models.json

import cats.syntax.all._
import com.lucidchart.open.xtract.XmlReader._
import com.lucidchart.open.xtract.{XmlReader, __}
import play.api.libs.json.{Json, Writes}

import scala.util.matching.Regex

case class OfficeOfFirstEntry(
  reference: String,
  expectedDateTimeOfArrival: String
)

object OfficeOfFirstEntry {
  implicit val reader: XmlReader[OfficeOfFirstEntry] = (
    (__ \ "RefNumCUSOFFFENT731").read[String],
    (__ \ "ExpDatOfArrFIRENT733").read[String]
  ).mapN((reference, expectedDateTimeOfArrival) => OfficeOfFirstEntry(reference, datetime(expectedDateTimeOfArrival)))

  implicit val writes: Writes[OfficeOfFirstEntry] = Json.writes[OfficeOfFirstEntry]
  val datetimeRegex: Regex                        = "([0-9]{4})([0-9]{2})([0-9]{2})([0-9]{2})([0-9]{2})".r

  def datetime(datetime: String): String = datetime match {
    case datetimeRegex(year, month, day, hour, minute) => s"$year-$month-${day}T$hour:$minute:00.000Z"
  }
}
