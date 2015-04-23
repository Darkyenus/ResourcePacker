organization := "darkyenus"

name := "ResourcePacker"

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.5"

javaOptions += "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"

startYear := Some(2014)

scalacOptions ++= Seq("-deprecation","-feature","-target:jvm-1.6")

javacOptions ++= Seq("-source","6","-target","6")

val gdxVersion = "1.5.3"

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

fork in Test := true

scalaSource in Test := baseDirectory.value / "testsrc"

resourceDirectory in Test := baseDirectory.value / "testresources"
