import play.core.PlayVersion.current
import sbt._

object AppDependencies {

  val compile: Seq[ModuleID] = Seq(
    "com.github.fge"         % "json-schema-validator"      % "2.2.6",
    "uk.gov.hmrc"            %% "simple-reactivemongo"      % "7.30.0-play-26",
    "org.reactivemongo"      %% "reactivemongo-akkastream"  % "0.18.8",
    "uk.gov.hmrc"            %% "bootstrap-backend-play-26" % "2.24.0",
    "uk.gov.hmrc"            %% "mongo-lock"                % "6.23.0-play-26",
    "org.typelevel"          %% "cats-core"                 % "2.0.0",
    "org.scala-lang.modules" %% "scala-xml"                 % "1.2.0",
    "com.lucidchart"         %% "xtract"                    % "2.2.1",
    "org.codehaus.groovy"    % "groovy-all"                 % "2.5.8",
    "com.chuusai"            %% "shapeless"                 % "2.3.3"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"            %% "bootstrap-backend-play-26" % "2.24.0"        % "test, it",
    "org.scalatest"          %% "scalatest"                 % "3.0.8"         % "test, it",
    "com.typesafe.play"      %% "play-test"                 % current         % "test, it",
    "org.pegdown"            % "pegdown"                    % "1.6.0"         % "test, it",
    "org.scalatestplus.play" %% "scalatestplus-play"        % "3.1.3"         % "test, it",
    "org.scalamock"          %% "scalamock"                 % "4.4.0"         % "test, it",
    "org.scalacheck"         %% "scalacheck"                % "1.14.3"        % "test, it",
    "uk.gov.hmrc"            %% "hmrctest"                  % "3.9.0-play-26" % "test, it",
    "com.github.tomakehurst" % "wiremock"                   % "2.26.3"        % "test, it",
    "org.mockito"            % "mockito-all"                % "1.10.19"       % "test, it",
    "com.miguno.akka"        %% "akka-mock-scheduler"       % "0.5.5"         % "test, it",
    "com.typesafe.akka"      %% "akka-testkit"              % "2.5.23"        % "test, it"
  )

// Fixes a transitive dependency clash between wiremock and scalatestplus-play
  val overrides: Seq[ModuleID] = {
    val jettyFromWiremockVersion = "9.2.24.v20180105"
    Seq(
      "com.typesafe.akka"           %% "akka-actor"        % "2.5.23",
      "com.typesafe.akka"           %% "akka-stream"       % "2.5.23",
      "com.typesafe.akka"           %% "akka-protobuf"     % "2.5.23",
      "com.typesafe.akka"           %% "akka-slf4j"        % "2.5.23",
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
