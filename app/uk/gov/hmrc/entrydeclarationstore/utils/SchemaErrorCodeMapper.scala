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

object SchemaErrorCodeMapper {

  val mapping = Map(
    "cvc-attribute.3"          -> 4000,
    "cvc-attribute.4"          -> 4000,
    "cvc-complex-type.3.1"     -> 4000,
    "cvc-complex-type.3.2.1"   -> 4000,
    "cvc-complex-type.3.2.2"   -> 4000,
    "cvc-complex-type.4"       -> 4001,
    "cvc-complex-type.5.1"     -> 4002,
    "cvc-complex-type.5.2"     -> 4002,
    "cvc-elt.3.1"              -> 4003,
    "cvc-datatype-valid.1.2.1" -> 4020,
    "cvc-datatype-valid.1.2.2" -> 4020,
    "cvc-datatype-valid.1.2.3" -> 4020,
    "cvc-complex-type.2.1"     -> 4051,
    "cvc-complex-type.2.4.d"   -> 4051,
    "cvc-elt.3.2.1"            -> 4051,
    "cvc-elt.5.2.2.1"          -> 4051,
    "cvc-type.3.1.2"           -> 4051,
    "cvc-complex-type.2.2"     -> 4052,
    "cvc-complex-type.2.3"     -> 4053,
    "cvc-elt.1"                -> 4057,
    "cvc-elt.1.a"              -> 4057,
    "cvc-elt.5.2.2.2.1"        -> 4058,
    "cvc-elt.5.2.2.2.2"        -> 4058,
    "cvc-complex-type.2.4.a"   -> 4065,
    "cvc-type.3.1.3"           -> 4065,
    "cvc-complex-type.2.4.b"   -> 4066,
    "cvc-complex-type.2.4.c"   -> 4067,
    "cvc-type.3.1.1"           -> 4068,
    "cvc-enumeration-valid"    -> 4080,
    "cvc-fractionDigits-valid" -> 4081,
    "cvc-length-valid"         -> 4082,
    "cvc-minLength-valid"      -> 4082,
    "cvc-maxExclusive-valid"   -> 4083,
    "cvc-maxInclusive-valid"   -> 4083,
    "cvc-maxLength-valid"      -> 4083,
    "cvc-minExclusive-valid"   -> 4084,
    "cvc-minInclusive-valid"   -> 4084,
    "cvc-pattern-valid"        -> 4085,
    "cvc-totalDigits-valid"    -> 4086
  )

  val catchAllErrorCode = 4999

  def getErrorCodeFromParserFailure(validatorError: String): Int =
    mapping.getOrElse(validatorError, catchAllErrorCode)

}
