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
import org.joda.time.LocalDate
import play.api.Logger
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.{allEnrolments, _}
import uk.gov.hmrc.auth.core.retrieve._
import uk.gov.hmrc.entrydeclarationstore.config.AppConfig
import uk.gov.hmrc.entrydeclarationstore.connectors.ApiSubscriptionFieldsConnector
import uk.gov.hmrc.entrydeclarationstore.nrs.IdentityData
import uk.gov.hmrc.entrydeclarationstore.reporting.ClientType
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

case class UserDetails(eori: String, clientType: ClientType, identityData: Option[IdentityData])

@Singleton
class AuthService @Inject()(
  val authConnector: AuthConnector,
  apiSubscriptionFieldsConnector: ApiSubscriptionFieldsConnector,
  appConfig: AppConfig
)(implicit ec: ExecutionContext)
    extends AuthorisedFunctions {

  private val X_CLIENT_ID = "X-Client-Id"

  sealed trait AuthError

  case object NoClientId extends AuthError

  case object NoEori extends AuthError

  case object AuthFail extends AuthError

  def authenticate(implicit hc: HeaderCarrier): Future[Option[UserDetails]] =
    if (appConfig.nrsEnabled) {
      doAuthenticate(nrsRetrievals, identityDataBuilder)
    } else {
      doAuthenticate(EmptyRetrieval, (_: Unit) => None)
    }

  private def doAuthenticate[A](retrieval: Retrieval[A], identityBuilder: A => Option[IdentityData])(
    implicit hc: HeaderCarrier): Future[Option[UserDetails]] =
    authCSP(retrieval, identityBuilder)
      .recoverWith {
        case AuthFail | NoClientId => authNonCSP(retrieval, identityBuilder)
      }
      .toOption
      .value

  private def authCSP[A](retrieval: Retrieval[A], identityBuilder: A => Option[IdentityData])(
    implicit hc: HeaderCarrier): EitherT[Future, AuthError, UserDetails] = {
    def auth: Future[Option[Option[IdentityData]]] =
      authorised(AuthProviders(AuthProvider.PrivilegedApplication))
        .retrieve(retrieval) { identityParts =>
          Logger.debug(s"Successfully authorised CSP PrivilegedApplication")
          Future.successful(Some(identityBuilder(identityParts)))
        }
        .recover {
          case ae: AuthorisationException =>
            Logger.debug(s"No authorisation for CSP PrivilegedApplication", ae)
            None
        }

    for {
      clientId     <- EitherT.fromOption[Future](hc.headers.find(_._1.equalsIgnoreCase(X_CLIENT_ID)).map(_._2), NoClientId)
      identityData <- EitherT.fromOptionF(auth, AuthFail)
      eori         <- EitherT.fromOptionF(apiSubscriptionFieldsConnector.getAuthenticatedEoriField(clientId), NoEori: AuthError)
    } yield UserDetails(eori, ClientType.CSP, identityData)
  }

  private def authNonCSP[A](retrieval: Retrieval[A], identityBuilder: A => Option[IdentityData])(
    implicit hc: HeaderCarrier): EitherT[Future, AuthError, UserDetails] =
    EitherT(authorised(AuthProviders(AuthProvider.GovernmentGateway))
      .retrieve(retrieval and allEnrolments) {
        case identityParts ~ usersEnrolments =>
          val enrolmentKey = if (appConfig.newSSEnrolmentEnabled) "HMRC-SS-ORG" else "HMRC-ICS-ORG"
          val identifier   = if (appConfig.newSSEnrolmentEnabled) "EORINumber" else "EoriTin"

          val icsEnrolments =
            usersEnrolments.enrolments.filter(enrolment => enrolment.isActivated && enrolment.key == enrolmentKey)

          val eoris = for {
            enrolment <- icsEnrolments
            eoriId    <- enrolment.getIdentifier(identifier)
          } yield eoriId.value

          val eori = eoris.headOption

          val result = eori match {
            case Some(eori) => UserDetails(eori, ClientType.GGW, identityBuilder(identityParts)).asRight
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

  private lazy val nrsRetrievals =
    (affinityGroup and
      internalId and externalId and agentCode and credentials and confidenceLevel and nino and saUtr and name and dateOfBirth
      and email and agentInformation and groupIdentifier and credentialRole
      and mdtpInformation and itmpName and itmpDateOfBirth and itmpAddress and credentialStrength and loginTimes)

  private def identityDataBuilder(
    identityParts: Option[AffinityGroup] ~ Option[String] ~ Option[String] ~ Option[String] ~
      Option[Credentials] ~ ConfidenceLevel ~ Option[String] ~ Option[String] ~ Option[Name] ~ Option[LocalDate] ~
      Option[String] ~ AgentInformation ~ Option[String] ~ Option[CredentialRole] ~ Option[MdtpInformation] ~
      Option[ItmpName] ~ Option[LocalDate] ~ Option[ItmpAddress] ~ Option[String] ~ LoginTimes): Option[IdentityData] =
    identityParts match {

      case affGroup ~ inId ~ exId ~ agCode ~ creds
            ~ confLevel ~ ni ~ saRef ~ nme ~ dob
            ~ eml ~ agInfo ~ groupId ~ credRole
            ~ mdtpInfo ~ itmpName ~ itmpDob ~ itmpAddress ~ credStrength ~ logins =>
        // @formatter:off
        Some(IdentityData(
          inId, exId, agCode, creds, confLevel, ni, saRef, nme, dob,
          eml, agInfo, groupId, credRole, mdtpInfo, itmpName, itmpDob,
          itmpAddress, affGroup, credStrength, logins
        ))
      // @formatter:on
    }
}
