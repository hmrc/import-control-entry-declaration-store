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

import java.io.InputStream
import java.net.URL
import java.util.Collections

import scala.collection.JavaConverters._

object ResourceUtils {
  def resourceList(directoryName: String): Seq[String] = {
    import java.nio.file._

    def list(path: Path): Seq[String] = {
      val walk = Files.walk(path, 1)
      walk.iterator.asScala.toList.flatMap(path =>
        if (!path.toFile.isDirectory) Some(path.getFileName.toString) else None)
    }

    val uri = url(directoryName).toURI

    if (uri.getScheme == "jar") {
      val fileSystem: FileSystem = FileSystems.newFileSystem(uri, Collections.emptyMap[String, Object])
      try {
        list(fileSystem.getPath(directoryName))
      } finally fileSystem.close()
    } else {
      list(Paths.get(uri))
    }
  }

  def url(resourceName: String): URL = Thread.currentThread().getContextClassLoader.getResource(resourceName)

  def withInputStreamFor[A](resourceName: String)(block: InputStream => A): A = {
    val is = Thread.currentThread().getContextClassLoader.getResourceAsStream(resourceName)
    try block(is)
    finally is.close()
  }

  def withInputStreamFor[A](clazz: Class[_], resourceName: String)(block: InputStream => A): A = {
    val is = clazz.getResourceAsStream(resourceName)
    try block(is)
    finally is.close()
  }

  def asString(resourceName: String): String =
    withInputStreamFor(resourceName)(scala.io.Source.fromInputStream(_).mkString)
}
