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

package uk.gov.hmrc.entrydeclarationstore.connectors.helpers

import javax.inject.{Inject, Singleton}
import play.api.http.HeaderNames._
import play.api.http.MimeTypes
import uk.gov.hmrc.entrydeclarationstore.config.AppConfig
import uk.gov.hmrc.http.HeaderCarrier

@Singleton
class HeaderGenerator @Inject()(dateTimeUtils: DateTimeUtils, appConfig: AppConfig) {

  def headersForEIS(submissionId: String)(implicit hc: HeaderCarrier): Seq[(String, String)] = {
    val headers = hc.headers ++ Seq(
      DATE               -> dateTimeUtils.currentDateInRFC1123Format,
      "X-Correlation-ID" -> submissionId,
      CONTENT_TYPE       -> MimeTypes.JSON,
      ACCEPT             -> MimeTypes.JSON,
      "Environment"      -> appConfig.eisEnvironment
    )
    appConfig.eisBearerToken match {
      case ""          => headers
      case bearerToken => headers ++ Seq(AUTHORIZATION -> s"Bearer $bearerToken")
    }
  }
}
