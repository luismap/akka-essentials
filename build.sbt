ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.8"

val akkaVersion = "2.5.13"

lazy val root = (project in file("."))
  .settings(
    name := "rjvm-akka-essentials"
  )
