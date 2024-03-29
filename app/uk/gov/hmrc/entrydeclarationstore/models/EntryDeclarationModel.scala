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

import play.api.libs.json.{Format, JsValue, Json}

import java.time.Instant

case class EntryDeclarationModel(
  correlationId: String,
  submissionId: String,
  eori: String,
  payload: JsValue,
  mrn: Option[String],
  receivedDateTime: Instant,
  eisSubmissionDateTime: Option[Instant] = None,
  eisSubmissionState: EisSubmissionState = EisSubmissionState.NotSent)

object EntryDeclarationModel extends InstantFormatter{
  import EisSubmissionState.jsonFormat
  implicit val format: Format[EntryDeclarationModel] = Json.format[EntryDeclarationModel]
}
