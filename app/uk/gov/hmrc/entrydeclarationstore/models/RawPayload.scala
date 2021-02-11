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

package uk.gov.hmrc.entrydeclarationstore.models

import akka.util.ByteString

import scala.xml.NodeSeq

case class RawPayload(byteString: ByteString) {
  @deprecated
  def valueAsUTF8String: String = byteString.decodeString("UTF-8")
  def length: Int               = byteString.length
}

object RawPayload {
  // For testing
  def apply(string: String): RawPayload     = RawPayload(ByteString.fromString(string))
  def apply(xml: NodeSeq): RawPayload       = RawPayload(ByteString.fromString(xml.toString))
  def apply(bytes: Array[Byte]): RawPayload = RawPayload(ByteString(bytes))
}
