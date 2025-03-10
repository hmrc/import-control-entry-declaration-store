import play.core.PlayVersion.current
import sbt._

object AppDependencies {
  val bootstrapVersion = "9.11.0"
  val pekkoVersion = "1.1.3"
  val mongoVersion = "2.6.0"

  val compile: Seq[ModuleID] = Seq(
    "com.github.java-json-tools"   %  "json-schema-validator"     % "2.2.14",
    "uk.gov.hmrc.mongo"            %% "hmrc-mongo-play-30"        % mongoVersion,
    "uk.gov.hmrc"                  %% "bootstrap-backend-play-30" % bootstrapVersion,
    "org.typelevel"                %% "cats-core"                 % "2.13.0",
    "org.scala-lang.modules"       %% "scala-xml"                 % "2.3.0",
    "com.lucidchart"               %% "xtract"                    % "2.3.0",
    "org.apache.groovy"          %  "groovy"                    % "4.0.26",
    "com.chuusai"                  %% "shapeless"                 % "2.3.13",
    "com.fasterxml.jackson.module" %% "jackson-module-scala"      % "2.18.3"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"            %% "bootstrap-test-play-30"      % bootstrapVersion,
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-test-play-30"     % mongoVersion,
    "org.scalamock"          %% "scalamock"                   % "6.2.0",
    "org.scalacheck"         %% "scalacheck"                  % "1.18.1",
    "org.mockito"            %  "mockito-core"                % "5.16.1",
    "org.mockito"            %% "mockito-scala"               % "1.17.37",
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
