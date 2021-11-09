import play.core.PlayVersion.current
import sbt._

object AppDependencies {
  val bootstrapVersion = "5.16.0"

  val compile: Seq[ModuleID] = Seq(
    "com.github.java-json-tools"   %  "json-schema-validator"     % "2.2.14",
    "uk.gov.hmrc"                  %% "simple-reactivemongo"      % "8.0.0-play-28",
    "org.reactivemongo"            %% "reactivemongo-akkastream"  % "0.20.13",
    "uk.gov.hmrc"                  %% "bootstrap-backend-play-28" % bootstrapVersion,
    "uk.gov.hmrc"                  %% "mongo-lock"                % "7.0.0-play-28",
    "org.typelevel"                %% "cats-core"                 % "2.6.1",
    "org.scala-lang.modules"       %% "scala-xml"                 % "2.0.1",
    "com.lucidchart"               %% "xtract"                    % "2.2.1",
    "org.codehaus.groovy"          %  "groovy-all"                % "3.0.9",
    "com.chuusai"                  %% "shapeless"                 % "2.3.7",
    "com.fasterxml.jackson.module" %% "jackson-module-scala"      % "2.13.0"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"            %% "bootstrap-test-play-28"  % bootstrapVersion % "test, it",
    "com.typesafe.play"      %% "play-test"               % current          % "test, it",
    "org.pegdown"            %  "pegdown"                 % "1.6.0"          % "test, it",
    "org.scalamock"          %% "scalamock"               % "5.1.0"          % "test, it",
    "org.scalacheck"         %% "scalacheck"              % "1.15.4"         % "test, it",
    "com.github.tomakehurst" %  "wiremock-jre8"           % "2.31.0"         % "test, it",
    "org.mockito"            %  "mockito-core"            % "4.0.0"          % "test, it",
    "org.mockito"            %% "mockito-scala"           % "1.16.46"        % "test, it",
    "org.mockito"            %% "mockito-scala-scalatest" % "1.16.46"        % "test, it",
    "com.miguno.akka"        %% "akka-mock-scheduler"     % "0.5.5"          % "test, it",
    "com.typesafe.akka"      %% "akka-testkit"            % "2.6.15"         % "test, it"
  )

// Fixes a transitive dependency clash between wiremock and scalatestplus-play
  val overrides: Seq[ModuleID] = {
    val jettyFromWiremockVersion = "9.4.44.v20210927"
    Seq(
      "com.typesafe.akka"           %% "akka-actor"                 % "2.6.17",
      "com.typesafe.akka"           %% "akka-stream"                % "2.6.17",
      "com.typesafe.akka"           %% "akka-protobuf"              % "2.6.17",
      "com.typesafe.akka"           %% "akka-slf4j"                 % "2.6.17",
      "com.typesafe.akka"           %% "akka-serialization-jackson" % "2.6.17",
      "com.typesafe.akka"           %% "akka-actor-typed"           % "2.6.17",
      "com.typesafe.akka"           %% "akka-testkit"               % "2.6.17",
      "org.eclipse.jetty"           % "jetty-client"       % jettyFromWiremockVersion,
      "org.eclipse.jetty"           % "jetty-continuation" % jettyFromWiremockVersion,
      "org.eclipse.jetty"           % "jetty-http"         % jettyFromWiremockVersion,
      "org.eclipse.jetty"           % "jetty-io"           % jettyFromWiremockVersion,
      "org.eclipse.jetty"           % "jetty-security"     % jettyFromWiremockVersion,
      "org.eclipse.jetty"           % "jetty-server"       % jettyFromWiremockVersion,
      "org.eclipse.jetty"           % "jetty-servlet"      % jettyFromWiremockVersion,
      "org.eclipse.jetty"           % "jetty-servlets"     % jettyFromWiremockVersion,
      "org.eclipse.jetty"           % "jetty-util"         % jettyFromWiremockVersion,
      "org.eclipse.jetty"           % "jetty-webapp"       % jettyFromWiremockVersion,
      "org.eclipse.jetty"           % "jetty-xml"          % jettyFromWiremockVersion,
      "org.eclipse.jetty.websocket" % "websocket-api"      % jettyFromWiremockVersion,
      "org.eclipse.jetty.websocket" % "websocket-client"   % jettyFromWiremockVersion,
      "org.eclipse.jetty.websocket" % "websocket-common"   % jettyFromWiremockVersion
    )
  }
}
