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

package uk.gov.hmrc.entrydeclarationstore.utils

import org.scalamock.handlers.CallHandler
import org.scalamock.scalatest.MockFactory
import uk.gov.hmrc.entrydeclarationstore.logging.LoggingContext

trait MockPagerDutyLogger extends MockFactory {
  val mockPagerDutyLogger: PagerDutyLogger = stub[PagerDutyLogger]

  object MockPagerDutyLogger {
    def logEISFailure: CallHandler[Unit] =
      (mockPagerDutyLogger.logEISFailure(_: Int)(_: LoggingContext)).verify(*, *)

    def logEISTimeout: CallHandler[Unit] =
      (mockPagerDutyLogger.logEISTimeout()(_: LoggingContext)).verify(*)

    def logEISError: CallHandler[Unit] =
      (mockPagerDutyLogger.logEISError(_: Throwable)(_: LoggingContext)).verify(*, *)

    def logEISTrafficSwitchFlowStopped: CallHandler[Unit] =
      (mockPagerDutyLogger.logEISTrafficSwitchFlowStopped()(_: LoggingContext)).verify(*)

    def logEventFailure: CallHandler[Unit] =
      (mockPagerDutyLogger.logEventFailure(_: Int)(_: LoggingContext)).verify(*, *)

    def logEventError: CallHandler[Unit] =
      (mockPagerDutyLogger.logEventError(_: Throwable)(_: LoggingContext)).verify(*, *)
  }

}
