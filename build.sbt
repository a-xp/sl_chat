name := "server"

version := "1.0"

scalaVersion := "2.11.8"

organization := "ru.shoppinglive"

resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

libraryDependencies ++= {
  val akkaVer = "2.4.14"
  val akkaHttpVer = "10.0.0"
  val kamonVersion = "0.6.0"
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
    "com.github.ironfish" %% "akka-persistence-mongo"  % "1.0.0-SNAPSHOT" % "compile",
    "org.mongodb" %% "casbah" % "3.1.1",
    "org.scaldi" %% "scaldi-akka" % "0.5.8",
    "io.kamon" %% "kamon-core" % kamonVersion,
    "io.kamon" %% "kamon-akka" % kamonVersion,
    "io.kamon" %% "kamon-statsd" % kamonVersion,
    "io.kamon" %% "kamon-system-metrics" % kamonVersion,
    "org.aspectj" % "aspectjweaver" % "1.8.2"
  )
}

aspectjSettings
AspectjKeys.compileOnly in Aspectj := true
products in Compile <++= products in Aspectj
javaOptions in run <++= AspectjKeys.weaverOptions in Aspectj

mergeStrategy in assembly := AssemblyMergeStrategies.customMergeStrategy