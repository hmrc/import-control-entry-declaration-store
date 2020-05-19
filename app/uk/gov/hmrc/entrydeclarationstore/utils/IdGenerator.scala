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

package uk.gov.hmrc.entrydeclarationstore.utils

import java.security.SecureRandom
import java.util.UUID

class IdGenerator {

  private val idLength        = 14
  private val numberGenerator = new SecureRandom()

  def generateCorrelationId: String = {
    val randomBytes = new Array[Byte](idLength)
    numberGenerator.nextBytes(randomBytes)

    correlationIdFrom(randomBytes)
  }

  private val chars =
    "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray

  private[utils] def correlationIdFrom(bytes: Array[Byte]): String =
    bytes.map { byte =>
      val index: Int = Math.floorMod(byte, chars.length)
      val char       = chars(index)

      char
    }.mkString

  def generateSubmissionId: String = UUID.randomUUID.toString
}
