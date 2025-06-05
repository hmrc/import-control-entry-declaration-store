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

package uk.gov.hmrc.entrydeclarationstore.utils

import uk.gov.hmrc.entrydeclarationstore.reporting.ClientInfo
import uk.gov.hmrc.entrydeclarationstore.reporting.ClientType.{CSP, GGW}

import java.security.SecureRandom
import java.util.UUID

class IdGenerator {

  private val idLength = 14

  private val numberGenerator = new SecureRandom()

  def generateCorrelationIdFor(clientInfo: ClientInfo): String = {
    val cspPrefixLength = ClientInfo.cspPrefixLength
    clientInfo.clientType match {
      case CSP =>
        clientInfo.clientId match {
          case Some(id) if id.length >= cspPrefixLength =>
            val partialClientId: String = id.take(cspPrefixLength)
            correlationIdFromRandomString(idLength - cspPrefixLength, Some(partialClientId))
          case Some(_) =>
            throw new RuntimeException("clientId too short for CSP user")
          case None =>
            throw new RuntimeException("Missing clientId for CSP user")
        }
      case GGW =>
        correlationIdFromRandomString(idLength)
    }
  }

  private val chars =
    "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray

  private def correlationIdFromRandomString(length: Int, suffix: Option[String] = None): String = {
    val randomBytes = new Array[Byte](length)
    numberGenerator.nextBytes(randomBytes)

    randomStringFrom(randomBytes) + suffix.getOrElse("")
  }

  private[utils] def randomStringFrom(bytes: Array[Byte]): String =
    bytes.map { byte =>
      val index: Int = Math.floorMod(byte, chars.length)
      val char       = chars(index)

      char
    }.mkString

  def generateSubmissionId: String = UUID.randomUUID.toString

  def generateUuid: String = UUID.randomUUID.toString
}
