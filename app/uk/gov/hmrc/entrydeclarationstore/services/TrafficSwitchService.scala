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

package uk.gov.hmrc.entrydeclarationstore.services

import java.time.Duration

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.entrydeclarationstore.logging.LoggingContext
import uk.gov.hmrc.entrydeclarationstore.models.{TrafficSwitchState, TrafficSwitchStatus}
import uk.gov.hmrc.entrydeclarationstore.reporting.{EventSources, ReportSender, TrafficStarted}
import uk.gov.hmrc.entrydeclarationstore.repositories.TrafficSwitchRepo
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TrafficSwitchService @Inject()(repo: TrafficSwitchRepo, reportSender: ReportSender)(
  implicit ec: ExecutionContext) {
  def resetTrafficSwitch: Future[Unit] = repo.resetToDefault
  def stopTrafficFlow: Future[Unit]    = repo.setTrafficSwitchState(TrafficSwitchState.NotFlowing).map(_ => ())
  def startTrafficFlow: Future[Unit] = {
    val result = repo.setTrafficSwitchState(TrafficSwitchState.Flowing)
      result.map(_.map {
        case TrafficSwitchStatus(_, Some(timeStopped), Some(timeStarted)) =>
          reportSender.sendReport(
            TrafficStarted(Duration.between(timeStopped, timeStarted).toMillis)
          )(implicitly[EventSources[TrafficStarted]], HeaderCarrier(), LoggingContext())
        case _ =>
      })
      result.map(_ => ())
  }
  def getTrafficSwitchStatus: Future[TrafficSwitchStatus] = repo.getTrafficSwitchStatus
  def getTrafficSwitchState: Future[TrafficSwitchState]   = getTrafficSwitchStatus.map(_.isTrafficFlowing)
}
