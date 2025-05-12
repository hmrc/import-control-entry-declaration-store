/*
 * Copyright 2025 HM Revenue & Customs
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

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.mockito.ArgumentMatchers.{any, eq => is}
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.mockito.MockitoSugar.mock
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{LoneElement, StreamlinedXml}
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{Configuration, Logger, LoggerLike}
import uk.gov.hmrc.auth.core.AuthorisationException
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.http.connector.AuditResult.Success
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.play.bootstrap.config.HttpAuditEvent

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random
import scala.xml.Elem

class XmlErrorHandlerSpec
    extends AnyWordSpec
    with ScalaFutures
    with LoneElement
    with Eventually
    with StreamlinedXml {

  import ExecutionContext.Implicits.global

  "onServerError" must {

    "convert a NotFoundException to NotFound response and audit the error" in new Setup {
      val notFoundException           = new NotFoundException("test")
      val createdDataEvent: DataEvent = DataEvent("auditSource", "auditType")
      Mockito
        .when(httpAuditEvent.dataEvent(
          eventType       = is("ResourceNotFound"),
          transactionName = is("Unexpected error"),
          request         = is(requestHeader),
          detail          = is(Map("transactionFailureReason" -> notFoundException.getMessage)),
          truncationLog   = any[uk.gov.hmrc.play.audit.model.TruncationLog]
        )(any[HeaderCarrier]))
        .thenReturn(createdDataEvent)

      val result: Future[Result] = xmlErrorHandler.onServerError(requestHeader, notFoundException)

      status(result)                              shouldEqual NOT_FOUND
      xml.XML.loadString(contentAsString(result)) should equal(<error>
          <statusCode>404</statusCode>
          <message>test</message>
        </error>)(after being streamlined[Elem])

      verify(auditConnector).sendEvent(is(createdDataEvent))(any[HeaderCarrier], any[ExecutionContext])
    }

    "convert an AuthorisationException to Unauthorized response and audit the error" in new Setup {
      val authorisationException: AuthorisationException = new AuthorisationException("reason") {}
      val createdDataEvent: DataEvent                    = DataEvent("auditSource", "auditType")
      Mockito
        .when(httpAuditEvent.dataEvent(
          eventType       = is("ClientError"),
          transactionName = is("Unexpected error"),
          request         = is(requestHeader),
          detail          = is(Map("transactionFailureReason" -> authorisationException.getMessage)),
          truncationLog   = any[uk.gov.hmrc.play.audit.model.TruncationLog]
        )(any[HeaderCarrier]))
        .thenReturn(createdDataEvent)

      val result: Future[Result] = xmlErrorHandler.onServerError(requestHeader, authorisationException)

      status(result)                              shouldEqual UNAUTHORIZED
      xml.XML.loadString(contentAsString(result)) should equal(<error>
          <statusCode>401</statusCode>
          <message>
            {authorisationException.getMessage}
          </message>
        </error>)(after being streamlined[Elem])

      verify(auditConnector).sendEvent(is(createdDataEvent))(any[HeaderCarrier], any[ExecutionContext])
    }

    "convert an Exception to InternalServerError and audit the error" in new Setup {
      val exception                   = new Exception("any application exception")
      val createdDataEvent: DataEvent = DataEvent("auditSource", "auditType")
      Mockito
        .when(httpAuditEvent.dataEvent(
          eventType       = is("ServerInternalError"),
          transactionName = is("Unexpected error"),
          request         = is(requestHeader),
          detail          = is(Map("transactionFailureReason" -> exception.getMessage)),
          truncationLog   = any[uk.gov.hmrc.play.audit.model.TruncationLog]
        )(any[HeaderCarrier]))
        .thenReturn(createdDataEvent)

      val result: Future[Result] = xmlErrorHandler.onServerError(requestHeader, exception)

      status(result)                              shouldEqual INTERNAL_SERVER_ERROR
      xml.XML.loadString(contentAsString(result)) should equal(<error>
          <statusCode>500</statusCode>
          <message>
            {exception.getMessage}
          </message>
        </error>)(after being streamlined[Elem])

      verify(auditConnector).sendEvent(is(createdDataEvent))(any[HeaderCarrier], any[ExecutionContext])
    }

    "convert a JsValidationException to InternalServerError and audit the error" in new Setup {
      val exception                   = new JsValidationException(GET, uri, classOf[Int], "json deserialization error")
      val createdDataEvent: DataEvent = DataEvent("auditSource", "auditType")
      Mockito
        .when(httpAuditEvent.dataEvent(
          eventType       = is("ServerValidationError"),
          transactionName = is("Unexpected error"),
          request         = is(requestHeader),
          detail          = is(Map("transactionFailureReason" -> exception.getMessage)),
          truncationLog   = any[uk.gov.hmrc.play.audit.model.TruncationLog]
        )(any[HeaderCarrier]))
        .thenReturn(createdDataEvent)

      val result: Future[Result] = xmlErrorHandler.onServerError(requestHeader, exception)

      status(result)                              shouldEqual INTERNAL_SERVER_ERROR
      xml.XML.loadString(contentAsString(result)) should equal(<error>
          <statusCode>500</statusCode>
          <message>
            {exception.getMessage}
          </message>
        </error>)(after being streamlined[Elem])

      verify(auditConnector).sendEvent(is(createdDataEvent))(any[HeaderCarrier], any[ExecutionContext])
    }

    "convert a HttpException to responseCode from the exception and audit the error" in new Setup {
      val responseCode: Int           = Random.nextInt()
      val exception                   = new HttpException("error message", responseCode)
      val createdDataEvent: DataEvent = DataEvent("auditSource", "auditType")
      Mockito
        .when(httpAuditEvent.dataEvent(
          eventType       = is("ServerInternalError"),
          transactionName = is("Unexpected error"),
          request         = is(requestHeader),
          detail          = is(Map("transactionFailureReason" -> exception.getMessage)),
          truncationLog   = any[uk.gov.hmrc.play.audit.model.TruncationLog]
        )(any[HeaderCarrier]))
        .thenReturn(createdDataEvent)

      val result: Future[Result] = xmlErrorHandler.onServerError(requestHeader, exception)

      status(result)                              shouldEqual responseCode
      xml.XML.loadString(contentAsString(result)) should equal(<error>
          <statusCode>
            {responseCode.toString}
          </statusCode>
          <message>
            {exception.getMessage}
          </message>
        </error>)(after being streamlined[Elem])

      verify(auditConnector).sendEvent(is(createdDataEvent))(any[HeaderCarrier], any[ExecutionContext])
    }

    "convert a UpstreamErrorResponse for 4xx to reportAs from the exception and audit the error" in new Setup {
      private def random4xxStatus = 400 + Random.nextInt(100)

      val reportAs: Int                    = random4xxStatus
      val exception: UpstreamErrorResponse = UpstreamErrorResponse("error message", random4xxStatus, reportAs)
      val createdDataEvent: DataEvent      = DataEvent("auditSource", "auditType")
      Mockito
        .when(httpAuditEvent.dataEvent(
          eventType       = is("ServerInternalError"),
          transactionName = is("Unexpected error"),
          request         = is(requestHeader),
          detail          = is(Map("transactionFailureReason" -> exception.getMessage)),
          truncationLog   = any[uk.gov.hmrc.play.audit.model.TruncationLog]
        )(any[HeaderCarrier]))
        .thenReturn(createdDataEvent)

      val result: Future[Result] = xmlErrorHandler.onServerError(requestHeader, exception)

      status(result)                              shouldEqual reportAs
      xml.XML.loadString(contentAsString(result)) should equal(<error>
          <statusCode>
            {reportAs.toString}
          </statusCode>
          <message>error message</message>
        </error>)(after being streamlined[Elem])

      verify(auditConnector).sendEvent(is(createdDataEvent))(any[HeaderCarrier], any[ExecutionContext])
    }

    "convert a UpstreamErrorResponse for 5xx to reportAs from the exception and audit the error" in new Setup {
      private def random5xxStatus = 500 + Random.nextInt(100)

      val reportAs: Int                    = random5xxStatus
      val exception: UpstreamErrorResponse = UpstreamErrorResponse("error message", random5xxStatus, reportAs)
      val createdDataEvent: DataEvent      = DataEvent("auditSource", "auditType")
      Mockito
        .when(httpAuditEvent.dataEvent(
          eventType       = is("ServerInternalError"),
          transactionName = is("Unexpected error"),
          request         = is(requestHeader),
          detail          = is(Map("transactionFailureReason" -> exception.getMessage)),
          truncationLog   = any[uk.gov.hmrc.play.audit.model.TruncationLog]
        )(any[HeaderCarrier]))
        .thenReturn(createdDataEvent)

      val result: Future[Result] = xmlErrorHandler.onServerError(requestHeader, exception)

      status(result)                              shouldEqual reportAs
      xml.XML.loadString(contentAsString(result)) should equal(<error>
          <statusCode>
            {reportAs.toString}
          </statusCode>
          <message>
            {exception.getMessage}
          </message>
        </error>)(after being streamlined[Elem])

      verify(auditConnector).sendEvent(is(createdDataEvent))(any[HeaderCarrier], any[ExecutionContext])
    }

    "log a warning for upstream code in the warning list" when {
      class WarningSetup(upstreamWarnStatuses: Seq[Int]) extends Setup {
        override val configuration: Configuration = Configuration(
          "appName"                                     -> "myApp",
          "bootstrap.errorHandler.warnOnly.statusCodes" -> upstreamWarnStatuses
        )
      }

      def withCaptureOfLoggingFrom(loggerLike: LoggerLike)(body: (=> List[ILoggingEvent]) => Unit): Unit = {
        import ch.qos.logback.classic.{Logger => LogbackLogger}

        import scala.jdk.CollectionConverters._

        val logger   = loggerLike.logger.asInstanceOf[LogbackLogger]
        val appender = new ListAppender[ILoggingEvent]()
        appender.setContext(logger.getLoggerContext)
        appender.start()
        logger.addAppender(appender)
        logger.setLevel(Level.TRACE)
        logger.setAdditive(true)
        body(appender.list.asScala.toList)
      }

      "an UpstreamErrorResponse exception occurs" in new WarningSetup(Seq(500)) {
        withCaptureOfLoggingFrom(Logger(xmlErrorHandler.getClass)) { logEvents =>
          xmlErrorHandler
            .onServerError(requestHeader, UpstreamErrorResponse("any application exception", 500, 502))
            .futureValue

          eventually {
            val event = logEvents.loneElement
            event.getLevel   shouldBe Level.WARN
            event.getMessage shouldBe s"any application exception"
          }
        }
      }

      "a HttpException occurs" in new WarningSetup(Seq(400)) {
        withCaptureOfLoggingFrom(Logger(xmlErrorHandler.getClass)) { logEvents =>
          xmlErrorHandler.onServerError(requestHeader, new BadRequestException("any application exception")).futureValue

          eventually {
            val event = logEvents.loneElement
            event.getLevel   shouldBe Level.WARN
            event.getMessage shouldBe s"any application exception"
          }
        }
      }
    }

  }

  "onClientError" must {

    "audit an error and return json response for 400" in new Setup {
      val createdDataEvent: DataEvent = DataEvent("auditSource", "auditType")
      Mockito
        .when(
          httpAuditEvent.dataEvent(
            eventType       = is("ServerValidationError"),
            transactionName = is("Request bad format exception"),
            request         = is(requestHeader),
            detail          = is(Map.empty),
            truncationLog   = any[uk.gov.hmrc.play.audit.model.TruncationLog]
          )(any[HeaderCarrier]))
        .thenReturn(createdDataEvent)

      val result: Future[Result] =
        xmlErrorHandler.onClientError(requestHeader, BAD_REQUEST, "some message we want to override")

      status(result)                              shouldEqual BAD_REQUEST
      xml.XML.loadString(contentAsString(result)) should equal(<error>
          <statusCode>400</statusCode>
          <message>bad request</message>
        </error>)(after being streamlined[Elem])

      verify(auditConnector).sendEvent(is(createdDataEvent))(any[HeaderCarrier], any[ExecutionContext])
    }

    "audit an error and return json response for 404 including requested path" in new Setup {
      val createdDataEvent: DataEvent = DataEvent("auditSource", "auditType")
      Mockito
        .when(
          httpAuditEvent.dataEvent(
            eventType       = is("ResourceNotFound"),
            transactionName = is("Resource Endpoint Not Found"),
            request         = is(requestHeader),
            detail          = is(Map.empty),
            truncationLog   = any[uk.gov.hmrc.play.audit.model.TruncationLog]
          )(any[HeaderCarrier]))
        .thenReturn(createdDataEvent)

      val result: Future[Result] =
        xmlErrorHandler.onClientError(requestHeader, NOT_FOUND, "some message we want to override")

      status(result)                              shouldEqual NOT_FOUND
      xml.XML.loadString(contentAsString(result)) should equal(<error>
        <statusCode>404</statusCode>
          <message>URI not found</message>
          <requested>
            {uri}
          </requested>
        </error>)(after being streamlined[Elem])

      verify(auditConnector).sendEvent(is(createdDataEvent))(any[HeaderCarrier], any[ExecutionContext])
    }

    "audit an error and return json response for 4xx except 404 and 400" in new Setup {
      (401 to 403) ++ (405 to 499) foreach { statusCode =>
        val createdDataEvent = DataEvent("auditSource", "auditType")
        Mockito
          .when(
            httpAuditEvent.dataEvent(
              eventType       = is("ClientError"),
              transactionName = is(s"A client error occurred, status: $statusCode"),
              request         = is(requestHeader),
              detail          = is(Map.empty),
            truncationLog   = any[uk.gov.hmrc.play.audit.model.TruncationLog]
            )(any[HeaderCarrier]))
          .thenReturn(createdDataEvent)

        val errorMessage = "unauthorized"

        val result = xmlErrorHandler.onClientError(requestHeader, statusCode, errorMessage)

        status(result)                              shouldEqual statusCode
        xml.XML.loadString(contentAsString(result)) should equal(<error>
            <statusCode>
              {statusCode.toString}
            </statusCode>
            <message>
              {errorMessage}
            </message>
          </error>)(after being streamlined[Elem])

        verify(auditConnector).sendEvent(is(createdDataEvent))(any[HeaderCarrier], any[ExecutionContext])
      }
    }
  }

  private trait Setup {
    val uri                                                = "some-uri"
    val requestHeader: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(GET, uri)

    val auditConnector: AuditConnector = mock[AuditConnector]
    Mockito
      .when(auditConnector.sendEvent(any[DataEvent])(any[HeaderCarrier], any[ExecutionContext]))
      .thenReturn(Future.successful(Success))
    val httpAuditEvent: HttpAuditEvent = mock[HttpAuditEvent]

    val configuration: Configuration = Configuration("appName" -> "myApp")
    lazy val xmlErrorHandler         = new XmlErrorHandler(auditConnector, httpAuditEvent, configuration)
  }

}
