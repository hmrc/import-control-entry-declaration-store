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

package uk.gov.hmrc.entrydeclarationstore.models

import play.api.libs.json.{Format, Json}

import java.time.Instant


case class DeclarationRejectionEnrichmenResult(eisSubmissionDateTime: Option[Instant])

object DeclarationRejectionEnrichmenResult {
  import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats.Implicits.jatInstantFormat
  implicit val jsonFormat: Format[DeclarationRejectionEnrichmenResult] = Json.format[DeclarationRejectionEnrichmenResult]
}

case class DeclarationRejectionEnrichment(eisSubmissionDateTime: Option[Instant])

object DeclarationRejectionEnrichment extends InstantFormatter {
  implicit val jsonFormat: Format[DeclarationRejectionEnrichment] = Json.format[DeclarationRejectionEnrichment]
}
