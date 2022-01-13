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

import cats.syntax.all._
import com.lucidchart.open.xtract.XmlReader._
import com.lucidchart.open.xtract.{XmlReader, __}
import play.api.libs.json.{Json, Writes}
import uk.gov.hmrc.entrydeclarationstore.utils.ReaderUtils

case class GoodsItem(
  itemNumber: String,
  description: Option[String],
  descriptionLanguage: Option[String],
  grossMass: Option[String],
  transportChargesMethodOfPayment: Option[String],
  commercialReferenceNumber: Option[String],
  unDangerousGoodsCode: Option[String],
  loading: Option[Loading],
  documents: Option[Seq[Document]],
  specialMentions: Option[Seq[String]],
  consignor: Option[Trader],
  commodityCode: Option[String],
  consignee: Option[Trader],
  containers: Option[Seq[Container]],
  identityOfMeansOfCrossingBorder: Option[Seq[IdentityOfMeansOfCrossingBorder]],
  packages: Option[Seq[Package]],
  notifyParty: Option[Trader]
)

object GoodsItem extends ReaderUtils {
  implicit val reader: XmlReader[GoodsItem] = (
    (__ \ "IteNumGDS7").read[String],
    (__ \ "GooDesGDS23").read[String].optional,
    (__ \ "GooDesGDS23LNG").read[String].optional,
    (__ \ "GroMasGDS46").read[String].optional,
    (__ \ "MetOfPayGDI12").read[String].optional,
    (__ \ "ComRefNumGIM1").read[String].optional,
    (__ \ "UNDanGooCodGDI1").read[String].optional,
    __.read(Loading.reader("PlaLoaGOOITE333", "PlaLoaGOOITE333LNG", "PlaUnlGOOITE333", "PlaUnlGOOITE333LNG"))
      .mapToNoneIfEmpty,
    (__ \ "PRODOCDC2").read[Seq[Document]].mapToNoneIfEmpty,
    (__ \ "SPEMENMT2" \ "AddInfCodMT23").read[Seq[String]].mapToNoneIfEmpty,
    (__ \ "TRACONCO2")
      .read(
        Trader.reader(
          "NamCO27",
          Address.reader("StrAndNumCO222", "CitCO224", "PosCodCO223", "CouCO225"),
          "NADLNGGTCO",
          "TINCO259"))
      .mapToNoneIfEmpty,
    (__ \ "COMCODGODITM" \ "ComNomCMD1").read[String].optional,
    (__ \ "TRACONCE2")
      .read(
        Trader.reader(
          "NamCE27",
          Address.reader("StrAndNumCE222", "CitCE224", "PosCodCE223", "CouCE225"),
          "NADLNGGICE",
          "TINCE259"))
      .mapToNoneIfEmpty,
    (__ \ "CONNR2").read[Seq[Container]].mapToNoneIfEmpty,
    (__ \ "IDEMEATRAGI970")
      .read(
        seqXmlReader(
          IdentityOfMeansOfCrossingBorder.reader("NatIDEMEATRAGI973", "IdeMeaTraGIMEATRA971", "IdeMeaTraGIMEATRA972LNG")
        ))
      .mapToNoneIfEmpty,
    (__ \ "PACGS2").read[Seq[Package]].mapToNoneIfEmpty,
    (__ \ "PRTNOT640")
      .read(
        Trader.reader(
          "NamPRTNOT642",
          Address.reader("StrNumPRTNOT646", "CtyPRTNOT643", "PstCodPRTNOT644", "CouCodGINOT647"),
          "PRTNOT640LNG",
          "TINPRTNOT641"))
      .mapToNoneIfEmpty
  ).mapN(apply)
  implicit val writes: Writes[GoodsItem] = Json.writes[GoodsItem]
}
