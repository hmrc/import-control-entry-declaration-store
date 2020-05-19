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

package uk.gov.hmrc.entrydeclarationstore.models.json

import cats.syntax.all._
import com.lucidchart.open.xtract.XmlReader._
import com.lucidchart.open.xtract.{XmlReader, __}
import play.api.libs.json.{Json, Writes}

case class Package(
  kindOfPackages: String,
  numberOfPackages: Option[String],
  numberOfPieces: Option[String],
  marks: Option[String],
  marksLanguage: Option[String]
)

object Package {
  implicit val reader: XmlReader[Package] = (
    (__ \ "KinOfPacGS23").read[String],
    (__ \ "NumOfPacGS24").read[String].optional,
    (__ \ "NumOfPieGS25").read[String].optional,
    (__ \ "MarNumOfPacGSL21").read[String].optional,
    (__ \ "MarNumOfPacGSL21LNG").read[String].optional
  ).mapN(apply)
  implicit val writes: Writes[Package] = Json.writes[Package]
}
