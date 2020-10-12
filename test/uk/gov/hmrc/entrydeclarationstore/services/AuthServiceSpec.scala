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

import org.scalatest.Inside
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Span}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals._
import uk.gov.hmrc.entrydeclarationstore.config.MockAppConfig
import uk.gov.hmrc.entrydeclarationstore.connectors.{MockApiSubscriptionFieldsConnector, MockAuthConnector}
import uk.gov.hmrc.entrydeclarationstore.nrs.NRSMetadataTestData
import uk.gov.hmrc.entrydeclarationstore.reporting.ClientType
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NoStackTrace

class AuthServiceSpec
    extends UnitSpec
    with MockAuthConnector
    with MockApiSubscriptionFieldsConnector
    with ScalaFutures
    with Inside
    with MockAppConfig
    with NRSMetadataTestData {

  implicit override val patienceConfig: PatienceConfig = PatienceConfig(timeout = Span(500, Millis))

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  val service  = new AuthService(mockAuthConnector, mockApiSubscriptionFieldsConnector, mockAppConfig)
  val eori     = "GB123"
  val clientId = "someClientId"

  // WLOG - any AuthorisationException will do
  val authException = new InsufficientEnrolments with NoStackTrace

  def validICSEnrolment(eori: String): Enrolment =
    Enrolment(
      key               = "HMRC-ICS-ORG",
      identifiers       = Seq(EnrolmentIdentifier("EoriTin", eori)),
      state             = "Activated",
      delegatedAuthRule = None)

  private val nonCSPRetrievalNRSEnabled = (
    affinityGroup and
      internalId and externalId and agentCode and credentials
      and confidenceLevel and nino and saUtr and name and dateOfBirth
      and email and agentInformation and groupIdentifier and credentialRole
      and mdtpInformation and itmpName and itmpDateOfBirth and itmpAddress and credentialStrength and loginTimes and allEnrolments
  )

  private val nonCSPRetrievalNRSDisabled = EmptyRetrieval and allEnrolments

  private def nonCSPRetrievalResultsNRSEnabled(enrolments: Enrolments) =
    // @formatter:off
    new ~(new ~(new ~(new ~(  new ~(new ~(new ~(new ~(new ~(new ~(new ~(new ~(new ~(new ~(new ~(new ~(new ~(new ~(new ~(new ~(
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
    and mdtpInformation and itmpName and itmpDateOfBirth and itmpAddress and credentialStrength and loginTimes)

  private def stubNonCSPAuth[A](retrieval: Retrieval[A])(implicit hc: HeaderCarrier) =
    MockAuthConnector
      .authorise(AuthProviders(AuthProvider.GovernmentGateway), retrieval, hc)

  private def stubCSPAuth[A](retrieval: Retrieval[A])(implicit hc: HeaderCarrier) =
    MockAuthConnector.authorise(AuthProviders(AuthProvider.PrivilegedApplication), retrieval, hc)

  private val cspIdentityDataRetrievalNRSEnabled =
    // @formatter:off
   new ~(new ~(new ~(  new ~(new ~(new ~(new ~(new ~(new ~(new ~(new ~(new ~(new ~(new ~(new ~(new ~(new ~(new ~(new ~(
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
     identityData.loginTimes
   )
  // @formatter:on

  "AuthService.authenticate" when {
    "X-Client-Id header present" when {
      implicit val hc: HeaderCarrier = HeaderCarrier().withExtraHeaders("X-Client-Id" -> clientId)

      "NRS enabled" when {
        "CSP authentication succeeds" when {
          "authenticated EORI present in subscription fields" should {
            "return that EORI" in {
              MockAppConfig.nrsEnabled returns true

              stubCSPAuth(cspRetrievalsNRSEnabled) returns Future.successful(cspIdentityDataRetrievalNRSEnabled)

              MockApiSubscriptionFieldsConnector.getAuthenticatedEoriField(clientId) returns Some(eori)
              service.authenticate.futureValue shouldBe Some(UserDetails(eori, ClientType.CSP, Some(identityData)))
            }
          }

          "no authenticated EORI present in subscription fields" should {
            "return None (without non-CSP auth)" in {
              MockAppConfig.nrsEnabled returns true
              stubCSPAuth(cspRetrievalsNRSEnabled) returns Future.successful(cspIdentityDataRetrievalNRSEnabled)
              MockApiSubscriptionFieldsConnector.getAuthenticatedEoriField(clientId) returns None

              service.authenticate.futureValue shouldBe None
            }
          }
        }

        "CSP authentication fails" should {
          authenticateBasedOnICSEnrolmentNrsEnabled { () =>
            MockAppConfig.nrsEnabled returns true
            stubCSPAuth(cspRetrievalsNRSEnabled) returns Future.failed(authException)
          }
        }

        "no X-Client-Id header present" should {
          implicit val hc: HeaderCarrier = HeaderCarrier()
          authenticateBasedOnICSEnrolmentNrsEnabled { () =>
            MockAppConfig.nrsEnabled returns true
          }
        }
      }

      "NRS disabled" when {
        "CSP authentication succeeds" when {
          "authenticated EORI present in subscription fields" should {
            "return that EORI" in {
              MockAppConfig.nrsEnabled returns false
              stubCSPAuth(EmptyRetrieval) returns Future.successful(())

              MockApiSubscriptionFieldsConnector.getAuthenticatedEoriField(clientId) returns Some(eori)
              service.authenticate.futureValue shouldBe Some(UserDetails(eori, ClientType.CSP, None))
            }
          }

          "no authenticated EORI present in subscription fields" should {
            "return None (without non-CSP auth)" in {
              MockAppConfig.nrsEnabled returns false
              stubCSPAuth(EmptyRetrieval) returns Future.successful(())
              MockApiSubscriptionFieldsConnector.getAuthenticatedEoriField(clientId) returns None

              service.authenticate.futureValue shouldBe None
            }
          }
        }

        "CSP authentication fails" should {
          authenticateBasedOnICSEnrolmentNrsDisabled { () =>
            MockAppConfig.nrsEnabled returns false
            stubCSPAuth(EmptyRetrieval) returns Future.failed(authException)
          }
        }

        "no X-Client-Id header present" should {
          implicit val hc: HeaderCarrier = HeaderCarrier()
          authenticateBasedOnICSEnrolmentNrsDisabled { () =>
            MockAppConfig.nrsEnabled returns false
          }
        }
      }
    }

    "X-Client-Id header present with different case" must {
      implicit val hc: HeaderCarrier = HeaderCarrier().withExtraHeaders("x-client-id" -> clientId)

      "Attempt CSP auth" in {
        MockAppConfig.nrsEnabled returns false
        stubCSPAuth(EmptyRetrieval) returns Future.successful(())

        MockApiSubscriptionFieldsConnector.getAuthenticatedEoriField(clientId) returns Some(eori)
        service.authenticate.futureValue shouldBe Some(UserDetails(eori, ClientType.CSP, None))
      }
    }

    def authenticateBasedOnICSEnrolmentNrsDisabled(stubScenario: () => Unit)(implicit hc: HeaderCarrier): Unit = {
      "return Some(eori)" when {
        "ICS enrolment with an eori" in {
          stubScenario()
          stubNonCSPAuth(nonCSPRetrievalNRSDisabled) returns nonCSPRetrievalResultsNRSDisabled(
            Enrolments(Set(validICSEnrolment(eori))))
          service.authenticate.futureValue shouldBe Some(UserDetails(eori, ClientType.GGW, None))
        }
      }

      "return None" when {
        "ICS enrolment with no identifiers" in {
          stubScenario()
          stubNonCSPAuth(nonCSPRetrievalNRSDisabled) returns nonCSPRetrievalResultsNRSDisabled(
            Enrolments(Set(validICSEnrolment(eori).copy(identifiers = Nil))))
          service.authenticate.futureValue shouldBe None
        }

        "no ICS enrolment in authorization header" in {
          stubScenario()
          stubNonCSPAuth(nonCSPRetrievalNRSDisabled) returns nonCSPRetrievalResultsNRSDisabled(
            Enrolments(
              Set(
                Enrolment(
                  key               = "OTHER",
                  identifiers       = Seq(EnrolmentIdentifier("EoriTin", eori)),
                  state             = "Activated",
                  delegatedAuthRule = None))))
          service.authenticate.futureValue shouldBe None
        }

        "no enrolments at all in authorization header" in {
          stubScenario()
          stubNonCSPAuth(nonCSPRetrievalNRSDisabled) returns nonCSPRetrievalResultsNRSDisabled(Enrolments(Set.empty))
          service.authenticate.futureValue shouldBe None
        }

        "ICS enrolment not activated" in {
          stubScenario()
          stubNonCSPAuth(nonCSPRetrievalNRSDisabled) returns nonCSPRetrievalResultsNRSDisabled(
            Enrolments(Set(validICSEnrolment(eori).copy(state = "inactive"))))
          service.authenticate.futureValue shouldBe None
        }

        "authorisation fails" in {
          stubScenario()
          stubNonCSPAuth(nonCSPRetrievalNRSDisabled) returns Future.failed(authException)
          service.authenticate.futureValue shouldBe None
        }
      }
    }

    def authenticateBasedOnICSEnrolmentNrsEnabled(stubScenario: () => Unit)(implicit hc: HeaderCarrier): Unit = {
      "return Some(eori)" when {
        "ICS enrolment with an eori" in {
          stubScenario()
          stubNonCSPAuth(nonCSPRetrievalNRSEnabled) returns nonCSPRetrievalResultsNRSEnabled(
            Enrolments(Set(validICSEnrolment(eori))))
          service.authenticate.futureValue shouldBe Some(UserDetails(eori, ClientType.GGW, Some(identityData)))
        }
      }

      "return None" when {
        "ICS enrolment with no identifiers" in {
          stubScenario()
          stubNonCSPAuth(nonCSPRetrievalNRSEnabled) returns nonCSPRetrievalResultsNRSEnabled(
            Enrolments(Set(validICSEnrolment(eori).copy(identifiers = Nil))))
          service.authenticate.futureValue shouldBe None
        }

        "no ICS enrolment in authorization header" in {
          stubScenario()
          stubNonCSPAuth(nonCSPRetrievalNRSEnabled) returns nonCSPRetrievalResultsNRSEnabled(
            Enrolments(
              Set(
                Enrolment(
                  key               = "OTHER",
                  identifiers       = Seq(EnrolmentIdentifier("EoriTin", eori)),
                  state             = "Activated",
                  delegatedAuthRule = None))))
          service.authenticate.futureValue shouldBe None
        }
        "no enrolments at all in authorization header" in {
          stubScenario()
          stubNonCSPAuth(nonCSPRetrievalNRSEnabled) returns nonCSPRetrievalResultsNRSEnabled(Enrolments(Set.empty))
          service.authenticate.futureValue shouldBe None
        }

        "ICS enrolment not activated" in {
          stubScenario()
          stubNonCSPAuth(nonCSPRetrievalNRSEnabled) returns nonCSPRetrievalResultsNRSEnabled(
            Enrolments(Set(validICSEnrolment(eori).copy(state = "inactive"))))
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
