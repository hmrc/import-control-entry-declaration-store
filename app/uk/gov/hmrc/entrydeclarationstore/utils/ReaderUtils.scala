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

import com.lucidchart.open.xtract.{ParseResult, XmlReader}

trait HasEmpty {
  def isEmpty: Boolean
}

trait ReaderUtils {
  implicit def seqXmlReader[A](implicit r: XmlReader[A]): XmlReader[Seq[A]] =
    XmlReader { xml =>
      ParseResult.combine(xml.map(r.read))
    }

  implicit class XmlReaderSeqExtensions[A](r: XmlReader[Seq[A]]) {
    def mapToNoneIfEmpty: XmlReader[Option[Seq[A]]] = r.map(as => if (as.isEmpty) None else Some(as))
  }

  implicit class XmlReaderEmptyObjectExtensions[A <: HasEmpty](r: XmlReader[A]) {
    def mapToNoneIfEmpty: XmlReader[Option[A]] = r.map(a => if (a.isEmpty) None else Some(a))
  }

}
