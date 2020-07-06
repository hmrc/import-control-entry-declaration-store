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

package uk.gov.hmrc.entrydeclarationstore.nrs

import org.joda.time.{DateTime, LocalDate}
import play.api.libs.json.{Json, Writes}
import uk.gov.hmrc.auth.core.retrieve._
import uk.gov.hmrc.auth.core.{AffinityGroup, ConfidenceLevel, CredentialRole}
import uk.gov.hmrc.http.controllers.RestFormats

case class IdentityData(
  internalId: Option[String]       = None,
  externalId: Option[String]       = None,
  agentCode: Option[String]        = None,
  credentials: Option[Credentials] = None,
  confidenceLevel: ConfidenceLevel,
  nino: Option[String]           = None,
  saUtr: Option[String]          = None,
  name: Option[Name]             = None,
  dateOfBirth: Option[LocalDate] = None,
  email: Option[String]          = None,
  agentInformation: AgentInformation,
  groupIdentifier: Option[String] = None,
  credentialRole: Option[CredentialRole],
  mdtpInformation: Option[MdtpInformation] = None,
  itmpName: ItmpName,
  itmpDateOfBirth: Option[LocalDate] = None,
  itmpAddress: ItmpAddress,
  affinityGroup: Option[AffinityGroup],
  credentialStrength: Option[String] = None,
  loginTimes: LoginTimes)

object IdentityData {
  implicit val dateTimeWrites: Writes[DateTime]          = RestFormats.dateTimeWrite
  implicit val localDateTimeWrites: Writes[LocalDate]    = RestFormats.localDateWrite
  implicit val credWrites: Writes[Credentials]           = Json.writes[Credentials]
  implicit val nameWrites: Writes[Name]                  = Json.writes[Name]
  implicit val agentInfoWrites: Writes[AgentInformation] = Json.writes[AgentInformation]
  implicit val mdtpInfoWrites: Writes[MdtpInformation]   = Json.writes[MdtpInformation]
  implicit val itmpNameWrites: Writes[ItmpName]          = Json.writes[ItmpName]
  implicit val itmpAddressWrites: Writes[ItmpAddress]    = Json.writes[ItmpAddress]
  implicit val loginTimesWrites: Writes[LoginTimes]      = Json.writes[LoginTimes]
  implicit val writes: Writes[IdentityData]              = Json.writes[IdentityData]
}
