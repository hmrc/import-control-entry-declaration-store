import play.core.PlayVersion.current
import sbt._

object AppDependencies {
  val bootstrapVersion = "7.21.0"
  val akkaVersion = "2.6.21"
  val mongoVersion = "1.3.0"

  val compile: Seq[ModuleID] = Seq(
    "com.github.java-json-tools"   %  "json-schema-validator"     % "2.2.14",
    "uk.gov.hmrc.mongo"            %% "hmrc-mongo-play-28"        % mongoVersion,
    "uk.gov.hmrc"                  %% "bootstrap-backend-play-28" % bootstrapVersion,
    "org.typelevel"                %% "cats-core"                 % "2.10.0",
    "org.scala-lang.modules"       %% "scala-xml"                 % "2.2.0",
    "com.lucidchart"               %% "xtract"                    % "2.3.0",
    "org.codehaus.groovy"          %  "groovy"                    % "3.0.18",
    "com.chuusai"                  %% "shapeless"                 % "2.3.10",
    "com.fasterxml.jackson.module" %% "jackson-module-scala"      % "2.15.2"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"            %% "bootstrap-test-play-28"     % bootstrapVersion % "test, it",
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-test-play-28"    % mongoVersion     % "test, it",
    "com.typesafe.play"      %% "play-test"                  % current          % "test, it",
    "org.scalamock"          %% "scalamock"                  % "5.2.0"          % "test, it",
    "org.scalacheck"         %% "scalacheck"                 % "1.17.0"         % "test, it",
    "com.github.tomakehurst" %  "wiremock-jre8"              % "2.35.0"         % "test, it",
    "org.mockito"            %  "mockito-core"               % "5.4.0"          % "test, it",
    "org.mockito"            %% "mockito-scala"              % "1.17.14"        % "test, it",
    "org.mockito"            %% "mockito-scala-scalatest"    % "1.17.14"        % "test, it",
    "com.miguno.akka"        %% "akka-mock-scheduler"        % "0.5.5"          % "test, it",
    "com.typesafe.akka"      %% "akka-testkit"               % akkaVersion      % "test, it",
    "com.typesafe.akka"      %% "akka-actor-typed"           % akkaVersion      % "test, it",
    "com.typesafe.akka"      %% "akka-slf4j"                 % akkaVersion      % "test, it",
    "com.typesafe.akka"      %% "akka-protobuf-v3"           % akkaVersion      % "test, it",
    "com.typesafe.akka"      %% "akka-stream"                % akkaVersion      % "test, it",
    "com.typesafe.akka"      %% "akka-serialization-jackson" % akkaVersion      % "test, it"
  )

  val itDependencies: Seq[ModuleID] = Seq()
}
