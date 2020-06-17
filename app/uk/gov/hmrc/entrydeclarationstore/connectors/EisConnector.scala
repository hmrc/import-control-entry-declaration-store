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

package uk.gov.hmrc.entrydeclarationstore.connectors

import akka.actor.Scheduler
import akka.pattern.{CircuitBreaker, CircuitBreakerOpenException}
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.http.Status
import play.api.http.Status._
import uk.gov.hmrc.entrydeclarationstore.config.AppConfig
import uk.gov.hmrc.entrydeclarationstore.connectors.helpers.HeaderGenerator
import uk.gov.hmrc.entrydeclarationstore.models.EntryDeclarationMetadata
import uk.gov.hmrc.entrydeclarationstore.utils.PagerDutyLogger
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future, TimeoutException}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

trait EisConnector {
  def submitMetadata(metadata: EntryDeclarationMetadata)(implicit hc: HeaderCarrier): Future[Option[EISSendFailure]]
}

@Singleton
class EisConnectorImpl @Inject()(
  client: HttpClient,
  appConfig: AppConfig,
  pagerDutyLogger: PagerDutyLogger,
  headerGenerator: HeaderGenerator)(implicit scheduler: Scheduler, executionContext: ExecutionContext)
    extends EisConnector {
  val newUrl: String   = s"${appConfig.eisHost}${appConfig.eisNewEnsUrlPath}"
  val amendUrl: String = s"${appConfig.eisHost}${appConfig.eisAmendEnsUrlPath}"

  val circuitBreaker: CircuitBreaker = CircuitBreaker(
    scheduler,
    appConfig.eisCircuitBreakerMaxFailures,
    appConfig.eisCircuitBreakerCallTimeout,
    appConfig.eisCircuitBreakerResetTimeout)

  // This replaces the default HttpReads[HttpResponse] so that we can fully control error handling
  implicit object ResultReads extends HttpReads[HttpResponse] {
    override def read(method: String, url: String, response: HttpResponse): HttpResponse = response
  }

  implicit val emptyHeaderCarrier: HeaderCarrier = HeaderCarrier()

  def submitMetadata(metadata: EntryDeclarationMetadata)(implicit hc: HeaderCarrier): Future[Option[EISSendFailure]] =
    withCircuitBreaker {
      val isAmendment = metadata.movementReferenceNumber.isDefined
      val headers     = headerGenerator.headersForEIS(metadata.submissionId)(hc)
      Logger.info(s"submissionId is ${metadata.submissionId}")
      if (isAmendment) putAmendment(metadata, headers) else postNew(metadata, headers)
    }

  private def putAmendment(metadata: EntryDeclarationMetadata, headers: Seq[(String, String)]): Future[HttpResponse] = {
    Logger.info(s"sending PUT request to $amendUrl")
    client
      .PUT(amendUrl, metadata, headers)
  }

  private def postNew(metadata: EntryDeclarationMetadata, headers: Seq[(String, String)]): Future[HttpResponse] = {
    Logger.info(s"sending POST request to $newUrl")
    client
      .POST(newUrl, metadata, headers)
  }

  private[connectors] def withCircuitBreaker(code: => Future[HttpResponse]): Future[Option[EISSendFailure]] =
    circuitBreaker
      .withCircuitBreaker(code, failureFunction)
      .map { response =>
        val status = response.status
        Logger.info(s"Send to EIS returned status code: $status")

        if (status == ACCEPTED) {
          None
        } else {
          pagerDutyLogger.logEISFailure(status)
          Some(EISSendFailure.ErrorResponse(status))
        }
      }
      .recover {
        case _: CircuitBreakerOpenException =>
          pagerDutyLogger.logEISCircuitBreakerOpen()
          Some(EISSendFailure.CircuitBreakerOpen)

        case _: TimeoutException =>
          pagerDutyLogger.logEISTimeout()
          Some(EISSendFailure.Timeout)

        case NonFatal(e) =>
          pagerDutyLogger.logEISError(e)
          Some(EISSendFailure.ExceptionThrown)
      }

  private val failureFunction: Try[HttpResponse] => Boolean = {
    case Success(response) =>
      // 400s should be submission-specific issues and not open
      // circuit breaker (esp since their replays would also likely fail
      // and affect ongoing submissions).
      !(Status.isSuccessful(response.status) || response.status == BAD_REQUEST)

    case Failure(_) => true
  }
}
