/*
 * Copyright 2022 HM Revenue & Customs
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

import java.io.IOException

import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.MimeTypes
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.AnyContentAsEmpty
import play.api.test.Helpers.{GET, _}
import play.api.test.{FakeRequest, Injecting}
import play.api.{Application, Environment, Mode}
import uk.gov.hmrc.entrydeclarationstore.housekeeping.HousekeepingScheduler

class XmlOrJsonErrorHandlerSpec extends AnyWordSpec with GuiceOneAppPerSuite with Injecting {

  override lazy val app: Application = new GuiceApplicationBuilder()
    .in(Environment.simple(mode = Mode.Dev))
    .disable[HousekeepingScheduler]
    .configure("metrics.enabled" -> "false")
    .build()

  class Test {
    // WLOG...
    val uri        = "some-uri"
    val statusCode = 123
    val message    = "test message"
    val exception  = new IOException

    protected def requestHeader: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(GET, uri)

    val errorHandler: XmlOrJsonErrorHandler = inject[XmlOrJsonErrorHandler]

    protected def testOnServerError(acceptHeaders: String*): Option[String] = {
      val result = errorHandler.onServerError(requestHeader.withHeaders(acceptHeaders.map(ACCEPT -> _): _*), exception)

      contentType(result)
    }

    protected def testOnClientError(acceptHeaders: String*): Option[String] = {
      val result =
        errorHandler.onClientError(requestHeader.withHeaders(acceptHeaders.map(ACCEPT -> _): _*), statusCode, message)

      contentType(result)
    }
  }

  "XmlOrJsonErrorHandler" when {

    "client error" must {
      "select json error handler" when {
        "accept header is application/json" in new Test {
          testOnClientError("application/json") shouldBe Some(MimeTypes.JSON)
        }

        "accept header is application/json with version" in new Test {
          testOnClientError("application/vnd.hmrc.1.0+json") shouldBe Some(MimeTypes.JSON)
        }
      }

      "select xml error handler" when {
        "accept header is application/xml" in new Test {
          testOnClientError("application/xml") shouldBe Some(MimeTypes.XML)
        }

        "accept header is application/xml with version" in new Test {
          testOnClientError("application/vnd.hmrc.1.0+xml") shouldBe Some(MimeTypes.XML)
        }

        "no accept header is supplied" in new Test {
          testOnClientError()
        }

        "both xml and json are accepted" in new Test {
          testOnClientError("application/json, application/xml") shouldBe Some(MimeTypes.XML)
        }
      }
    }

    "server error" must {
      "select json error handler" when {
        "accept header is application/json" in new Test {
          testOnServerError("application/json") shouldBe Some(MimeTypes.JSON)
        }

        "accept header is application/json with version" in new Test {
          testOnServerError("application/vnd.hmrc.1.0+json") shouldBe Some(MimeTypes.JSON)
        }
      }

      "select xml error handler" when {
        "accept header is application/xml" in new Test {
          testOnServerError("application/xml") shouldBe Some(MimeTypes.XML)
        }

        "accept header is application/xml with version" in new Test {
          testOnServerError("application/vnd.hmrc.1.0+xml") shouldBe Some(MimeTypes.XML)
        }

        "no accept header is supplied" in new Test {
          testOnServerError() shouldBe Some(MimeTypes.XML)
        }

        "both xml and json are accepted" in new Test {
          testOnServerError("application/json, application/xml") shouldBe Some(MimeTypes.XML)
        }
      }
    }
  }

}
