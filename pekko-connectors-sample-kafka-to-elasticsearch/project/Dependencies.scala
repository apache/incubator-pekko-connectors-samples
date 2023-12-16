import sbt._

object Dependencies {
  val scalaVer = "2.13.12"
  // #deps
  val PekkoVersion = "2.6.19"
  val PekkoConnectorsVersion = "4.0.0"
  val AlpakkaKafkaVersion = "3.0.1"

  // #deps

  val dependencies = List(
  // #deps
    "com.lightbend.akka" %% "akka-stream-alpakka-elasticsearch" % PekkoConnectorsVersion,
    "com.typesafe.akka" %% "akka-stream-kafka" % AlpakkaKafkaVersion,
    "com.typesafe.akka" %% "akka-stream" % PekkoVersion,
    "com.typesafe.akka" %% "akka-actor-typed" % PekkoVersion,
    "com.typesafe.akka" %% "akka-actor" % PekkoVersion,
    // for JSON in Scala
    "io.spray" %% "spray-json" % "1.3.6",
    // for JSON in Java
    "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8" % "2.13.3",
    "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % "2.13.3",
    // Logging
    "com.typesafe.akka" %% "akka-slf4j" % PekkoVersion,
    "ch.qos.logback" % "logback-classic" % "1.2.13",
  // #deps
    "org.testcontainers" % "elasticsearch" % "1.17.3",
    "org.testcontainers" % "kafka" % "1.17.3"
  )
}
