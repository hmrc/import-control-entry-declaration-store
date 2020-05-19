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

import play.api.Logger

class PagerDutyLogger {
  def logEISFailure(statusCode: Int): Unit =
    Logger.error(s"Submission failed with status $statusCode")

  def logEISError(e: Throwable): Unit =
    Logger.error(s"Submission failed with error", e)

  def logEISTimeout(): Unit =
    Logger.error(s"Submission timed out")

  def logEISCircuitBreakerOpen(): Unit =
    Logger.error(s"Circuit breaker open - submission failed")

  def logEventFailure(statusCode: Int): Unit =
    Logger.error(s"Send event failed with status $statusCode")

  def logEventError(e: Throwable): Unit =
    Logger.error(s"Send event failed with error", e)
}
