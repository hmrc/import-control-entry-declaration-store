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
import uk.gov.hmrc.entrydeclarationstore.utils.HasEmpty

case class Loading(
  placeOfLoading: Option[String],
  loadingLanguage: Option[String],
  placeOfUnloading: Option[String],
  unloadingLanguage: Option[String]
) extends HasEmpty {
  def isEmpty: Boolean =
    placeOfLoading.isEmpty && loadingLanguage.isEmpty && placeOfUnloading.isEmpty && unloadingLanguage.isEmpty
}

object Loading {
  def reader(
    placeOfLoadingPath: String,
    loadingLanguagePath: String,
    placeOfUnloadingPath: String,
    unloadingLanguagePath: String
  ): XmlReader[Loading] =
    (
      (__ \ placeOfLoadingPath).read[String].optional,
      (__ \ loadingLanguagePath).read[String].optional,
      (__ \ placeOfUnloadingPath).read[String].optional,
      (__ \ unloadingLanguagePath).read[String].optional
    ).mapN(apply)

  implicit val writes: Writes[Loading] = Json.writes[Loading]
}
