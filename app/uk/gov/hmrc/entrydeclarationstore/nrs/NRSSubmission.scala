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

package uk.gov.hmrc.entrydeclarationstore.nrs

import akka.util.ByteString
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, OWrites}

import java.nio.charset.StandardCharsets
import java.util.Base64

case class NRSSubmission(payload: ByteString, metadata: NRSMetadata)

object NRSSubmission {

  private val encoder = Base64.getEncoder

  private def encodeBase64(payload: ByteString) = encoder.encodeToString(payload.toArray)

  implicit val writes: OWrites[NRSSubmission] = (
    (JsPath \ "payload").write[String].contramap(encodeBase64) and
      (JsPath \ "metadata").write[NRSMetadata]
  )(unlift(NRSSubmission.unapply))
}
