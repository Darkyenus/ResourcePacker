organization := "darkyenus"

scalaVersion := "2.11.5"

javaOptions += "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"

fork in run := true

exportJars := true

startYear := Some(2014)

resolvers += "Maven Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

scalacOptions ++= Seq("-deprecation","-feature","-target:jvm-1.6")

javacOptions ++= Seq("-source","6","-target","6")

val gdxVersion = "1.5.3"

name := "RiverpebbleFrameworkTools"

version := "1.0-SNAPSHOT"

crossScalaVersions in Compile := Seq(scalaVersion.value, "2.10.4")

scalaSource in Compile := baseDirectory.value / "src"

resourceDirectory in Compile := baseDirectory.value / "resources"

libraryDependencies ++= Seq(
	"com.badlogicgames.gdx" % "gdx" % gdxVersion,
	"com.badlogicgames.gdx" % "gdx-backend-lwjgl" % gdxVersion,
	"com.badlogicgames.gdx" % "gdx-platform" % gdxVersion classifier "natives-desktop",
	"com.badlogicgames.gdx" % "gdx-tools" % gdxVersion,
	"com.esotericsoftware" % "minlog" % "1.3.0",
	"com.google.guava" % "guava" % "17.0",
	"org.apache.xmlgraphics" % "batik-transcoder" % "1.7",
	"org.apache.xmlgraphics" % "batik-codec" % "1.7"
)
