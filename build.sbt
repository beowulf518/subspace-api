name := """terrella-api"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  cache,
  ws,
  filters,
  evolutions,
  "org.eclipse.jgit" % "org.eclipse.jgit" % "4.6.1.201703071140-r",
  "org.sangria-graphql" %% "sangria-relay" % "1.0.0",
  "org.sangria-graphql" %% "sangria-play-json" % "1.0.0",
  "com.google.firebase" % "firebase-admin" % "4.1.2",
  "com.typesafe.play" %% "play-slick" % "2.0.0",
  "com.typesafe.play" %% "play-slick-evolutions" % "2.0.0",
  "mysql" % "mysql-connector-java" % "5.1.38",
  "com.typesafe.slick" %% "slick" % "3.1.1",
  "org.slf4j" % "slf4j-nop" % "1.6.4",
  specs2 % Test
)

// Remove toplevel directory in zip distribution
topLevelDirectory := None


fork in run := true