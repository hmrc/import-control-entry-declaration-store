/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.entrydeclarationstore.validation

import org.scalamock.handlers.CallHandler
import org.scalamock.scalatest.MockFactory
import uk.gov.hmrc.entrydeclarationstore.logging.LoggingContext
import uk.gov.hmrc.entrydeclarationstore.models.{ErrorWrapper, RawPayload}

import scala.xml.NodeSeq

trait MockValidationHandler extends MockFactory {
  val mockValidationHandler: ValidationHandler = mock[ValidationHandler]

  object MockValidationHandler {
    def handleValidation(
      payload: RawPayload,
      eori: String,
      mrn: Option[String]): CallHandler[Either[ErrorWrapper[_], NodeSeq]] =
      (mockValidationHandler
        .handleValidation(_: RawPayload, _: String, _: Option[String])(_: LoggingContext)) expects (payload, eori, mrn, *)
  }

}
