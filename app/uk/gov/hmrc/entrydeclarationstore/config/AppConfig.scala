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
import uk.gov.hmrc.entrydeclarationstore.circuitbreaker.CircuitBreakerConfig
import uk.gov.hmrc.entrydeclarationstore.utils.{Retrying, XmlFormatConfig}
import uk.gov.hmrc.play.bootstrap.config.{AppName, ServicesConfig}

import scala.concurrent.duration._

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

  def eisInboundBearerToken: String

  def eisEnvironment: String

  def validateXMLtoJsonTransformation: Boolean

  def businessRules315: Seq[String]

  def businessRules313: Seq[String]

  def headerWhitelist: Seq[String]

  def eisCircuitBreakerConfig: CircuitBreakerConfig

  def defaultTtl: FiniteDuration

  def shortTtl: FiniteDuration

  def housekeepingBatchSize: Int

  def housekeepingRunInterval: FiniteDuration

  def housekeepingLockDuration: FiniteDuration

  def housekeepingRunLimit: Int

  def replayBatchSizeLimit: Int

  def logSubmissionPayloads: Boolean

  //NRS config items
  def nrsBaseUrl: String

  def nrsApiKey: String

  def nrsRetries: List[FiniteDuration]

  def nrsEnabled: Boolean
}

@Singleton
class AppConfigImpl @Inject()(config: Configuration, servicesConfig: ServicesConfig) extends AppConfig {

  val authBaseUrl: String = servicesConfig.baseUrl("auth")

  private val eisConfig = config.get[Configuration]("microservice.services.import-control-entry-declaration-eis")

  private val nrsConfig = config.get[Configuration]("microservice.services.non-repudiation")

  val xmlFormatConfig: XmlFormatConfig =
    XmlFormatConfig(config.get[Int]("response.max.errors"))

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

  lazy val eisInboundBearerToken: String =
    config.get[String]("microservice.services.import-control-entry-declaration-eis.inboundBearerToken")

  lazy val validateXMLtoJsonTransformation: Boolean =
    config.getOptional[Boolean]("validateXMLtoJsonTransformation").getOrElse(false)

  lazy val businessRules315: Seq[String] = config.get[Seq[String]]("businessRules315")

  lazy val businessRules313: Seq[String] = config.get[Seq[String]]("businessRules313")

  lazy val headerWhitelist: Seq[String] = config.get[Seq[String]]("httpHeadersWhitelist")

  lazy val eisCircuitBreakerConfig: CircuitBreakerConfig = {
    CircuitBreakerConfig(
      maxFailures              = eisConfig.get[Int]("circuitBreaker.maxFailures"),
      callTimeout              = getFiniteDuration(eisConfig, "circuitBreaker.callTimeout"),
      closedStateRefreshPeriod = getFiniteDuration(eisConfig, "circuitBreaker.closedStateRefreshPeriod"),
      openStateRefreshPeriod   = getFiniteDuration(eisConfig, "circuitBreaker.openStateRefreshPeriod")
    )
  }

  lazy val defaultTtl: FiniteDuration = getFiniteDuration(config.get[Configuration](s"mongodb"), "defaultTtl")

  lazy val shortTtl: FiniteDuration = getFiniteDuration(config.get[Configuration](s"mongodb"), "shortTtl")

  lazy val housekeepingBatchSize: Int = config.get[Configuration](s"mongodb").get[Int]("housekeepingBatchSize")

  lazy val housekeepingRunInterval: FiniteDuration =
    getFiniteDuration(config.get[Configuration](s"mongodb"), "housekeepingRunInterval")

  lazy val housekeepingLockDuration: FiniteDuration =
    getFiniteDuration(config.get[Configuration](s"mongodb"), "housekeepingLockDuration")

  lazy val housekeepingRunLimit: Int = config.get[Configuration](s"mongodb").get[Int]("housekeepingRunLimit")

  lazy val replayBatchSizeLimit: Int = config.get[Int]("replay.batchSizeLimit")

  lazy val logSubmissionPayloads: Boolean = config.get[Boolean]("logSubmissionPayloads")

  lazy val nrsBaseUrl: String = servicesConfig.baseUrl("non-repudiation")

  lazy val nrsApiKey: String = nrsConfig.get[String]("xApiKey")

  lazy val nrsRetries: List[FiniteDuration] =
    Retrying.fibonacciDelays(getFiniteDuration(nrsConfig, "initialDelay"), nrsConfig.get[Int]("numberOfRetries"))

  lazy val nrsEnabled: Boolean = nrsConfig.getOptional[Boolean]("enabled").getOrElse(true)
}
