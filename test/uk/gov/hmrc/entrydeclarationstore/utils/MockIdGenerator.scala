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

package uk.gov.hmrc.entrydeclarationstore.utils

import org.scalamock.handlers.{CallHandler, CallHandler1}
import org.scalamock.scalatest.MockFactory
import org.scalatest.TestSuite
import uk.gov.hmrc.entrydeclarationstore.reporting.ClientInfo

trait MockIdGenerator extends TestSuite with MockFactory {
  val mockIdGenerator: IdGenerator = mock[IdGenerator]

  object MockIdGenerator {

    def generateCorrelationIdFor(clientInfo: ClientInfo): CallHandler1[ClientInfo, String] = {
      (mockIdGenerator.generateCorrelationIdFor(_: ClientInfo)).expects(clientInfo)
    }

    def generateSubmissionId: CallHandler[String] =
      (() => mockIdGenerator.generateSubmissionId).expects ()

    def generateUuid(): CallHandler[String] =
      (() => mockIdGenerator.generateUuid).expects()
  }
}
