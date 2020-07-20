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

package uk.gov.hmrc.entrydeclarationstore.utils

import com.google.inject.Singleton
import com.kenshoo.play.metrics.Metrics
import uk.gov.hmrc.entrydeclarationstore.models.MessageType
import uk.gov.hmrc.entrydeclarationstore.reporting.ClientType

@Singleton
trait MetricsReporter {
  val metrics: Metrics

  private def incrementCounter(reportedMetric: String): Unit =
    metrics.defaultRegistry.counter(reportedMetric).inc()

  private def reportNoOfMessagesPerMessageType(messageType: MessageType): Unit = {
    val reportedMetric = s"messageType.$messageType.counter"
    incrementCounter(reportedMetric)
  }

  private def reportNoOfMessagesPerAuthType(clientType: ClientType): Unit = {
    val reportedMetric = s"authType.$clientType.counter"
    incrementCounter(reportedMetric)
  }

  private def reportNoOfMessagesPerTransportMode(transportMode: String): Unit = {
    val reportedMetric = s"transportMode.$transportMode.counter"
    incrementCounter(reportedMetric)
  }

  private def reportSizeOfMessage(size: Long): Unit = {
    val reportedMetric = s"message.size"
    metrics.defaultRegistry.histogram(reportedMetric).update(size)
  }

  def reportMetrics(messageType: MessageType, clientType: ClientType, transportMode: String, size: Long): Unit = {
    reportNoOfMessagesPerMessageType(messageType)
    reportNoOfMessagesPerAuthType(clientType)
    reportNoOfMessagesPerTransportMode(transportMode)
    reportSizeOfMessage(size)
  }
}
