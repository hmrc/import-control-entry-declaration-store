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
import uk.gov.hmrc.entrydeclarationstore.utils.ReaderUtils

case class Parties(
  consignor: Option[Trader],
  consignee: Option[Trader],
  declarant: Trader,
  representative: Option[Trader],
  carrier: Option[Trader],
  notifyParty: Option[Trader]
)

object Parties extends ReaderUtils {
  implicit val reader: XmlReader[Parties] = (
    (__ \ "TRACONCO1")
      .read(
        Trader.reader(
          "NamCO17",
          Address.reader("StrAndNumCO122", "CitCO124", "PosCodCO123", "CouCO125"),
          "NADLNGCO",
          "TINCO159"))
      .mapToNoneIfEmpty,
    (__ \ "TRACONCE1")
      .read(
        Trader.reader(
          "NamCE17",
          Address.reader("StrAndNumCE122", "CitCE124", "PosCodCE123", "CouCE125"),
          "NADLNGCE",
          "TINCE159"))
      .mapToNoneIfEmpty,
    (__ \ "PERLODSUMDEC").read(
      Trader.reader(
        "NamPLD1",
        Address.reader("StrAndNumPLD1", "CitPLD1", "PosCodPLD1", "CouCodPLD1"),
        "PERLODSUMDECLNG",
        "TINPLD1")),
    (__ \ "TRAREP")
      .read(
        Trader.reader(
          "NamTRE1",
          Address.reader("StrAndNumTRE1", "CitTRE1", "PosCodTRE1", "CouCodTRE1"),
          "TRAREPLNG",
          "TINTRE1"))
      .mapToNoneIfEmpty,
    (__ \ "TRACARENT601")
      .read(
        Trader.reader(
          "NamTRACARENT604",
          Address.reader("StrNumTRACARENT607", "CtyTRACARENT603", "PstCodTRACARENT606", "CouCodTRACARENT605"),
          "TRACARENT601LNG",
          "TINTRACARENT602"
        ))
      .mapToNoneIfEmpty,
    (__ \ "NOTPAR670")
      .read(
        Trader.reader(
          "NamNOTPAR672",
          Address.reader("StrNumNOTPAR673", "CitNOTPAR674", "PosCodNOTPAR676", "CouCodNOTPAR675"),
          "NOTPAR670LNG",
          "TINNOTPAR671"))
      .mapToNoneIfEmpty
  ).mapN(apply)
  implicit val writes: Writes[Parties] = Json.writes[Parties]
}
