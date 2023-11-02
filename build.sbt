
ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.8"

Global / excludeLintKeys += test / fork
Global / excludeLintKeys += run / mainClass

val guavaVersion = "31.1-jre"
val scalaTestVersion = "3.2.11"
val typeSafeConfigVersion = "1.4.2"
val logbackVersion = "1.2.10"
val sfl4sVersion = "2.0.0-alpha5"
val netModelGeneratorVersion = "0.1.0-SNAPSHOT"
val hadoopCommonVersion = "3.3.2"
val hadoopHdfsClientVersion = "3.3.2"
val logbackClassicVersion = "1.1.3"
val sparkVersion = "3.5.0"

lazy val commonDependencies = Seq(
  "org.scalatest" %% "scalatest" % scalaTestVersion % Test,
  "org.scalatestplus" %% "mockito-4-2" % "3.2.12.0-RC2" % Test,
  "com.typesafe" % "config" % typeSafeConfigVersion,
  "com.google.guava" % "guava" % guavaVersion,
  "org.apache.hadoop" % "hadoop-core" % "1.2.1",
  "org.apache.hadoop" % "hadoop-common" % "3.3.5",
  "org.apache.hadoop" % "hadoop-mapreduce-client-core" % "3.3.5",
  "org.apache.hadoop" % "hadoop-mapreduce-client-jobclient" % "3.3.5",
  "org.yaml" % "snakeyaml" % "2.0",
  "org.apache.spark" %% "spark-core" % sparkVersion,
  "org.apache.spark" %% "spark-graphx" % sparkVersion,
  "org.apache.spark" %% "spark-sql" % sparkVersion
).map(_.exclude("org.slf4j", "*"))

lazy val root = (project in file("."))
  .settings(
    name := "HW2",
    libraryDependencies ++= Seq ("ch.qos.logback" % "logback-classic" % logbackClassicVersion),
    libraryDependencies ++= commonDependencies
  )

scalacOptions ++= Seq(
  // emit warning and location for usages of deprecated APIs
  "-deprecation",
  // explain type errors in more detail
  "--explain-types",
  // emit warning and location for usages of features that should be imported explicitly
  "-feature"
)

compileOrder := CompileOrder.JavaThenScala
test / fork := true
run / fork := true
run / javaOptions ++= Seq(
  "-Xms8G",
  "-Xmx100G",
  "-XX:+UseG1GC"
)

Compile / mainClass := Some("Main")
run / mainClass := Some("Main")

assembly / assemblyJarName := "HW2.jar"

ThisBuild / assemblyMergeStrategy := {
  case PathList("META-INF", _*) => MergeStrategy.discard
  case _ => MergeStrategy.first
}
