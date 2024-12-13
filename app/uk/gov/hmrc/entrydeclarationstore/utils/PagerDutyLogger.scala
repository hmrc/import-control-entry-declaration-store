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

import uk.gov.hmrc.entrydeclarationstore.exceptions.EISFailure
import uk.gov.hmrc.entrydeclarationstore.logging.{ContextLogger, LoggingContext}
import uk.gov.hmrc.http.HttpResponse

class PagerDutyLogger {
  def logEISFailure(response: HttpResponse)(implicit lc: LoggingContext): Unit =
    ContextLogger.error(s"Submission failed with status ${response.status}", EISFailure(response.body))

  def logEISError(e: Throwable)(implicit lc: LoggingContext): Unit =
    ContextLogger.error(s"Submission failed with error", e)

  def logEISTimeout()(implicit lc: LoggingContext): Unit =
    ContextLogger.error(s"Submission timed out")

  def logEISTrafficSwitchFlowStopped()(implicit lc: LoggingContext): Unit =
    ContextLogger.error(s"TRAFFIC_SWITCH_SET_TO_NOT_FLOWING - submission failed")

  def logEventFailure(statusCode: Int)(implicit lc: LoggingContext): Unit =
    ContextLogger.error(s"Send event failed with status $statusCode")

  def logEventError(e: Throwable)(implicit lc: LoggingContext): Unit =
    ContextLogger.error(s"Send event failed with error", e)
}
