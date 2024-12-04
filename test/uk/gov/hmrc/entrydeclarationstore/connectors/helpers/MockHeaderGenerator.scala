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

package uk.gov.hmrc.entrydeclarationstore.connectors.helpers

import org.scalamock.handlers.CallHandler
import uk.gov.hmrc.entrydeclarationstore.utils.TestHarness
import uk.gov.hmrc.http.HeaderCarrier

trait MockHeaderGenerator extends TestHarness {
  val mockHeaderGenerator: HeaderGenerator = mock[HeaderGenerator]

  object MockHeaderGenerator {
    def headersForEIS(submissionId: String): CallHandler[Seq[(String, String)]] =
      (mockHeaderGenerator.headersForEIS(_: String)(_: HeaderCarrier)).expects(submissionId, *)
  }

}
