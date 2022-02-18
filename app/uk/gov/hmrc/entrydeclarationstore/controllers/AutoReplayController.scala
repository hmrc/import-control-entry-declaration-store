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

package uk.gov.hmrc.entrydeclarationstore.controllers

import play.api.Logging
import play.api.libs.json.{JsError, JsSuccess, JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.entrydeclarationstore.models.AutoReplayStatus
import uk.gov.hmrc.entrydeclarationstore.services.AutoReplayService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton()
class AutoReplayController @Inject()(cc: ControllerComponents, service: AutoReplayService)(
  implicit ec: ExecutionContext)
    extends BackendController(cc) with Logging {

  def setStatus(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    request.body.validate[AutoReplayStatus] match {
      case JsSuccess(status, _) => service.setStatus(status).map(_ => NoContent)

      case err: JsError =>
        logger.error(s"Bad request: $err")
        Future.successful(BadRequest)
    }
  }

  def getStatus: Action[AnyContent] = Action.async { _ =>
    service.getStatus.map(status => Ok(Json.toJson(status)))
  }
}
