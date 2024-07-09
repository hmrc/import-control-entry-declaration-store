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

package uk.gov.hmrc.entrydeclarationstore.connectors

import org.apache.pekko.actor.Scheduler
import org.apache.pekko.pattern.CircuitBreakerOpenException
import play.api.http.Status
import play.api.http.Status._
import play.api.libs.json.Json
import uk.gov.hmrc.entrydeclarationstore.config.AppConfig
import uk.gov.hmrc.entrydeclarationstore.connectors.helpers.HeaderGenerator
import uk.gov.hmrc.entrydeclarationstore.logging.{ContextLogger, LoggingContext}
import uk.gov.hmrc.entrydeclarationstore.models.EntryDeclarationMetadata
import uk.gov.hmrc.entrydeclarationstore.trafficswitch.TrafficSwitch
import uk.gov.hmrc.entrydeclarationstore.utils.{Delayer, PagerDutyLogger, Retrying}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}

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
                                  client: HttpClientV2,
                                  trafficSwitch: TrafficSwitch,
                                  appConfig: AppConfig,
                                  pagerDutyLogger: PagerDutyLogger,
                                  headerGenerator: HeaderGenerator)(implicit val ec: ExecutionContext, val scheduler: Scheduler)
    extends EisConnector
    with Retrying
    with Delayer {
  val newUrl: String   = s"${appConfig.eisHost}${appConfig.eisNewEnsUrlPath}"
  val amendUrl: String = s"${appConfig.eisHost}${appConfig.eisAmendEnsUrlPath}"

  private lazy val numRetries = appConfig.eisRetries.length

  implicit val emptyHeaderCarrier: HeaderCarrier = HeaderCarrier()

  def submitMetadata(metadata: EntryDeclarationMetadata, bypassTrafficSwitch: Boolean)(
    implicit hc: HeaderCarrier,
    lc: LoggingContext): Future[Option[EISSendFailure]] =
    submit(bypassTrafficSwitch) {
      val isAmendment = metadata.movementReferenceNumber.isDefined
      val headers     = headerGenerator.headersForEIS(metadata.submissionId)(hc)

      retry(appConfig.eisRetries, retryCondition) { attempt =>
        logSending(attempt)

        if (isAmendment) putAmendment(metadata, headers) else postNew(metadata, headers)
      }
    }

  private def putAmendment(metadata: EntryDeclarationMetadata, headers: Seq[(String, String)])(
    implicit lc: LoggingContext): Future[HttpResponse] = {
    ContextLogger.info(s"sending PUT request to $amendUrl")
    client.put(url"$amendUrl").withBody(Json.toJson(metadata)).setHeader(headers : _*).execute[HttpResponse]
  }

  private def postNew(metadata: EntryDeclarationMetadata, headers: Seq[(String, String)])(
    implicit lc: LoggingContext): Future[HttpResponse] = {
    ContextLogger.info(s"sending POST request to $newUrl")
    client.post(url"$newUrl").withBody(Json.toJson(metadata)).setHeader(headers: _*).execute[HttpResponse]
  }

  private[connectors] def submit(bypassTrafficSwitch: Boolean)(code: => Future[HttpResponse])(
    implicit lc: LoggingContext): Future[Option[EISSendFailure]] =
    withTrafficSwitchIfRequired(bypassTrafficSwitch)(code)
      .map { response =>
        val status = response.status
        logSendResult(response, willRetry = false)

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

  private def retryCondition(implicit lc: LoggingContext): Try[HttpResponse] => Boolean = {
    case Success(response) =>
      val willRetry = appConfig.eisRetryStatusCodes.contains(response.status)

      // So we don't duplicate logging when not retrying...
      if (willRetry) logSendResult(response, willRetry = true)

      willRetry

    case _ => false
  }

  private def logSending(attempt: Int)(implicit lc: LoggingContext): Unit = {
    val retryInfo = if (attempt > 0) s" retry $attempt of $numRetries" else ""

    ContextLogger.info(s"Sending to EIS$retryInfo")
  }

  private def logSendResult(response: HttpResponse, willRetry: Boolean)(implicit lc: LoggingContext): Unit = {
    val retryInfo = if (willRetry) " will retry" else ""

    ContextLogger.info(s"Send to EIS returned status code: ${response.status}$retryInfo")
  }
}
