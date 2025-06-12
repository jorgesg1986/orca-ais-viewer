ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.16"

val flywayVersion = "11.10.0"
val pekkoVersion = "1.1.4"
val pekkoHttpVersion = "1.2.0"
val slickVersion = "3.6.1"

lazy val root = (project in file("backend"))
  .settings(
    name := "orca-ais-viewer",
    assembly / mainClass := Some("com.orca.ais.viewer.Server"),
    assembly / assemblyJarName := "app.jar",
    libraryDependencies ++= Seq(
      "org.apache.pekko"    %% "pekko-actor-typed"          % pekkoVersion,
      "org.apache.pekko"    %% "pekko-stream"               % pekkoVersion,
      "org.apache.pekko"    %% "pekko-stream-typed"         % pekkoVersion,
      "org.apache.pekko"    %% "pekko-coordination"         % pekkoVersion,
      "org.apache.pekko"    %% "pekko-distributed-data"     % pekkoVersion,
      "org.apache.pekko"    %% "pekko-persistence"          % pekkoVersion,
      "org.apache.pekko"    %% "pekko-remote"               % pekkoVersion,
      "org.apache.pekko"    %% "pekko-http"                 % pekkoHttpVersion,
      "org.apache.pekko"    %% "pekko-http-spray-json"      % pekkoHttpVersion,
      "ch.qos.logback"       % "logback-classic"            % "1.5.18",
      "com.typesafe.slick"  %% "slick"                      % slickVersion,
      "com.typesafe.slick"  %% "slick-hikaricp"             % slickVersion,
      "com.github.tminglei" %% "slick-pg"                   % "0.23.1",
      "com.github.tminglei" %% "slick-pg_jts"               % "0.23.1",
      "org.postgresql"       % "postgresql"                 % "42.7.7",
      "net.postgis"          % "postgis-jdbc"               % "2025.1.1",
      "org.orbisgis"         % "postgis-jts"                % "2.2.3",
      "org.flywaydb"         % "flyway-core"                % flywayVersion,
      "org.flywaydb"         % "flyway-database-postgresql" % flywayVersion,
      "fr.davit"            %% "pekko-http-metrics-prometheus" % "2.1.0",

      "org.apache.pekko"    %% "pekko-http-testkit"         % pekkoHttpVersion % Test,
      "org.apache.pekko"    %% "pekko-actor-testkit-typed"  % pekkoVersion     % Test,
      "org.apache.pekko"    %% "pekko-stream-testkit"       % pekkoVersion     % Test,
      "org.scalatest"       %% "scalatest"                  % "3.2.19"         % Test,
      "org.scalacheck"      %% "scalacheck"                 % "1.18.1"         % Test,
      "com.spotify"         %% "magnolify-shared"           % "0.8.0"          % Test,
      "com.spotify"         %% "magnolify-scalacheck"       % "0.8.0"          % Test,
      "dev.optics"          %% "monocle-core"               % "3.3.0"          % Test,
      "dev.optics"          %% "monocle-macro"              % "3.3.0"          % Test,
      "org.mockito"         %% "mockito-scala"              % "2.0.0"          % Test,
      "org.mockito"         %% "mockito-scala-scalatest"    % "2.0.0"          % Test,
    )
  )

ThisBuild / assemblyMergeStrategy := {
  case PathList(ps @ _*) if ps.last endsWith "module-info.class" => MergeStrategy.first
  case PathList(ps @ _*) if ps.last endsWith ".proto" => MergeStrategy.first
  case PathList(ps @ _*) if ps.last endsWith ".properties" => MergeStrategy.first
  case x =>
    val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
    oldStrategy(x)
}
