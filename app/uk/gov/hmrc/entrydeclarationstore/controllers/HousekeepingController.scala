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

package uk.gov.hmrc.entrydeclarationstore.controllers

import play.api.Logging
import play.api.libs.json.{JsError, JsSuccess, JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.entrydeclarationstore.models.{HousekeepingEnabled, HousekeepingStatus}
import uk.gov.hmrc.entrydeclarationstore.services.HousekeepingService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton()
class HousekeepingController @Inject()(cc: ControllerComponents, service: HousekeepingService)(
  implicit ec: ExecutionContext)
    extends BackendController(cc) with Logging {

  def setStatus(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    request.body.validate[HousekeepingEnabled] match {
      case JsSuccess(HousekeepingEnabled(value), _) =>
        service
          .enableHousekeeping(value)
          .map(_ => NoContent)

      case err: JsError =>
        logger.error(s"Bad request: $err")
        Future.successful(BadRequest)
    }
  }

  def getStatus: Action[AnyContent] = Action.async { _ =>
    service.getHousekeepingStatus.map {
      case HousekeepingStatus(value) => Ok(Json.toJson(HousekeepingEnabled(value)))
    }
  }

  def setShortTtlBySubmissionId(submissionId: String): Action[AnyContent] = Action.async { _ =>
    service.setShortTtl(submissionId).map(ok => if (ok) NoContent else NotFound)
  }
  def setShortTtlByEoriAndCorrelationId(eori: String, correlationId: String): Action[AnyContent] = Action.async { _ =>
    service.setShortTtl(eori, correlationId).map(ok => if (ok) NoContent else NotFound)
  }
}
