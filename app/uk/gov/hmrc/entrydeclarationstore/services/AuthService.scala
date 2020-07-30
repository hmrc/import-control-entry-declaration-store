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

import cats.data.EitherT
import cats.implicits._
import javax.inject.{Inject, Singleton}
import play.api.Logger
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.EmptyRetrieval
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.allEnrolments
import uk.gov.hmrc.entrydeclarationstore.connectors.ApiSubscriptionFieldsConnector
import uk.gov.hmrc.entrydeclarationstore.reporting.ClientType
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

case class UserDetails(eori: String, clientType: ClientType)

@Singleton
class AuthService @Inject()(
  val authConnector: AuthConnector,
  apiSubscriptionFieldsConnector: ApiSubscriptionFieldsConnector)(implicit ec: ExecutionContext)
    extends AuthorisedFunctions {

  private val X_CLIENT_ID = "X-Client-Id"

  sealed trait AuthError
  case object NoClientId extends AuthError
  case object NoEori extends AuthError
  case object AuthFail extends AuthError

  def authenticate()(implicit hc: HeaderCarrier): Future[Option[UserDetails]] =
    authCSP
      .recoverWith {
        case AuthFail | NoClientId => authNonCSP
      }
      .toOption
      .value

  private def authCSP(implicit hc: HeaderCarrier): EitherT[Future, AuthError, UserDetails] = {
    def auth: Future[Option[Unit]] =
      authorised(AuthProviders(AuthProvider.PrivilegedApplication))
        .retrieve(EmptyRetrieval) { _ =>
          Logger.debug(s"Successfully authorised CSP PrivilegedApplication")
          Future.successful(Some(()))
        }
        .recover {
          case ae: AuthorisationException =>
            Logger.debug(s"No authorisation for CSP PrivilegedApplication", ae)
            None
        }

    for {
      clientId <- EitherT.fromOption[Future](hc.headers.find(_._1 == X_CLIENT_ID).map(_._2), NoClientId)
      _        <- EitherT.fromOptionF(auth, AuthFail)
      eori     <- EitherT.fromOptionF(apiSubscriptionFieldsConnector.getAuthenticatedEoriField(clientId), NoEori: AuthError)
    } yield UserDetails(eori, ClientType.CSP)
  }

  private def authNonCSP(implicit hc: HeaderCarrier): EitherT[Future, AuthError, UserDetails] =
    EitherT(authorised(AuthProviders(AuthProvider.GovernmentGateway))
      .retrieve(allEnrolments) { usersEnrolments =>
        val icsEnrolments =
          usersEnrolments.enrolments.filter(enrolment => enrolment.isActivated && enrolment.key == "HMRC-ICS-ORG")

        val eoris = for {
          enrolment <- icsEnrolments
          eoriId    <- enrolment.getIdentifier("EoriTin")
        } yield eoriId.value

        val eori = eoris.headOption

        val result = eori match {
          case Some(eori) => UserDetails(eori, ClientType.GGW).asRight
          case None       => NoEori.asLeft
        }

        Logger.debug(
          s"Successfully authorised non-CSP GovernmentGateway with enrolments ${usersEnrolments.enrolments} and eori $eori")
        Future.successful(result)
      }
      .recover {
        case ae: AuthorisationException =>
          Logger.debug(s"No authorisation for non-CSP GovernmentGateway", ae)
          AuthFail.asLeft
      })
}
