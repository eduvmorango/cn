name         := "cn"
organization := "edvmorango"

val compilerOptions = Seq(
  "-deprecation",   // emit warning and location for usages of deprecated APIs
  "-explain",       // explain errors in more detail
  "-explain-types", // explain type errors in more detail
  "-feature",       // emit warning and location for usages of features that should be imported explicitly
  "-indent",        // allow significant indentation.
  "-new-syntax",    // require `then` and `do` in control expressions.
  "-print-lines",   // show source code line numbers.
  "-unchecked",     // enable additional warnings where generated code depends on assumptions
  "-Xmigration",
  "-Wunused:imports"
)

Global / scalaVersion := "3.4.2"
Global / onChangedBuildSource := ReloadOnSourceChanges

testFrameworks += new TestFramework("weaver.framework.CatsEffect")

val CatsCore      = "2.12.0"
val CatsEffect    = "3.5.4"
val CatsEffectMtl = "1.4.0"
val CatsTime      = "0.5.1"
val CirceCore     = "0.14.8"
val Kittens       = "3.3.0"
val Fs2Core       = "3.10.2"
val Iron          = "2.4.0"
val Ciris         = "3.5.0"
val Log4Cats      = "2.6.0"
val Logback       = "1.4.14"
val WeaverTest    = "0.8.4"
val Monocle       = "3.2.0"
val Http4s        = "0.23.25"
val Tapir         = "1.9.6"
val Discipline    = "1.7.0"
val SttpClient    = "3.9.1"
val Ducktape      = "0.1.11"

lazy val core = (project in file("modules/core"))
  .settings(
    name              := "core",
    scalacOptions ++= compilerOptions,
    semanticdbEnabled := true,
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core"     % CatsCore,
      "org.typelevel" %% "cats-effect"   % CatsEffect,
      "org.typelevel" %% "cats-mtl"      % CatsEffectMtl,
      "org.typelevel" %% "cats-time"     % CatsTime,
      "org.typelevel" %% "kittens"       % Kittens,
      "co.fs2"        %% "fs2-core"      % Fs2Core,
      "dev.optics"    %% "monocle-core"  % Monocle,
      "io.circe"      %% "circe-core"    % CirceCore,
      "io.circe"      %% "circe-generic" % CirceCore,
      "io.circe"      %% "circe-parser"  % CirceCore
    ),
    run / fork        := true,
    Test / fork       := true
  )

lazy val testkit = (project in file("modules/testkit"))
  .dependsOn(core)
  .settings(
    name              := "testkit",
    scalacOptions ++= compilerOptions,
    semanticdbEnabled := true,
    libraryDependencies ++= Seq(
      "com.disneystreaming" %% "weaver-cats"       % WeaverTest,
      "com.disneystreaming" %% "weaver-discipline" % WeaverTest,
      "org.typelevel"       %% "discipline-core"   % Discipline
    ),
    run / fork        := true,
    Test / fork       := true
  )
