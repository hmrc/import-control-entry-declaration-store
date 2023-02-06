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

package uk.gov.hmrc.entrydeclarationstore.repositories

import java.time.Instant
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats.Implicits._
import play.api.libs.json.{Format, JsValue, Json}
import uk.gov.hmrc.entrydeclarationstore.models.{EisSubmissionState, EntryDeclarationModel}

import scala.concurrent.duration._

private[repositories] case class EntryDeclarationPersisted(
  submissionId: String,
  eori: String,
  correlationId: String,
  housekeepingAt: Instant,
  payload: JsValue,
  mrn: Option[String],
  receivedDateTime: Instant,
  eisSubmissionDateTime: Option[Instant],
  eisSubmissionState: EisSubmissionState
) {
  def toEntryDeclarationModel: EntryDeclarationModel =
    EntryDeclarationModel(
      submissionId          = submissionId,
      eori                  = eori,
      correlationId         = correlationId,
      payload               = payload,
      mrn                   = mrn,
      receivedDateTime      = receivedDateTime,
      eisSubmissionDateTime = eisSubmissionDateTime,
      eisSubmissionState    = eisSubmissionState
    )
}

private[repositories] object EntryDeclarationPersisted {
  import EisSubmissionState.jsonFormat
  implicit val format: Format[EntryDeclarationPersisted] = Json.format[EntryDeclarationPersisted]

  def from(entryDeclarationModel: EntryDeclarationModel, defaultTtl: FiniteDuration): EntryDeclarationPersisted = {
    import entryDeclarationModel._

    val housekeepingAt = receivedDateTime.plusMillis(defaultTtl.toMillis)

    EntryDeclarationPersisted(
      submissionId          = submissionId,
      eori                  = eori,
      correlationId         = correlationId,
      housekeepingAt        = housekeepingAt,
      payload               = payload,
      mrn                   = mrn,
      receivedDateTime      = receivedDateTime,
      eisSubmissionDateTime = eisSubmissionDateTime,
      eisSubmissionState    = eisSubmissionState
    )
  }
}
