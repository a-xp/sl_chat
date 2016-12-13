name := "server"

version := "1.0"

scalaVersion := "2.12.1"

organization := "ru.shoppinglive"

libraryDependencies ++= {
  val akkaVer = "2.4.14"
  val akkaHttpVer = "10.0.0"
  Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaVer,
    "com.typesafe.akka" %% "akka-slf4j" % akkaVer,
    "com.typesafe.akka" %% "akka-agent" % akkaVer,
    "com.typesafe.akka" %% "akka-http" % akkaHttpVer,
    "ch.qos.logback" % "logback-classic" % "1.1.3",
    "org.json4s" %% "json4s-native" % "3.5.0"
  )
}