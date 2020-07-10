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

package uk.gov.hmrc.entrydeclarationstore.repositories

import play.api.libs.json.{Format, JsValue, Json}
import uk.gov.hmrc.entrydeclarationstore.models.{EntryDeclarationModel, InstantFormatter}

import scala.concurrent.duration._

private[repositories] case class EntryDeclarationPersisted(
  submissionId: String,
  eori: String,
  correlationId: String,
  housekeepingAt: PersistableDateTime,
  payload: JsValue,
  mrn: Option[String],
  receivedDateTime: PersistableDateTime,
  eisSubmissionDateTime: Option[PersistableDateTime] = None
) {
  def toEntryDeclarationModel: EntryDeclarationModel =
    EntryDeclarationModel(
      submissionId          = submissionId,
      eori                  = eori,
      correlationId         = correlationId,
      payload               = payload,
      mrn                   = mrn,
      receivedDateTime      = receivedDateTime.toInstant,
      eisSubmissionDateTime = eisSubmissionDateTime.map(_.toInstant)
    )
}

private[repositories] object EntryDeclarationPersisted extends InstantFormatter {
  implicit val format: Format[EntryDeclarationPersisted] = Json.format[EntryDeclarationPersisted]

  def from(entryDeclarationModel: EntryDeclarationModel, defaultTtl: FiniteDuration): EntryDeclarationPersisted = {
    import entryDeclarationModel._

    val housekeepingAt = PersistableDateTime(receivedDateTime.toEpochMilli + defaultTtl.toMillis)
    EntryDeclarationPersisted(
      submissionId          = submissionId,
      eori                  = eori,
      correlationId         = correlationId,
      housekeepingAt        = housekeepingAt,
      payload               = payload,
      mrn                   = mrn,
      receivedDateTime      = PersistableDateTime(receivedDateTime),
      eisSubmissionDateTime = eisSubmissionDateTime.map(PersistableDateTime(_))
    )
  }
}
