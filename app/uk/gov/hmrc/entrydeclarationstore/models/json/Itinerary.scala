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
import uk.gov.hmrc.entrydeclarationstore.utils.ReaderUtils

case class Itinerary(
  modeOfTransportAtBorder: String,
  identityOfMeansOfCrossingBorder: Option[IdentityOfMeansOfCrossingBorder],
  transportChargesMethodOfPayment: Option[String],
  commercialReferenceNumber: Option[String],
  conveyanceReference: Option[String],
  loading: Option[Loading],
  countriesOfRouting: Option[Seq[String]],
  officeOfFirstEntry: OfficeOfFirstEntry,
  officesOfSubsequentEntry: Option[Seq[String]]
)

object Itinerary extends ReaderUtils {
  implicit val reader: XmlReader[Itinerary] = (
    (__ \ "HEAHEA" \ "TraModAtBorHEA76").read[String],
    (__ \ "HEAHEA")
      .read(
        IdentityOfMeansOfCrossingBorder
          .reader("NatOfMeaOfTraCroHEA87", "IdeOfMeaOfTraCroHEA85", "IdeOfMeaOfTraCroHEA85LNG"))
      .optional,
    (__ \ "HEAHEA" \ "TraChaMetOfPayHEA1").read[String].optional,
    (__ \ "HEAHEA" \ "ComRefNumHEA").read[String].optional,
    (__ \ "HEAHEA" \ "ConRefNumHEA").read[String].optional,
    (__ \ "HEAHEA")
      .read(Loading.reader("PlaLoaGOOITE334", "PlaLoaGOOITE334LNG", "PlaUnlGOOITE334", "CodPlUnHEA357LNG"))
      .mapToNoneIfEmpty,
    (__ \ "ITI" \ "CouOfRouCodITI1").read[Seq[String]].mapToNoneIfEmpty,
    (__ \ "CUSOFFFENT730").read[OfficeOfFirstEntry],
    (__ \ "CUSOFFSENT740" \ "RefNumSUBENR909").read[Seq[String]].mapToNoneIfEmpty
  ).mapN(apply)
  implicit val writes: Writes[Itinerary] = Json.writes[Itinerary]
}
