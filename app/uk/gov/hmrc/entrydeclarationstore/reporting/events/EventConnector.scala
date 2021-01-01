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

package uk.gov.hmrc.entrydeclarationstore.reporting.events

import play.api.http.Status._
import uk.gov.hmrc.entrydeclarationstore.config.AppConfig
import uk.gov.hmrc.entrydeclarationstore.logging.LoggingContext
import uk.gov.hmrc.entrydeclarationstore.utils.PagerDutyLogger
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

trait EventConnector {
  def sendEvent(event: Event)(implicit hc: HeaderCarrier, lc: LoggingContext): Future[Unit]
}

@Singleton
class EventConnectorImpl @Inject()(client: HttpClient, appConfig: AppConfig, pagerDutyLogger: PagerDutyLogger)(
  implicit executionContext: ExecutionContext)
    extends EventConnector {
  val url: String = s"${appConfig.eventsHost}/import-control/event"

  def sendEvent(event: Event)(implicit hc: HeaderCarrier, lc: LoggingContext): Future[Unit] =
    client
      .POST[Event, HttpResponse](url, event)
      .map(response =>
        response.status match {
          case CREATED => ()
          case code    => pagerDutyLogger.logEventFailure(code)
      })
      .recover {
        case NonFatal(e) => pagerDutyLogger.logEventError(e)
      }
}
