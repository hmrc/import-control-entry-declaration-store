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

case class IdentityOfMeansOfCrossingBorder(
  nationality: Option[String],
  identity: String,
  language: Option[String]
)

object IdentityOfMeansOfCrossingBorder {
  def reader(
    nationalityPath: String,
    identityPath: String,
    languagePath: String
  ): XmlReader[IdentityOfMeansOfCrossingBorder] =
    (
      (__ \ nationalityPath).read[String].optional,
      (__ \ identityPath).read[String],
      (__ \ languagePath).read[String].optional
    ).mapN(apply)

  implicit val writes: Writes[IdentityOfMeansOfCrossingBorder] = Json.writes[IdentityOfMeansOfCrossingBorder]
}
