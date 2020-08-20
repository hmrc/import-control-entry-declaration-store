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

import play.api.mvc._
import uk.gov.hmrc.entrydeclarationstore.config.AppConfig
import uk.gov.hmrc.http.logging.Authorization
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

abstract class EisInboundAuthorisedController(cc: ControllerComponents, appConfig: AppConfig)
    extends BackendController(cc) {
  def authorisedAction: ActionBuilder[Request, AnyContent] =
    new ActionBuilder[Request, AnyContent] {

      override def parser: BodyParser[AnyContent] = cc.parsers.defaultBodyParser

      implicit override protected def executionContext: ExecutionContext = cc.executionContext
      override def invokeBlock[A](request: Request[A], block: Request[A] => Future[Result]): Future[Result] =
        hc(request).authorization match {
          case Some(Authorization(value)) if value == s"Bearer ${appConfig.eisInboundBearerToken}" => block(request)

          case _ => Future.successful(Unauthorized)
        }
    }
}
