import play.sbt.PlayImport.{guice, jdbc}
import sbt.*

object LibDependencies {
  val libraryDependenciesCompile: Seq[ModuleID] = Seq(
    guice,
    jdbc,
    "org.mariadb.jdbc"         %  "mariadb-java-client" % "3.5.10",
    "org.playframework.anorm"  %% "anorm"               % "3.1.0",
    "org.typelevel"            %% "cats-core"           % "2.13.0",
    "com.password4j"           %  "password4j"          % "1.8.4",
    "uk.gov.hmrc"              %% "play-frontend-hmrc-play-30" % "13.15.0",
    "net.logstash.logback"     % "logstash-logback-encoder" % "9.0",
    "uk.gov.hmrc"              %% "http-verbs-play-30"      % "15.8.0",
    "org.apache.pekko"         %% "pekko-actor"             % play.core.PlayVersion.pekkoVersion,
    "net.sf.geographiclib"     % "GeographicLib-Java"       % "2.1",
    "io.opentelemetry.instrumentation" % "opentelemetry-instrumentation-annotations" % "2.31.1"
  )

  val libraryDependenciesTest: Seq[ModuleID] = Seq(
    "org.scalatestplus.play"  %% "scalatestplus-play" % "7.0.2",
    "org.scalatestplus"       %% "mockito-5-10"       % "3.2.18.0",
    "org.jsoup"               %  "jsoup"              % "1.23.2",
    "org.wiremock"            % "wiremock"            % "3.13.2"
  ).map(_ % Test)

  val all: Seq[ModuleID]  = libraryDependenciesCompile ++ libraryDependenciesTest
}