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

import java.time.Instant

import play.api.http.MimeTypes
import play.api.libs.json.{JsValue, Json, Writes}
import uk.gov.hmrc.entrydeclarationstore.circuitbreaker.AuthRequest

case class NRSMetadata(
  businessId: String,
  notableEvent: String,
  payloadContentType: String,
  userSubmissionTimestamp: Instant,
  identityData: IdentityData,
  userAuthToken: String,
  headerData: JsValue,
  searchKeys: SearchKeys)

object NRSMetadata {
  implicit val writes: Writes[NRSMetadata] = Json.writes[NRSMetadata]

  def apply(
    userSubmissionTimestamp: Instant,
    eori: String,
    request: AuthRequest[_]
  ): NRSMetadata =
    NRSMetadata(
      businessId              = "safety-and-security",
      notableEvent            = "entry-declaration",
      payloadContentType      = MimeTypes.XML,
      userSubmissionTimestamp = userSubmissionTimestamp,
      identityData            = request.identityData,
      userAuthToken           = request.token,
      headerData              = Json.toJson(request.headers.toMap.map(h => h._1 -> h._2.head)),
      searchKeys              = SearchKeys(eori)
    )
}
