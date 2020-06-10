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

import java.time.Instant

import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._
import uk.gov.hmrc.entrydeclarationstore.models.{EntryDeclarationMetadata, InstantFormatter, MessageType, ReplayMetadata}

private[repositories] case class EntryDeclarationMetadataPersisted(
  submissionId: String,
  eori: String,
  correlationId: String,
  messageType: MessageType,
  modeOfTransport: String,
  receivedDateTime: Instant,
  movementReferenceNumber: Option[String]) {
  def toDomain: ReplayMetadata =
    ReplayMetadata(
      eori          = eori,
      correlationId = correlationId,
      metadata = EntryDeclarationMetadata(
        submissionId            = submissionId,
        messageType             = messageType,
        modeOfTransport         = modeOfTransport,
        receivedDateTime        = receivedDateTime,
        movementReferenceNumber = movementReferenceNumber
      )
    )
}

private[repositories] object EntryDeclarationMetadataPersisted extends InstantFormatter {
  implicit val reads: Reads[EntryDeclarationMetadataPersisted] = (
    (__ \ "submissionId").read[String] and
      (__ \ "eori").read[String] and
      (__ \ "correlationId").read[String] and
      (__ \ "payload" \ "metadata" \ "messageType").read[MessageType] and
      (__ \ "payload" \ "itinerary" \ "modeOfTransportAtBorder").read[String] and
      (__ \ "receivedDateTime").read[Instant] and
      (__ \ "mrn").readNullable[String]
  )(EntryDeclarationMetadataPersisted.apply _)
}
