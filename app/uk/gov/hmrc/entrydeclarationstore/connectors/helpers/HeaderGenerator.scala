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

package uk.gov.hmrc.entrydeclarationstore.connectors.helpers

import play.api.Logger
import play.api.http.HeaderNames._
import play.api.http.MimeTypes
import uk.gov.hmrc.entrydeclarationstore.config.AppConfig
import uk.gov.hmrc.http.HeaderCarrier

import java.time.Clock
import javax.inject.{Inject, Singleton}

@Singleton
class HeaderGenerator @Inject()(clock: Clock, appConfig: AppConfig) {

  def headersForEIS(submissionId: String)(implicit hc: HeaderCarrier): Seq[(String, String)] = {
    val upperCasedAllowList = appConfig.headerAllowlist.map(_.toUpperCase)
    val allowListedHeaders = hc.headers.filter {
      case (name, _) => upperCasedAllowList contains name.toUpperCase
    }
    val headers = allowListedHeaders ++ Seq(
      DATE               -> DateTimeUtils.httpDateFormatFor(clock.instant),
      "X-Correlation-ID" -> submissionId,
      CONTENT_TYPE       -> MimeTypes.JSON,
      ACCEPT             -> MimeTypes.JSON,
      "Environment"      -> appConfig.eisEnvironment
    )

    Logger.info(s"EIS send headers $headers")

    appConfig.eisBearerToken match {
      case ""          => headers
      case bearerToken => headers ++ Seq(AUTHORIZATION -> s"Bearer $bearerToken")
    }
  }
}
