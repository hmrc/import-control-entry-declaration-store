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

package uk.gov.hmrc.entrydeclarationstore.controllers

import play.api.mvc._
import uk.gov.hmrc.entrydeclarationstore.models.StandardError
import uk.gov.hmrc.entrydeclarationstore.services.{AuthService, UserDetails}
import uk.gov.hmrc.entrydeclarationstore.utils.Timer
import uk.gov.hmrc.entrydeclarationstore.utils.XmlFormats._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

abstract class AuthorisedController(cc: ControllerComponents) extends BackendController(cc) {
  self: Timer =>

  case class UserRequest[A](request: Request[A], userDetails: UserDetails) extends WrappedRequest[A](request)

  val authService: AuthService

  def authorisedAction(eoriCorrectForRequest: (Request[_], String) => Boolean): ActionBuilder[UserRequest, AnyContent] =
    new ActionBuilder[UserRequest, AnyContent] {

      override def parser: BodyParser[AnyContent] = cc.parsers.defaultBodyParser

      implicit override protected def executionContext: ExecutionContext = cc.executionContext

      private def error(err: StandardError) =
        Future.successful(Status(err.status)(err.toXml))

      override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
        timeFuture("Handle authentication", "handleSubmissionController.authentication") {
          implicit val headerCarrier: HeaderCarrier = hc(request)
          implicit val headers: Headers             = request.headers

          authService.authenticate.flatMap {
            case Some(userDetails) =>
              if (eoriCorrectForRequest(request, userDetails.eori)) {
                block(UserRequest(request, userDetails))
              } else {
                error(StandardError.forbidden)
              }

            case None => error(StandardError.unauthorized)
          }
        }
    }

}
