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

package uk.gov.hmrc.entrydeclarationstore.connectors

import play.api.Logging
import play.api.libs.json.JsObject
import uk.gov.hmrc.entrydeclarationstore.config.AppConfig
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient}

import java.net.URLEncoder
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ApiSubscriptionFieldsConnector @Inject()(client: HttpClient, appConfig: AppConfig)(
  implicit ec: ExecutionContext) extends Logging {

  def getAuthenticatedEoriField(clientId: String)(implicit hc: HeaderCarrier): Future[Option[String]] = {
    val url: String =
      s"${appConfig.apiSubscriptionFieldsHost}/field/application/$clientId/context/${URLEncoder.encode(appConfig.apiGatewayContext, "UTF-8")}/version/1.0"
    logger.info(s"sending GET request to $url")

    client
      .GET[Option[JsObject]](url, Seq.empty, Seq.empty)
      .map {
        case Some(response) =>
          val eori = (response \\ "authenticatedEori").headOption.map(_.as[String])

          logger.debug(s"Got eori $eori for client Id $clientId")
          eori
        case None => None
      }
  }
}
