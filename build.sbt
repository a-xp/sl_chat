name := "server"

version := "1.0"

scalaVersion := "2.11.8"

organization := "ru.shoppinglive"

libraryDependencies ++= {
  val akkaVer = "2.4.14"
  val akkaHttpVer = "10.0.0"
  Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaVer,
    "com.typesafe.akka" %% "akka-slf4j" % akkaVer,
    "com.typesafe.akka" %% "akka-agent" % akkaVer,
    "com.typesafe.akka" %% "akka-persistence" % akkaVer,
    "com.typesafe.akka" %% "akka-persistence-query-experimental" % akkaVer,
    "com.typesafe.akka" %% "akka-http" % akkaHttpVer,
    "ch.qos.logback" % "logback-classic" % "1.1.3",
    "org.json4s" %% "json4s-native" % "3.5.0",
    "ch.megard" %% "akka-http-cors" % "0.1.10",
    "com.github.scullxbones" %% "akka-persistence-mongo-casbah" % "1.3.6",
    "org.mongodb" %% "casbah" % "3.1.1"
  )
}