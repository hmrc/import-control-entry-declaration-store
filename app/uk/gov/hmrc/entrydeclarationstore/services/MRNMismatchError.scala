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

package uk.gov.hmrc.entrydeclarationstore.services

import uk.gov.hmrc.entrydeclarationstore.utils.XmlFormats

import scala.xml.Node

object MRNMismatchError {
  implicit val xmlFormats: XmlFormats[MRNMismatchError.type] = new XmlFormats[MRNMismatchError.type] {
    override def toXml(a: MRNMismatchError.type): Node =
      // @formatter:off
      <err:ErrorResponse xmlns:err="http://www.govtalk.gov.uk/CM/errorresponse" xmlns:dsl="http://decisionsoft.com/rim/errorExtension" SchemaVersion="2.0">
        <err:Application>
          <err:MessageCount>1</err:MessageCount>
        </err:Application>
        <err:Error>
          <err:RaisedBy>HMRC</err:RaisedBy>
          <err:Number>8999</err:Number>
          <err:Type>business</err:Type>
          <err:Text>MRN in body and URL do not match.</err:Text>
          <err:Location></err:Location>
        </err:Error>
      </err:ErrorResponse>
    // @formatter:on
  }
}
