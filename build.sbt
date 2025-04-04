ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.6.4"

lazy val root = (project in file("."))
  .settings(
    name := "GistLLMJ",
    fork := true,
    assembly / mainClass := Some("Main")
  )

assembly / assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x                             => MergeStrategy.first
}

lazy val catsDeps = Seq(
  "org.typelevel" %% "cats-core" % "2.13.0",
  "org.typelevel" %% "cats-effect" % "3.6.0"
)

lazy val javaFxDeps = Seq(
  "org.openjfx" % "javafx-base",
  "org.openjfx" % "javafx-graphics",
  "org.openjfx" % "javafx-fxml",
  "org.openjfx" % "javafx-controls",
  "org.openjfx" % "javafx-media",
  "org.openjfx" % "javafx-web",
  "org.openjfx" % "javafx-swing"
).map(_ % "25-ea+10")

lazy val pureconfigDeps = Seq(
  "com.github.pureconfig" %% "pureconfig-core",
  "com.github.pureconfig" %% "pureconfig-generic-scala3"
).map(_ % "0.17.8")

lazy val circeDeps = Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % "0.14.12")

lazy val sttpDeps = Seq(
  "com.softwaremill.sttp.client3" %% "cats" % "3.10.3"
)

lazy val fs2Deps = Seq(
  "co.fs2" %% "fs2-core" % "3.12.0",
  "co.fs2" %% "fs2-io" % "3.12.0"
)

lazy val langChainDeps = Seq(
  "dev.langchain4j" % "langchain4j",
  "dev.langchain4j" % "langchain4j-google-ai-gemini"
).map(_ % "1.0.0-beta2")

libraryDependencies ++= catsDeps ++ javaFxDeps ++ pureconfigDeps ++ circeDeps ++ sttpDeps ++ fs2Deps ++ langChainDeps
