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

package uk.gov.hmrc.entrydeclarationstore.nrs

import akka.actor.Scheduler
import play.api.http.Status
import uk.gov.hmrc.entrydeclarationstore.config.AppConfig
import uk.gov.hmrc.entrydeclarationstore.logging.{ContextLogger, LoggingContext}
import uk.gov.hmrc.entrydeclarationstore.utils.{Delayer, Retrying}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Success, Try}

trait NRSConnector {
  def submit(nrsSubmission: NRSSubmission)(
    implicit hc: HeaderCarrier,
    lc: LoggingContext): Future[Either[NRSSubmisionFailure, NRSResponse]]
}

@Singleton
class NRSConnectorImpl @Inject()(httpClient: HttpClient, appConfig: AppConfig)(
  implicit val scheduler: Scheduler,
  val ec: ExecutionContext)
    extends NRSConnector
    with Retrying
    with Delayer {

  private val url: String    = s"${appConfig.nrsBaseUrl}/submission"
  private val apiKey: String = appConfig.nrsApiKey

  def submit(nrsSubmission: NRSSubmission)(
    implicit hc: HeaderCarrier,
    lc: LoggingContext): Future[Either[NRSSubmisionFailure, NRSResponse]] = {

    val retryCondition: Try[Either[NRSSubmisionFailure, NRSResponse]] => Boolean = {
      case Success(Left(failure)) => failure.retryable
      case _                      => false
    }

    retry(appConfig.nrsRetries, retryCondition) { attemptNumber =>
      ContextLogger.info(s"Attempt $attemptNumber NRS submission: sending POST request to $url")

      httpClient
        .POST[NRSSubmission, HttpResponse](url, nrsSubmission, Seq("X-API-Key" -> apiKey))
        .map { response =>
          val status = response.status

          if (Status.isSuccessful(status)) {
            ContextLogger.info("NRS submission successful")
            Right(response.json.as[NRSResponse])
          } else {
            ContextLogger.warn(s"NRS submission failed with status $status")
            Left(NRSSubmisionFailure.ErrorResponse(status))
          }
        }
        .recover {
          case NonFatal(e) =>
            ContextLogger.error(s"NRS submission failed with exception", e)
            Left(NRSSubmisionFailure.ExceptionThrown)
        }
    }
  }
}
