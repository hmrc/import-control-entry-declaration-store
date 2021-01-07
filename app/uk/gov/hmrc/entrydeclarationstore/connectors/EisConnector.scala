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

package uk.gov.hmrc.entrydeclarationstore.connectors

import akka.actor.Scheduler
import akka.pattern.CircuitBreakerOpenException
import play.api.http.Status
import play.api.http.Status._
import uk.gov.hmrc.entrydeclarationstore.config.AppConfig
import uk.gov.hmrc.entrydeclarationstore.connectors.helpers.HeaderGenerator
import uk.gov.hmrc.entrydeclarationstore.logging.{ContextLogger, LoggingContext}
import uk.gov.hmrc.entrydeclarationstore.models.EntryDeclarationMetadata
import uk.gov.hmrc.entrydeclarationstore.trafficswitch.TrafficSwitch
import uk.gov.hmrc.entrydeclarationstore.utils.{Delayer, PagerDutyLogger, Retrying}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future, TimeoutException}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

trait EisConnector {
  def submitMetadata(metadata: EntryDeclarationMetadata, bypassTrafficSwitch: Boolean)(
    implicit hc: HeaderCarrier,
    lc: LoggingContext): Future[Option[EISSendFailure]]
}

@Singleton
class EisConnectorImpl @Inject()(
  client: HttpClient,
  trafficSwitch: TrafficSwitch,
  appConfig: AppConfig,
  pagerDutyLogger: PagerDutyLogger,
  headerGenerator: HeaderGenerator)(implicit val ec: ExecutionContext, val scheduler: Scheduler)
    extends EisConnector
    with Retrying
    with Delayer {
  val newUrl: String   = s"${appConfig.eisHost}${appConfig.eisNewEnsUrlPath}"
  val amendUrl: String = s"${appConfig.eisHost}${appConfig.eisAmendEnsUrlPath}"

  implicit val emptyHeaderCarrier: HeaderCarrier = HeaderCarrier()

  def submitMetadata(metadata: EntryDeclarationMetadata, bypassTrafficSwitch: Boolean)(
    implicit hc: HeaderCarrier,
    lc: LoggingContext): Future[Option[EISSendFailure]] =
    submit(bypassTrafficSwitch) {
      val isAmendment = metadata.movementReferenceNumber.isDefined
      val headers     = headerGenerator.headersForEIS(metadata.submissionId)(hc)
      ContextLogger.info(s"sending to EIS")
      retry(appConfig.eisRetries, retryCondition) { attemptNumber =>
        if (isAmendment) putAmendment(metadata, headers, attemptNumber) else postNew(metadata, headers, attemptNumber)
      }
    }

  private def putAmendment(metadata: EntryDeclarationMetadata, headers: Seq[(String, String)], attemptNumber: Int)(
    implicit lc: LoggingContext): Future[HttpResponse] = {
    ContextLogger.info(s"sending PUT request to $amendUrl")
    client
      .PUT[EntryDeclarationMetadata, HttpResponse](amendUrl, metadata, headers)
  }

  private def postNew(metadata: EntryDeclarationMetadata, headers: Seq[(String, String)], attemptNumber: Int)(
    implicit lc: LoggingContext): Future[HttpResponse] = {
    ContextLogger.info(s"sending POST request to $newUrl")
    client
      .POST[EntryDeclarationMetadata, HttpResponse](newUrl, metadata, headers)
  }

  private[connectors] def submit(bypassTrafficSwitch: Boolean)(code: => Future[HttpResponse])(
    implicit lc: LoggingContext): Future[Option[EISSendFailure]] =
    withTrafficSwitchIfRequired(bypassTrafficSwitch)(code)
      .map { response =>
        val status = response.status
        ContextLogger.info(s"Send to EIS returned status code: $status")

        if (status == ACCEPTED) {
          None
        } else {
          pagerDutyLogger.logEISFailure(status)
          Some(EISSendFailure.ErrorResponse(status))
        }
      }
      .recover {
        case _: CircuitBreakerOpenException =>
          pagerDutyLogger.logEISTrafficSwitchFlowStopped()
          Some(EISSendFailure.TrafficSwitchNotFlowing)

        case _: TimeoutException =>
          pagerDutyLogger.logEISTimeout()
          Some(EISSendFailure.Timeout)

        case NonFatal(e) =>
          pagerDutyLogger.logEISError(e)
          Some(EISSendFailure.ExceptionThrown)
      }

  private def withTrafficSwitchIfRequired(bypass: Boolean)(code: => Future[HttpResponse]) =
    if (bypass) code else trafficSwitch.withTrafficSwitch(code, trafficSwitchFailureFunction)

  private val trafficSwitchFailureFunction: Try[HttpResponse] => Boolean = {
    case Success(response) =>
      // 400s should be submission-specific issues and not turn off the
      // traffic switch (esp since their replays would also likely fail
      // and affect ongoing submissions).
      !(Status.isSuccessful(response.status) || response.status == BAD_REQUEST)

    case Failure(_) => true
  }

  private val retryCondition: Try[HttpResponse] => Boolean = {
    case Success(response) => appConfig.eisRetryStatusCodes.contains(response.status)
    case _                 => false
  }
}
