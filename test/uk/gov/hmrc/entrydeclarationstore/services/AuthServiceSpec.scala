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

package uk.gov.hmrc.entrydeclarationstore.services

import org.scalatest.Inside
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.time.{Millis, Span}
import org.scalatest.wordspec.AnyWordSpec
import play.api.mvc.Headers
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals._
import uk.gov.hmrc.entrydeclarationstore.config.MockAppConfig
import uk.gov.hmrc.entrydeclarationstore.connectors.{MockApiSubscriptionFieldsConnector, MockAuthConnector}
import uk.gov.hmrc.entrydeclarationstore.nrs.NRSMetadataTestData
import uk.gov.hmrc.entrydeclarationstore.reporting.{ClientInfo, ClientType}
import uk.gov.hmrc.entrydeclarationstore.utils.CommonHeaders
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NoStackTrace

class AuthServiceSpec
    extends AnyWordSpec
    with MockAuthConnector
    with MockApiSubscriptionFieldsConnector
    with ScalaFutures
    with Inside
    with MockAppConfig
    with NRSMetadataTestData
    with CommonHeaders {

  implicit override val patienceConfig: PatienceConfig = PatienceConfig(timeout = Span(500, Millis))

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  val service       = new AuthService(mockAuthConnector, mockApiSubscriptionFieldsConnector, mockAppConfig)
  val eori          = "GB123"
  val clientId      = "someClientId"
  val applicationId = "someAppId"

  // WLOG - any AuthorisationException will do
  val authException = new InsufficientEnrolments with NoStackTrace

  val enrolmentKey = "HMRC-SS-ORG"
  val identifier   = "EORINumber"

  def validSSEnrolment(eori: String): Enrolment =
    Enrolment(
      key               = enrolmentKey,
      identifiers       = Seq(EnrolmentIdentifier(identifier, eori)),
      state             = "Activated",
      delegatedAuthRule = None)

  private val nonCSPRetrievalNRSEnabled = (
    affinityGroup and
      internalId and externalId and agentCode and credentials
      and confidenceLevel and nino and saUtr and name and dateOfBirth
      and email and agentInformation and groupIdentifier and credentialRole
      and mdtpInformation and itmpName and itmpDateOfBirth and itmpAddress and credentialStrength and allEnrolments and loginTimes and allEnrolments
  )

  private val nonCSPRetrievalNRSDisabled = EmptyRetrieval and allEnrolments

  private def nonCSPRetrievalResultsNRSEnabled(enrolments: Enrolments) =
    // @formatter:off
    new ~(new ~(new ~(new ~(  new ~(new ~(new ~(new ~(new ~(new ~(new ~(new ~(new ~(new ~(new ~(new ~(new ~(new ~(new ~(new ~(new ~(
      identityData.affinityGroup,
      identityData.internalId),
      identityData.externalId),
      identityData.agentCode),
      identityData.credentials),
      identityData.confidenceLevel),
      identityData.nino),
      identityData.saUtr),
      identityData.name),
      identityData.dateOfBirth),
      identityData.email),
      identityData.agentInformation),
      identityData.groupIdentifier),
      identityData.credentialRole),
      identityData.mdtpInformation),
      identityData.itmpName),
      identityData.itmpDateOfBirth),
      identityData.itmpAddress),
      identityData.credentialStrength),
      identityData.enrolments),
      identityData.loginTimes),
      enrolments
    )
  // @formatter:on

  private def nonCSPRetrievalResultsNRSDisabled(enrolments: Enrolments): Unit ~ Enrolments =
    new ~(EmptyRetrieval, enrolments)

  private val cspRetrievalsNRSEnabled = (affinityGroup and
    internalId and externalId and agentCode and credentials
    and confidenceLevel and nino and saUtr and name and dateOfBirth
    and email and agentInformation and groupIdentifier and credentialRole
    and mdtpInformation and itmpName and itmpDateOfBirth and itmpAddress and credentialStrength and allEnrolments and loginTimes)

  private def stubNonCSPAuth[A](retrieval: Retrieval[A])(implicit hc: HeaderCarrier) =
    MockAuthConnector
      .authorise(AuthProviders(AuthProvider.GovernmentGateway), retrieval, hc)

  private def stubCSPAuth[A](retrieval: Retrieval[A])(implicit hc: HeaderCarrier) =
    MockAuthConnector.authorise(AuthProviders(AuthProvider.PrivilegedApplication), retrieval, hc)

  private val cspIdentityDataRetrievalNRSEnabled =
    // @formatter:off
   new ~(new ~(new ~(  new ~(new ~(new ~(new ~(new ~(new ~(new ~(new ~(new ~(new ~(new ~(new ~(new ~(new ~(new ~(new ~(new ~(
     identityData.affinityGroup,
     identityData.internalId),
     identityData.externalId),
     identityData.agentCode),
     identityData.credentials),
     identityData.confidenceLevel),
     identityData.nino),
     identityData.saUtr),
     identityData.name),
     identityData.dateOfBirth),
     identityData.email),
     identityData.agentInformation),
     identityData.groupIdentifier),
     identityData.credentialRole),
     identityData.mdtpInformation),
     identityData.itmpName),
     identityData.itmpDateOfBirth),
     identityData.itmpAddress),
     identityData.credentialStrength),
     identityData.enrolments),
     identityData.loginTimes
   )
  // @formatter:on

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "AuthService.authenticate" when {

    "X-Client-Id header present" when {
      implicit val headers: Headers = Headers(X_CLIENT_ID -> clientId, X_APPLICATION_ID -> applicationId)

      "NRS enabled" when {
        "CSP authentication succeeds" when {
          "authenticated EORI present in subscription fields" must {
            "return that EORI" in {
              MockAppConfig.nrsEnabled returns true

              stubCSPAuth(cspRetrievalsNRSEnabled) returns Future.successful(cspIdentityDataRetrievalNRSEnabled)

              MockApiSubscriptionFieldsConnector.getAuthenticatedEoriField(clientId) returns Future.successful(Some(eori))
              service.authenticate.futureValue shouldBe Some(
                UserDetails(
                  eori,
                  ClientInfo(ClientType.CSP, clientId = Some(clientId), applicationId = Some(applicationId)),
                  Some(identityData)))
            }
          }

          "no authenticated EORI present in subscription fields" must {
            "return None (without non-CSP auth)" in {
              MockAppConfig.nrsEnabled returns true
              stubCSPAuth(cspRetrievalsNRSEnabled) returns Future.successful(cspIdentityDataRetrievalNRSEnabled)
              MockApiSubscriptionFieldsConnector.getAuthenticatedEoriField(clientId) returns Future.successful(None)

              service.authenticate.futureValue shouldBe None
            }
          }
        }

        "CSP authentication fails" must {
          authenticateBasedOnSSEnrolmentNrsEnabled { () =>
            MockAppConfig.nrsEnabled returns true

            stubCSPAuth(cspRetrievalsNRSEnabled) returns Future.failed(authException)
          }
        }

        "no X-Client-Id header present" must {
          implicit val headers: Headers = Headers(X_APPLICATION_ID -> applicationId)
          authenticateBasedOnSSEnrolmentNrsEnabled { () =>
            MockAppConfig.nrsEnabled returns true
          }
        }
      }

      "NRS disabled" when {
        "CSP authentication succeeds" when {
          "authenticated EORI present in subscription fields" must {
            "return that EORI" in {
              MockAppConfig.nrsEnabled returns false
              stubCSPAuth(EmptyRetrieval) returns Future.successful(())

              MockApiSubscriptionFieldsConnector.getAuthenticatedEoriField(clientId) returns Future.successful(Some(eori))
              service.authenticate.futureValue shouldBe Some(
                UserDetails(
                  eori,
                  ClientInfo(ClientType.CSP, clientId = Some(clientId), applicationId = Some(applicationId)),
                  None))
            }
          }

          "no authenticated EORI present in subscription fields" must {
            "return None (without non-CSP auth)" in {
              MockAppConfig.nrsEnabled returns false
              stubCSPAuth(EmptyRetrieval) returns Future.successful(())
              MockApiSubscriptionFieldsConnector.getAuthenticatedEoriField(clientId) returns Future.successful(None)

              service.authenticate.futureValue shouldBe None
            }
          }
        }

        "CSP authentication fails" must {
          authenticateBasedOnSSEnrolmentNrsDisabled { () =>
            MockAppConfig.nrsEnabled returns false

            stubCSPAuth(EmptyRetrieval) returns Future.failed(authException)
          }
        }

        "no X-Client-Id header present" must {
          implicit val headers: Headers = Headers(X_APPLICATION_ID -> applicationId)
          authenticateBasedOnSSEnrolmentNrsDisabled { () =>
            MockAppConfig.nrsEnabled returns false
          }
        }
      }
    }

    "X-Client-Id header present with different case" must {
      implicit val headers: Headers = Headers(X_CLIENT_ID -> clientId)

      "Attempt CSP auth" in {
        MockAppConfig.nrsEnabled returns false
        stubCSPAuth(EmptyRetrieval) returns Future.successful(())

        MockApiSubscriptionFieldsConnector.getAuthenticatedEoriField(clientId) returns Future.successful(Some(eori))
        service.authenticate.futureValue shouldBe Some(
          UserDetails(eori, ClientInfo(ClientType.CSP, clientId = Some(clientId), applicationId = None), None))
      }
    }

    def authenticateBasedOnSSEnrolmentNrsDisabled(
      stubScenario: () => Unit)(implicit hc: HeaderCarrier, headers: Headers): Unit = {
      "return Some(eori)" when {
        "S&S enrolment with an eori" in {
          stubScenario()
          stubNonCSPAuth(nonCSPRetrievalNRSDisabled) returns Future.successful(nonCSPRetrievalResultsNRSDisabled(
            Enrolments(Set(validSSEnrolment(eori)))))
          service.authenticate.futureValue shouldBe Some(UserDetails(eori, ClientInfo(ClientType.GGW), None))
        }
      }

      "return None" when {
        "S&S enrolment with no identifiers" in {
          stubScenario()
          stubNonCSPAuth(nonCSPRetrievalNRSDisabled) returns Future.successful(nonCSPRetrievalResultsNRSDisabled(
            Enrolments(Set(validSSEnrolment(eori).copy(identifiers = Nil)))))
          service.authenticate.futureValue shouldBe None
        }

        "no S&S enrolment in authorization header" in {
          stubScenario()
          stubNonCSPAuth(nonCSPRetrievalNRSDisabled) returns Future.successful(nonCSPRetrievalResultsNRSDisabled(
            Enrolments(
              Set(
                Enrolment(
                  key               = "OTHER",
                  identifiers       = Seq(EnrolmentIdentifier(identifier, eori)),
                  state             = "Activated",
                  delegatedAuthRule = None)))))
          service.authenticate.futureValue shouldBe None
        }

        "no enrolments at all in authorization header" in {
          stubScenario()
          stubNonCSPAuth(nonCSPRetrievalNRSDisabled) returns Future.successful(nonCSPRetrievalResultsNRSDisabled(Enrolments(Set.empty)))
          service.authenticate.futureValue shouldBe None
        }

        "S&S enrolment not activated" in {
          stubScenario()
          stubNonCSPAuth(nonCSPRetrievalNRSDisabled) returns Future.successful(nonCSPRetrievalResultsNRSDisabled(
            Enrolments(Set(validSSEnrolment(eori).copy(state = "inactive")))))
          service.authenticate.futureValue shouldBe None
        }

        "authorisation fails" in {
          stubScenario()
          stubNonCSPAuth(nonCSPRetrievalNRSDisabled) returns Future.failed(authException)
          service.authenticate.futureValue shouldBe None
        }
      }
    }

    def authenticateBasedOnSSEnrolmentNrsEnabled(
      stubScenario: () => Unit)(implicit hc: HeaderCarrier, headers: Headers): Unit = {
      "return Some(eori)" when {
        "S&S enrolment with an eori" in {
          stubScenario()
          stubNonCSPAuth(nonCSPRetrievalNRSEnabled) returns Future.successful(nonCSPRetrievalResultsNRSEnabled(
            Enrolments(Set(validSSEnrolment(eori)))))
          service.authenticate.futureValue shouldBe Some(
            UserDetails(eori, ClientInfo(ClientType.GGW), Some(identityData)))
        }
      }

      "return None" when {
        "S&S enrolment with no identifiers" in {
          stubScenario()
          stubNonCSPAuth(nonCSPRetrievalNRSEnabled) returns Future.successful(nonCSPRetrievalResultsNRSEnabled(
            Enrolments(Set(validSSEnrolment(eori).copy(identifiers = Nil)))))
          service.authenticate.futureValue shouldBe None
        }

        "no S&S enrolment in authorization header" in {
          stubScenario()
          stubNonCSPAuth(nonCSPRetrievalNRSEnabled) returns Future.successful(nonCSPRetrievalResultsNRSEnabled(
            Enrolments(
              Set(
                Enrolment(
                  key               = "OTHER",
                  identifiers       = Seq(EnrolmentIdentifier(identifier, eori)),
                  state             = "Activated",
                  delegatedAuthRule = None)))))
          service.authenticate.futureValue shouldBe None
        }
        "no enrolments at all in authorization header" in {
          stubScenario()
          stubNonCSPAuth(nonCSPRetrievalNRSEnabled) returns Future.successful(nonCSPRetrievalResultsNRSEnabled(Enrolments(Set.empty)))
          service.authenticate.futureValue shouldBe None
        }

        "S&S enrolment not activated" in {
          stubScenario()
          stubNonCSPAuth(nonCSPRetrievalNRSEnabled) returns Future.successful(nonCSPRetrievalResultsNRSEnabled(
            Enrolments(Set(validSSEnrolment(eori).copy(state = "inactive")))))
          service.authenticate.futureValue shouldBe None
        }

        "authorisation fails" in {
          stubScenario()
          stubNonCSPAuth(nonCSPRetrievalNRSEnabled) returns Future.failed(authException)
          service.authenticate.futureValue shouldBe None
        }
      }
    }
  }
}
