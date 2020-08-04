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

package uk.gov.hmrc.entrydeclarationstore.nrs
import cats.syntax.either._

import com.kenshoo.play.metrics.Metrics
import javax.inject.Inject
import uk.gov.hmrc.entrydeclarationstore.config.AppConfig
import uk.gov.hmrc.entrydeclarationstore.logging.LoggingContext
import uk.gov.hmrc.entrydeclarationstore.utils.{EventLogger, Timer}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class NRSService @Inject()(nrsConnector: NRSConnector, appConfig: AppConfig, override val metrics: Metrics)(
  implicit ec: ExecutionContext)
    extends Timer
    with EventLogger {

  def submit(
    nrsSubmission: NRSSubmission)(implicit hc: HeaderCarrier, lc: LoggingContext): Future[Option[NRSResponse]] =
    timeFuture("NRS Submission", "nrs.submission") {
      if (appConfig.nrsEnabled) {
        nrsConnector.submit(nrsSubmission).map(_.toOption)
      } else {
        Future.successful(None)
      }
    }
}
