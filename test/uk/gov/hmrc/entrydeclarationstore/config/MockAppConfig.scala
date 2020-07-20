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

package uk.gov.hmrc.entrydeclarationstore.config

import org.scalamock.handlers.CallHandler
import org.scalamock.scalatest.MockFactory
import uk.gov.hmrc.entrydeclarationstore.utils.XmlFormatConfig

import scala.concurrent.duration.FiniteDuration

trait MockAppConfig extends MockFactory {
  val mockAppConfig: AppConfig = mock[AppConfig]

  object MockAppConfig {
    def appName: CallHandler[String] = mockAppConfig.appName _ expects ()

    def apiSubscriptionFieldsHost: CallHandler[String] = mockAppConfig.apiSubscriptionFieldsHost _ expects ()

    def apiGatewayContext: CallHandler[String] = mockAppConfig.apiGatewayContext _ expects ()

    def eventsHost: CallHandler[String] = mockAppConfig.eventsHost _ expects ()

    def eisHost: CallHandler[String] = mockAppConfig.eisHost _ expects ()

    def eisNewEnsUrlPath: CallHandler[String] = mockAppConfig.eisNewEnsUrlPath _ expects ()

    def eisAmendEnsUrlPath: CallHandler[String] = mockAppConfig.eisAmendEnsUrlPath _ expects ()

    def eisBearerToken: CallHandler[String] = mockAppConfig.eisBearerToken _ expects ()

    def eisInboundBearerToken: CallHandler[String] = mockAppConfig.eisInboundBearerToken _ expects ()

    def eisEnvironment: CallHandler[String] = mockAppConfig.eisEnvironment _ expects ()

    def xmlFormatConfig: CallHandler[XmlFormatConfig] = mockAppConfig.xmlFormatConfig _ expects ()

    def validateXMLtoJsonTransformation: CallHandler[Boolean] =
      mockAppConfig.validateXMLtoJsonTransformation _ expects ()

    def defaultTtl: CallHandler[FiniteDuration] = mockAppConfig.defaultTtl _ expects ()

    def businessRules313: CallHandler[Seq[String]] = mockAppConfig.businessRules313 _ expects ()

    def businessRules315: CallHandler[Seq[String]] = mockAppConfig.businessRules315 _ expects ()

    def headerWhitelist: CallHandler[Seq[String]] = mockAppConfig.headerWhitelist _ expects ()

    def replayBatchSizeLimit: CallHandler[Int] = mockAppConfig.replayBatchSizeLimit _ expects ()

    def logSubmissionPayloads: CallHandler[Boolean] = mockAppConfig.logSubmissionPayloads _ expects ()

    //NRS config items
    def nrsBaseUrl: CallHandler[String] = mockAppConfig.nrsBaseUrl _ expects ()

    def nrsApiKey: CallHandler[String] = mockAppConfig.nrsApiKey _ expects ()

    def nrsRetries: CallHandler[List[FiniteDuration]] = mockAppConfig.nrsRetries _ expects ()
  }

}
