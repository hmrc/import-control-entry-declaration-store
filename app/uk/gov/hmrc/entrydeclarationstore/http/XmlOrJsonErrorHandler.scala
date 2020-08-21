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

package uk.gov.hmrc.entrydeclarationstore.http

import javax.inject.Inject
import play.api.http.HttpErrorHandler
import play.api.mvc.{RequestHeader, Result}
import uk.gov.hmrc.play.bootstrap.backend.http.JsonErrorHandler

import scala.concurrent.Future

class XmlOrJsonErrorHandler @Inject()(xmlErrorHandler: XmlErrorHandler, jsonErrorHandler: JsonErrorHandler)
    extends HttpErrorHandler {

  private class MediaTypeMatcher(mediaSubType: String) {
    private val regex = raw"(vnd\\..*+$mediaSubType|$mediaSubType)".r

    def accepts(requestMediaSubTypes: Seq[String]): Boolean =
      requestMediaSubTypes.exists(subtype => regex.findFirstIn(subtype).isDefined)
  }

  private val jsonMatcher = new MediaTypeMatcher("json")
  private val xmlMatcher  = new MediaTypeMatcher("xml")

  override def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] =
    handlerFor(request).onClientError(request, statusCode, message)

  override def onServerError(request: RequestHeader, exception: Throwable): Future[Result] =
    handlerFor(request).onServerError(request, exception)

  private def handlerFor(request: RequestHeader) = {
    val mediaSubTypes = request.acceptedTypes.map(_.mediaSubType)

    lazy val acceptsXml  = xmlMatcher.accepts(mediaSubTypes)
    lazy val acceptsJson = jsonMatcher.accepts(mediaSubTypes)

    // Prefer xml should multiple or no types be specified...
    if (acceptsXml) xmlErrorHandler else if (acceptsJson) jsonErrorHandler else xmlErrorHandler
  }
}
