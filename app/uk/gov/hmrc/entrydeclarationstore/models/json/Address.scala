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

case class Address(
  streetAndNumber: String,
  city: String,
  postalCode: String,
  countryCode: String
)

object Address {
  def reader(
    streetAndNumberPath: String,
    cityPath: String,
    postalCodePath: String,
    countryCodePath: String
  ): XmlReader[Address] =
    (
      (__ \ streetAndNumberPath).read[String],
      (__ \ cityPath).read[String],
      (__ \ postalCodePath).read[String],
      (__ \ countryCodePath).read[String]
    ).mapN(apply)
  implicit val writes: Writes[Address] = Json.writes[Address]
}
