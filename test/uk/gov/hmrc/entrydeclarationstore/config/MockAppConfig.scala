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

package uk.gov.hmrc.entrydeclarationstore.config

import org.scalamock.handlers.CallHandler
import uk.gov.hmrc.entrydeclarationstore.utils.{TestHarness, XmlFormatConfig}

import scala.concurrent.duration.FiniteDuration

trait MockAppConfig extends TestHarness {
  val mockAppConfig: AppConfig = mock[AppConfig]

  object MockAppConfig {
    def appName: CallHandler[String] = (() => mockAppConfig.appName).expects()

    def apiSubscriptionFieldsHost: CallHandler[String] = (() => mockAppConfig.apiSubscriptionFieldsHost).expects()

    def apiGatewayContext: CallHandler[String] = (() => mockAppConfig.apiGatewayContext).expects()

    def apiStatus: CallHandler[String] = (() => mockAppConfig.apiStatus).expects()

    def apiEndpointsEnabled: CallHandler[Boolean] = (() => mockAppConfig.apiEndpointsEnabled).expects()

    def eventsHost: CallHandler[String] = (() => mockAppConfig.eventsHost).expects()

    def eisHost: CallHandler[String] = (() => mockAppConfig.eisHost).expects()

    def eisNewEnsUrlPath: CallHandler[String] = (() => mockAppConfig.eisNewEnsUrlPath).expects()

    def eisAmendEnsUrlPath: CallHandler[String] = (() => mockAppConfig.eisAmendEnsUrlPath).expects()

    def eisBearerToken: CallHandler[String] = (() => mockAppConfig.eisBearerToken).expects()

    def eisInboundBearerToken: CallHandler[String] = (() => mockAppConfig.eisInboundBearerToken).expects()

    def eisEnvironment: CallHandler[String] = (() => mockAppConfig.eisEnvironment).expects()

    def eisRetries: CallHandler[List[FiniteDuration]] = (() => mockAppConfig.eisRetries).expects()

    def eisRetryStatusCodes: CallHandler[Set[Int]] = (() => mockAppConfig.eisRetryStatusCodes).expects()

    def xmlFormatConfig: CallHandler[XmlFormatConfig] = (() => mockAppConfig.xmlFormatConfig).expects()

    def validateXMLtoJsonTransformation: CallHandler[Boolean] =
      (() => mockAppConfig.validateXMLtoJsonTransformation).expects()

    def defaultTtl: CallHandler[FiniteDuration] = (() => mockAppConfig.defaultTtl).expects()

    def shortTtl: CallHandler[FiniteDuration] = (() => mockAppConfig.shortTtl).expects()

    def businessRules313: CallHandler[Seq[String]] = (() => mockAppConfig.businessRules313).expects()

    def businessRules315: CallHandler[Seq[String]] = (() => mockAppConfig.businessRules315).expects()

    def headerAllowlist: CallHandler[Seq[String]] = (() => mockAppConfig.headerAllowlist).expects()

    def logSubmissionPayloads: CallHandler[Boolean] = (() => mockAppConfig.logSubmissionPayloads).expects()

    //NRS config items
    def nrsBaseUrl: CallHandler[String] = (() => mockAppConfig.nrsBaseUrl).expects()

    def nrsApiKey: CallHandler[String] = (() => mockAppConfig.nrsApiKey).expects()

    def nrsRetries: CallHandler[List[FiniteDuration]] = (() => mockAppConfig.nrsRetries).expects()

    def nrsEnabled: CallHandler[Boolean] = (() => mockAppConfig.nrsEnabled).expects()

    def replayBatchSize: CallHandler[Int] = (() => mockAppConfig.replayBatchSize).expects()

    def autoReplayLockDuration: CallHandler[FiniteDuration] = (() => mockAppConfig.autoReplayLockDuration).expects()
    def autoReplayRunInterval: CallHandler[FiniteDuration] = (() => mockAppConfig.autoReplayRunInterval).expects()

    def optionalFieldsEnabled: CallHandler[Boolean] = (() => mockAppConfig.optionalFieldsFeature).expects()

  }

}
