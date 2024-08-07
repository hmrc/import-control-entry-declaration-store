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

package uk.gov.hmrc.entrydeclarationstore.orchestrators

import org.scalamock.handlers.CallHandler
import org.scalamock.scalatest.MockFactory
import org.scalatest.TestSuite
import uk.gov.hmrc.entrydeclarationstore.models.{ReplayInitializationResult, ReplayResult, ReplayTrigger}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

trait MockReplayOrchestrator extends TestSuite with MockFactory {
  val mockReplayOrchestrator: ReplayOrchestrator = mock[ReplayOrchestrator]

  object MockReplayOrchestrator {
    def startReplay(limit: Option[Int], trigger: ReplayTrigger = ReplayTrigger.Manual): CallHandler[(Future[ReplayInitializationResult], Future[ReplayResult])] =
      (mockReplayOrchestrator.startReplay(_: Option[Int], _: ReplayTrigger)(_: HeaderCarrier)).expects(limit, trigger, *)
  }

}
