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

import javax.inject.{Inject, Singleton}
import play.api.Configuration
import uk.gov.hmrc.entrydeclarationstore.utils.XmlFormatConfig
import uk.gov.hmrc.play.bootstrap.config.{AppName, ServicesConfig}

import scala.concurrent.duration.{Duration, FiniteDuration}

trait AppConfig {

  def authBaseUrl: String

  def xmlFormatConfig: XmlFormatConfig

  def appName: String

  def apiGatewayContext: String

  def apiEndpointsEnabled: Boolean

  def apiStatus: String

  def auditingEnabled: Boolean

  def graphiteHost: String

  def apiSubscriptionFieldsHost: String

  def eventsHost: String

  def eisHost: String

  def eisNewEnsUrlPath: String

  def eisAmendEnsUrlPath: String

  def eisBearerToken: String

  def eisEnvironment: String

  def validateXMLtoJsonTransformation: Boolean

  def businessRules315: Seq[String]

  def businessRules313: Seq[String]

  def headerWhitelist: Seq[String]

  def eisCircuitBreakerMaxFailures: Int

  def eisCircuitBreakerCallTimeout: FiniteDuration

  def eisCircuitBreakerResetTimeout: FiniteDuration

  def defaultTtl: FiniteDuration
}

@Singleton
class AppConfigImpl @Inject()(config: Configuration, servicesConfig: ServicesConfig) extends AppConfig {

  val authBaseUrl: String = servicesConfig.baseUrl("auth")

  private val eisConfig =
    config
      .get[Configuration](s"microservice.services.import-control-entry-declaration-eis")

  val xmlFormatConfig: XmlFormatConfig =
    XmlFormatConfig(
      config.get[Int]("response.max.errors")
    )

  private final def getFiniteDuration(config: Configuration, path: String): FiniteDuration = {
    val string = config.get[String](path)

    Duration.create(string) match {
      case f: FiniteDuration => f
      case _                 => throw new RuntimeException(s"Not a finite duration '$string' for $path")
    }
  }

  lazy val appName: String = AppName.fromConfiguration(config)

  val apiGatewayContext: String = config.get[String]("api.gateway.context")

  val apiEndpointsEnabled: Boolean = config.get[Boolean]("api.endpoints.enabled")

  val apiStatus: String = config.get[String]("api.status")

  val auditingEnabled: Boolean = config.get[Boolean]("auditing.enabled")

  val graphiteHost: String = config.get[String]("microservice.metrics.graphite.host")

  val apiSubscriptionFieldsHost: String = servicesConfig.baseUrl("api-subscription-fields")

  lazy val eventsHost: String = servicesConfig.baseUrl("import-control-entry-declaration-events")

  lazy val eisHost: String = servicesConfig.baseUrl("import-control-entry-declaration-eis")

  lazy val eisNewEnsUrlPath: String = eisConfig.get[String]("new-ens-url-path")

  lazy val eisAmendEnsUrlPath: String = eisConfig.get[String]("amend-ens-url-path")

  lazy val eisEnvironment: String = eisConfig.get[String]("environment")

  lazy val eisBearerToken: String = eisConfig.get[String]("bearerToken")

  lazy val validateXMLtoJsonTransformation: Boolean =
    config.getOptional[Boolean]("validateXMLtoJsonTransformation").getOrElse(false)

  lazy val businessRules315: Seq[String] = config.get[Seq[String]]("businessRules315")

  lazy val businessRules313: Seq[String] = config.get[Seq[String]]("businessRules313")

  lazy val headerWhitelist: Seq[String] = config.get[Seq[String]]("httpHeadersWhitelist")

  lazy val eisCircuitBreakerMaxFailures: Int = eisConfig.get[Int]("circuitBreaker.maxFailures")

  lazy val eisCircuitBreakerCallTimeout: FiniteDuration = getFiniteDuration(eisConfig, "circuitBreaker.callTimeout")

  lazy val eisCircuitBreakerResetTimeout: FiniteDuration = getFiniteDuration(eisConfig, "circuitBreaker.resetTimeout")

  lazy val defaultTtl: FiniteDuration = getFiniteDuration(config.get[Configuration](s"mongodb"), "defaultTtl")
}
