/*
 * Copyright 2022 HM Revenue & Customs
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

import uk.gov.hmrc.entrydeclarationstore.models.ErrorWrapper
import uk.gov.hmrc.entrydeclarationstore.models.json.{EntrySummaryDeclaration, Parties}
import uk.gov.hmrc.entrydeclarationstore.nrs.IdentityData
import uk.gov.hmrc.entrydeclarationstore.reporting.SubmissionHandledData

object SubmissionUtils {
  def extractSubmissionHandledDetails(eori: String, identityData: Option[IdentityData], model: Either[ErrorWrapper[_], EntrySummaryDeclaration]) : SubmissionHandledData = {

    val parties: Option[Parties] = model match {
      case Right(p) => Some(p.parties)
      case _ => None
    }

    SubmissionHandledData(
      identityData,
      eori,
      identityData.flatMap(_.name),
      identityData.flatMap(_.itmpAddress.flatMap(_.countryName)),
      identityData.map(_.enrolments),
      parties
    )
  }
}
