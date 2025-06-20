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

package uk.gov.hmrc.entrydeclarationstore.reporting

import play.api.mvc.Headers
import uk.gov.hmrc.entrydeclarationstore.utils.CommonHeaders

case class ClientInfo(
  clientType: ClientType,
  clientId: Option[String],
  applicationId: Option[String]
)

object ClientInfo extends CommonHeaders {
  val cspPrefixLength: Int = 4
  def apply(clientType: ClientType)(implicit headers: Headers): ClientInfo =
    ClientInfo(clientType, clientId = headers.get(X_CLIENT_ID), applicationId = headers.get(X_APPLICATION_ID))
}
