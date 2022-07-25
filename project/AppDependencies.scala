import play.core.PlayVersion.current
import sbt._

object AppDependencies {
  val bootstrapVersion = "5.25.0"

  val compile: Seq[ModuleID] = Seq(
    "com.github.java-json-tools"   %  "json-schema-validator"     % "2.2.14",
    "uk.gov.hmrc.mongo"            %% "hmrc-mongo-play-28"        % "0.67.0",
    "uk.gov.hmrc"                  %% "bootstrap-backend-play-28" % bootstrapVersion,
    "org.typelevel"                %% "cats-core"                 % "2.8.0",
    "org.scala-lang.modules"       %% "scala-xml"                 % "2.1.0",
    "com.lucidchart"               %% "xtract"                    % "2.2.1",
    "org.codehaus.groovy"          %  "groovy"                    % "3.0.11",
    "com.chuusai"                  %% "shapeless"                 % "2.3.9",
    "com.fasterxml.jackson.module" %% "jackson-module-scala"      % "2.13.3"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"            %% "bootstrap-test-play-28"  % bootstrapVersion % "test, it",
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-test-play-28" % "0.67.0"         % "test, it",
    "com.typesafe.play"      %% "play-test"               % current          % "test, it",
    "org.pegdown"            %  "pegdown"                 % "1.6.0"          % "test, it",
    "org.scalamock"          %% "scalamock"               % "5.2.0"          % "test, it",
    "org.scalacheck"         %% "scalacheck"              % "1.16.0"         % "test, it",
    "com.github.tomakehurst" %  "wiremock-jre8"           % "2.33.2"         % "test, it",
    "org.mockito"            %  "mockito-core"            % "4.6.1"          % "test, it",
    "org.mockito"            %% "mockito-scala"           % "1.17.7"         % "test, it",
    "org.mockito"            %% "mockito-scala-scalatest" % "1.17.7"         % "test, it",
    "com.miguno.akka"        %% "akka-mock-scheduler"     % "0.5.5"          % "test, it",
    "com.typesafe.akka"      %% "akka-testkit"            % "2.6.19"         % "test, it"
  )
  
}
