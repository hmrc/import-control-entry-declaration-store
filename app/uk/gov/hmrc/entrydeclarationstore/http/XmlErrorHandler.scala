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

package uk.gov.hmrc.entrydeclarationstore.http

import play.api.http.HttpErrorHandler
import play.api.http.Status._
import play.api.mvc.Results._
import play.api.mvc.{RequestHeader, Result}
import play.api.{Configuration, Logging}
import uk.gov.hmrc.auth.core.AuthorisationException
import uk.gov.hmrc.entrydeclarationstore.utils.XmlFormats._
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendHeaderCarrierProvider
import uk.gov.hmrc.play.bootstrap.config.HttpAuditEvent

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class XmlErrorHandler @Inject()(
  auditConnector: AuditConnector,
  httpAuditEvent: HttpAuditEvent,
  configuration: Configuration
)(implicit ec: ExecutionContext)
    extends HttpErrorHandler
    with BackendHeaderCarrierProvider with Logging {

  import httpAuditEvent.dataEvent

  /**
    * `upstreamWarnStatuses` is used to determine the log level for exceptions
    * relating to a HttpResponse. You can set this value in your config as
    * a list of integers representing response codes that should log at a
    * warning level rather an error level.
    *
    * e.g. bootstrap.errorHandler.warnOnly.statusCodes=[400,404,502]
    *
    * This is used to reduce the number of noise the number of duplicated alerts
    * for a microservice.
    */
  protected val upstreamWarnStatuses: Seq[Int] =
    configuration.getOptional[Seq[Int]]("bootstrap.errorHandler.warnOnly.statusCodes").getOrElse(Nil)

  override def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] =
    Future.successful {
      implicit val headerCarrier: HeaderCarrier = hc(request)
      statusCode match {
        case NOT_FOUND =>
          auditConnector.sendEvent(
            dataEvent(
              eventType       = "ResourceNotFound",
              transactionName = "Resource Endpoint Not Found",
              request         = request,
              detail          = Map.empty
            )
          )
          NotFound(ErrorResponse(NOT_FOUND, "URI not found", requested = Some(request.path)).toXml)
        case BAD_REQUEST =>
          auditConnector.sendEvent(
            dataEvent(
              eventType       = "ServerValidationError",
              transactionName = "Request bad format exception",
              request         = request,
              detail          = Map.empty
            )
          )
          BadRequest(ErrorResponse(BAD_REQUEST, "bad request").toXml)
        case _ =>
          auditConnector.sendEvent(
            dataEvent(
              eventType       = "ClientError",
              transactionName = s"A client error occurred, status: $statusCode",
              request         = request,
              detail          = Map.empty
            )
          )
          Status(statusCode)(ErrorResponse(statusCode, message).toXml)
      }
    }

  override def onServerError(request: RequestHeader, ex: Throwable): Future[Result] = {
    implicit val headerCarrier: HeaderCarrier = hc(request)

    val message = s"! Internal server error, for (${request.method}) [${request.uri}] -> "
    val eventType = ex match {
      case _: NotFoundException      => "ResourceNotFound"
      case _: AuthorisationException => "ClientError"
      case _: JsValidationException  => "ServerValidationError"
      case _                         => "ServerInternalError"
    }

    val errorResponse = ex match {
      case e: AuthorisationException =>
        logger.error(message, e)
        ErrorResponse(UNAUTHORIZED, e.getMessage)
      case e: HttpException =>
        logException(e, e.responseCode)
        ErrorResponse(e.responseCode, e.getMessage)
      case e: Exception with UpstreamErrorResponse =>
        logException(e, e.statusCode)
        ErrorResponse(e.reportAs, e.getMessage)
      case e: Throwable =>
        logger.error(message, e)
        ErrorResponse(INTERNAL_SERVER_ERROR, e.getMessage)
    }

    auditConnector.sendEvent(
      dataEvent(
        eventType       = eventType,
        transactionName = "Unexpected error",
        request         = request,
        detail          = Map("transactionFailureReason" -> ex.getMessage)
      )
    )
    Future.successful(new Status(errorResponse.statusCode)(errorResponse.toXml))
  }

  private def logException(exception: Exception, responseCode: Int): Unit =
    if (upstreamWarnStatuses contains responseCode) {
      logger.warn(exception.getMessage, exception)
    } else {
      logger.error(exception.getMessage, exception)
    }
}
