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

    def apiStatus: CallHandler[String] = mockAppConfig.apiStatus _ expects ()

    def apiEndpointsEnabled: CallHandler[Boolean] = mockAppConfig.apiEndpointsEnabled _ expects ()

    def allowListEnabled: CallHandler[Boolean] = mockAppConfig.allowListEnabled _ expects ()

    def allowListApplicationIds: CallHandler[Seq[String]] = mockAppConfig.allowListApplicationIds _ expects ()

    def eventsHost: CallHandler[String] = mockAppConfig.eventsHost _ expects ()

    def eisHost: CallHandler[String] = mockAppConfig.eisHost _ expects ()

    def eisNewEnsUrlPath: CallHandler[String] = mockAppConfig.eisNewEnsUrlPath _ expects ()

    def eisAmendEnsUrlPath: CallHandler[String] = mockAppConfig.eisAmendEnsUrlPath _ expects ()

    def eisBearerToken: CallHandler[String] = mockAppConfig.eisBearerToken _ expects ()

    def eisInboundBearerToken: CallHandler[String] = mockAppConfig.eisInboundBearerToken _ expects ()

    def eisEnvironment: CallHandler[String] = mockAppConfig.eisEnvironment _ expects ()

    def eisRetries: CallHandler[List[FiniteDuration]] = mockAppConfig.eisRetries _ expects ()

    def eisRetryStatusCodes: CallHandler[Set[Int]] = mockAppConfig.eisRetryStatusCodes _ expects ()

    def xmlFormatConfig: CallHandler[XmlFormatConfig] = mockAppConfig.xmlFormatConfig _ expects ()

    def validateXMLtoJsonTransformation: CallHandler[Boolean] =
      mockAppConfig.validateXMLtoJsonTransformation _ expects ()

    def defaultTtl: CallHandler[FiniteDuration] = mockAppConfig.defaultTtl _ expects ()

    def shortTtl: CallHandler[FiniteDuration] = mockAppConfig.shortTtl _ expects ()

    def businessRules313: CallHandler[Seq[String]] = mockAppConfig.businessRules313 _ expects ()

    def businessRules315: CallHandler[Seq[String]] = mockAppConfig.businessRules315 _ expects ()

    def headerAllowlist: CallHandler[Seq[String]] = mockAppConfig.headerAllowlist _ expects ()

    def logSubmissionPayloads: CallHandler[Boolean] = mockAppConfig.logSubmissionPayloads _ expects ()

    //NRS config items
    def nrsBaseUrl: CallHandler[String] = mockAppConfig.nrsBaseUrl _ expects ()

    def nrsApiKey: CallHandler[String] = mockAppConfig.nrsApiKey _ expects ()

    def nrsRetries: CallHandler[List[FiniteDuration]] = mockAppConfig.nrsRetries _ expects ()

    def nrsEnabled: CallHandler[Boolean] = mockAppConfig.nrsEnabled _ expects ()

    def newSSEnrolmentEnabled: CallHandler[Boolean] = mockAppConfig.newSSEnrolmentEnabled _ stubs () returns true

    def replayBatchSize: CallHandler[Int] = (mockAppConfig.replayBatchSize _).expects
  }

}
