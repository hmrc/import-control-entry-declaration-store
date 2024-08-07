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

package uk.gov.hmrc.entrydeclarationstore.services

import org.scalamock.handlers.CallHandler
import org.scalamock.scalatest.MockFactory
import org.scalatest.TestSuite
import uk.gov.hmrc.entrydeclarationstore.models.{ErrorWrapper, RawPayload, SuccessResponse}
import uk.gov.hmrc.entrydeclarationstore.reporting.ClientInfo
import uk.gov.hmrc.http.HeaderCarrier

import java.time.Instant
import uk.gov.hmrc.entrydeclarationstore.models.json.InputParameters

import scala.concurrent.Future

trait MockEntryDeclarationStore extends TestSuite with MockFactory {
  val mockEntryDeclarationStore: EntryDeclarationStore = mock[EntryDeclarationStore]

  object MockEntryDeclarationStore {
    def handleSubmission(
      eori: String,
      rawPayload: RawPayload,
      mrn: Option[String],
      receivedDateTime: Instant,
      clientInfo: ClientInfo,
      submissionId: String,
      correlationId: String,
      input: InputParameters): CallHandler[Future[Either[ErrorWrapper[_], SuccessResponse]]] =
      (mockEntryDeclarationStore
        .handleSubmission(_: String, _: RawPayload, _: Option[String], _: Instant, _: ClientInfo, _: String, _: String, _ : InputParameters)(_: HeaderCarrier))
        .expects(eori, rawPayload, mrn, receivedDateTime, clientInfo, submissionId, correlationId,  input, *)
  }

}
