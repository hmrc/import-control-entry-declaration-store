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
import uk.gov.hmrc.entrydeclarationstore.utils.ReaderUtils

case class Goods(
  numberOfItems: Int,
  numberOfPackages: Option[Int],
  grossMass: Option[String],
  seals: Option[Seq[Seal]],
  goodsItems: Option[Seq[GoodsItem]]
)
object Goods extends ReaderUtils {
  implicit val reader: XmlReader[Goods] = (
    (__ \ "HEAHEA" \ "TotNumOfIteHEA305").read[Int],
    (__ \ "HEAHEA" \ "TotNumOfPacHEA306").read[Int].optional,
    (__ \ "HEAHEA" \ "TotGroMasHEA307").read[String].optional,
    (__ \ "SEAID529").read[Seq[Seal]].mapToNoneIfEmpty,
    (__ \ "GOOITEGDS").read[Seq[GoodsItem]].optional
  ).mapN(apply)
  implicit val writes: Writes[Goods] = Json.writes[Goods]
}
