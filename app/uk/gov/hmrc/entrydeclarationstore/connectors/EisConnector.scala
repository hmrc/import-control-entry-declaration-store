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
import akka.pattern.CircuitBreaker
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

import scala.concurrent.{ExecutionContext, Future, Promise}
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

  private val circuitBreaker = CircuitBreaker(
    scheduler,
    appConfig.eisCircuitBreakerMaxFailures,
    appConfig.eisCircuitBreakerCallTimeout,
    appConfig.eisCircuitBreakerResetTimeout)

  sealed trait Result

  object Result {

    case object Timeout extends Result

    case object Open extends Result

    case class ResponseReceived(status: Int) extends Result

  }

  // Replace default HttpReads[HttpResponse] so that we can fully control error handling
  implicit object ResultReads extends HttpReads[HttpResponse] {
    override def read(method: String, url: String, response: HttpResponse): HttpResponse = response
  }

  implicit val emptyHeaderCarrier: HeaderCarrier = HeaderCarrier()

  def submitMetadata(metadata: EntryDeclarationMetadata)(implicit hc: HeaderCarrier): Future[Option[EISSendFailure]] =
    withCircuitBreaker {

      val isAmendment = metadata.movementReferenceNumber.isDefined
      val headers     = headerGenerator.headersForEIS(metadata.submissionId)(hc)

      val result = if (isAmendment) putAmendment(metadata, headers) else postNew(metadata, headers)

      result.map(resp => Result.ResponseReceived(resp.status))
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

  private[connectors] def withCircuitBreaker(code: => Future[Result]): Future[Option[EISSendFailure]] = {

    val promise = Promise[Result]

    if (circuitBreaker.isOpen) {
      promise.trySuccess(Result.Open)
    } else {
      scheduler.scheduleOnce(appConfig.eisCircuitBreakerCallTimeout) {
        promise.trySuccess(Result.Timeout)
      }

      promise.completeWith(materialize(code))
    }

    promise.future
      .andThen(updateCircuitBreakerAndLog)
      .map {
        case Result.ResponseReceived(status) =>
          Logger.info(s"Send to EIS returned status code: $status")
          if (status == ACCEPTED) None else Some(EISSendFailure.ErrorResponse(status))
        case Result.Open    => Some(EISSendFailure.CircuitBreakerOpen)
        case Result.Timeout => Some(EISSendFailure.Timeout)
      }
      .recover {
        case NonFatal(_) => Some(EISSendFailure.ExceptionThrown)
      }
  }

  private val updateCircuitBreakerAndLog: PartialFunction[Try[Result], Unit] = {
    case Success(Result.ResponseReceived(status)) =>
      // Don't open for any 200s
      if (Status.isSuccessful(status)) {
        circuitBreaker.succeed()
      } else {
        circuitBreaker.fail()
        pagerDutyLogger.logEISFailure(status)
      }

    case Success(Result.Open) =>
      circuitBreaker.fail()
      pagerDutyLogger.logEISCircuitBreakerOpen()

    case Success(Result.Timeout) =>
      circuitBreaker.fail()
      pagerDutyLogger.logEISTimeout()

    case Failure(e) =>
      circuitBreaker.fail()
      pagerDutyLogger.logEISError(e)
  }

  // Converts code throwing exception to a failed future
  private def materialize[U](code: => Future[U]): Future[U] =
    try code
    catch {
      case NonFatal(t) => Future.failed(t)
    }

}
