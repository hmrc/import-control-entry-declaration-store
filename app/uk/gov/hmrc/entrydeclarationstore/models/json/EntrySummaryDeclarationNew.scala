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

case class EntrySummaryDeclarationNew(
  submissionId: String,
  specificCircumstancesIndicator: Option[String],
  metadata: Metadata,
  declaration: Option[Declaration],
  parties: Parties,
  goods: GoodsNew,
  itinerary: Itinerary,
  amendment: Option[AmendmentNew]
)

object EntrySummaryDeclarationNew {

  implicit def reader(implicit input: InputParameters): XmlReader[EntrySummaryDeclarationNew] =
    (
      (__ \ "HEAHEA" \ "SpeCirIndHEA1").read[String].optional,
      __.read[Metadata],
      __.read[Declaration].optional,
      __.read[Parties],
      __.read[Goods],
      __.read[Itinerary],
      (__ \ "HEAHEA").read[Amendment].optional
      ).mapN(
      (
        specialCircummstancesIndicator: Option[String],
        metadata: Metadata,
        declaration: Option[Declaration],
        parties: Parties,
        goods: Goods,
        itinerary: Itinerary,
        amendment: Option[Amendment]
      ) =>
        EntrySummaryDeclarationNew(
          input.submissionId,
          specialCircummstancesIndicator,
          metadata,
          declaration,
          parties,
          goods,
          itinerary,
          amendment))

  implicit val writes: Writes[EntrySummaryDeclarationNew] = Json.writes[EntrySummaryDeclarationNew]
}



