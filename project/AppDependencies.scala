/*
 * Copyright 2025 HM Revenue & Customs
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

import sbt.*

object AppDependencies {
  val bootstrapVersion = "10.1.0"
  val pekkoVersion = "1.1.5"
  val mongoVersion = "2.7.0"

  val compile: Seq[ModuleID] = Seq(
    "com.github.java-json-tools"   %  "json-schema-validator"     % "2.2.14",
    "uk.gov.hmrc.mongo"            %% "hmrc-mongo-play-30"        % mongoVersion,
    "uk.gov.hmrc"                  %% "bootstrap-backend-play-30" % bootstrapVersion,
    "org.typelevel"                %% "cats-core"                 % "2.13.0",
    "org.scala-lang.modules"       %% "scala-xml"                 % "2.4.0",
    "com.lucidchart"               %% "xtract"                    % "2.3.0",
    "org.apache.groovy"             %  "groovy"                   % "5.0.1",
    "com.chuusai"                  %% "shapeless"                 % "2.3.13",
    "com.fasterxml.jackson.module" %% "jackson-module-scala"      % "2.20.0"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"            %% "bootstrap-test-play-30"      % bootstrapVersion,
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-test-play-30"     % mongoVersion,
    "org.scalamock"          %% "scalamock"                   % "7.5.0",
    "org.scalacheck"         %% "scalacheck"                  % "1.19.0",
    "org.mockito"            %  "mockito-core"                % "5.19.0",
    "org.mockito"            %% "mockito-scala"               % "2.0.0",
    "com.github.pjfanning"   %% "pekko-mock-scheduler"        % "0.6.0",
    "org.apache.pekko"       %% "pekko-testkit"               % pekkoVersion,
    "org.apache.pekko"       %% "pekko-actor-typed"           % pekkoVersion,
    "org.apache.pekko"       %% "pekko-slf4j"                 % pekkoVersion,
    "org.apache.pekko"       %% "pekko-protobuf-v3"           % pekkoVersion,
    "org.apache.pekko"       %% "pekko-stream"                % pekkoVersion,
    "org.apache.pekko"       %% "pekko-serialization-jackson" % pekkoVersion
  ).map(_ % Test)

  val itDependencies: Seq[ModuleID] = Seq()
}
