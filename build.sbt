name         := "martian-robots"
version      := "0.1.0"
scalaVersion := "3.3.6"

scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked", "-Wunused:all")

libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.19" % Test

// `sbt run` behaves like a Unix filter: the program runs in its own JVM with sbt's stdin
// connected to it, and its stdout passed straight through rather than via sbt's logger.
run / fork           := true
run / connectInput   := true
run / outputStrategy := Some(StdoutOutput)

// `sbt assembly` builds a standalone jar: java -jar target/scala-3.3.6/martian-robots.jar
assembly / assemblyJarName := "martian-robots.jar"
