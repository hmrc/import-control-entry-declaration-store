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

package uk.gov.hmrc.entrydeclarationstore.controllers

import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.json.{JsError, JsSuccess, JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.entrydeclarationstore.models.{HousekeepingEnabled, HousekeepingStatus}
import uk.gov.hmrc.entrydeclarationstore.services.HousekeepingService
import uk.gov.hmrc.play.bootstrap.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton()
class HousekeepingController @Inject()(cc: ControllerComponents, service: HousekeepingService)(
  implicit ec: ExecutionContext)
    extends BackendController(cc) {

  def setStatus(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    request.body.validate[HousekeepingEnabled] match {
      case JsSuccess(HousekeepingEnabled(value), _) =>
        service
          .enableHousekeeping(value)
          .map(ok => if (ok) NoContent else InternalServerError)

      case err: JsError =>
        Logger.error(s"Bad request: $err")
        Future.successful(BadRequest)
    }
  }

  def getStatus: Action[AnyContent] = Action.async { implicit request =>
    service.getHousekeepingStatus.map {
      case HousekeepingStatus.On      => Ok(Json.toJson(HousekeepingEnabled(true)))
      case HousekeepingStatus.Off     => Ok(Json.toJson(HousekeepingEnabled(false)))
      case HousekeepingStatus.Unknown => InternalServerError
    }
  }

  def markRecordForDeletion(submissionId: String): Action[AnyContent] = Action.async { implicit request =>
    service.markRecordForDeletion(submissionId).map(ok => if(ok) NoContent else NotFound)
  }

  def markRecordForDeletion(eori: String, correlationId: String): Action[AnyContent] = Action.async { implicit request =>
    service.markRecordForDeletion(eori, correlationId).map(ok => if(ok) NoContent else NotFound)
  }
}
