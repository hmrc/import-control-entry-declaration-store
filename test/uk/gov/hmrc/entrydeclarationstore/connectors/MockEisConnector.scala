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

package uk.gov.hmrc.entrydeclarationstore.connectors

import org.scalamock.handlers.CallHandler
import org.scalamock.scalatest.MockFactory
import uk.gov.hmrc.entrydeclarationstore.logging.LoggingContext
import uk.gov.hmrc.entrydeclarationstore.models.EntryDeclarationMetadata
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

trait MockEisConnector extends MockFactory {
  val mockEisConnector: EisConnector = mock[EisConnector]

  object MockEisConnector {
    def submitMetadata(
                        metadata: EntryDeclarationMetadata,
                        bypassTrafficSwitch: Boolean): CallHandler[Future[Option[EISSendFailure]]] =
      (mockEisConnector
        .submitMetadata(_: EntryDeclarationMetadata, _: Boolean)(_: HeaderCarrier, _: LoggingContext))
        .expects(metadata, bypassTrafficSwitch, *, *)
  }

}
