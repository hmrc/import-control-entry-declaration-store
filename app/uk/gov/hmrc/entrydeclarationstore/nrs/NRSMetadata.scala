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

package uk.gov.hmrc.entrydeclarationstore.nrs

import play.api.http.MimeTypes
import play.api.libs.json._
import play.api.mvc.RequestHeader
import uk.gov.hmrc.entrydeclarationstore.models.InstantFormatter

import java.time.Instant

case class NRSMetadata(
  businessId: String,
  notableEvent: String,
  payloadContentType: String,
  payloadSha256Checksum: String,
  userSubmissionTimestamp: Instant,
  identityData: IdentityData,
  userAuthToken: String,
  headerData: JsValue,
  searchKeys: SearchKeys)

object NRSMetadata extends InstantFormatter {
  implicit val writes: Writes[NRSMetadata] = Json.writes[NRSMetadata]

  def apply(
    userSubmissionTimestamp: Instant,
    submissionId: String,
    identityData: IdentityData,
    request: RequestHeader,
    checkSum: String
  ): NRSMetadata =
    NRSMetadata(
      businessId              = "iceds",
      notableEvent            = "entry-declaration",
      payloadContentType      = MimeTypes.XML,
      payloadSha256Checksum   = checkSum,
      userSubmissionTimestamp = userSubmissionTimestamp,
      identityData            = identityData,
      userAuthToken           = request.headers.get("Authorization").getOrElse(""),
      headerData              = JsObject(request.headers.toMap.map(x => x._1 -> JsString(x._2 mkString ","))),
      searchKeys              = SearchKeys(submissionId)
    )
}
