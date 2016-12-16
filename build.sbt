organization := "com.github.Darkyenus" //Default jitpack organization

name := "ResourcePacker"

version := "1.10-SNAPSHOT"

scalaVersion := "2.11.7"

startYear := Some(2014)

scalacOptions ++= Seq("-deprecation","-feature","-target:jvm-1.7")

javacOptions ++= Seq("-source","7","-target","7")

val gdxVersion = "1.9.5"

scalaSource in Compile := baseDirectory.value / "src"

resourceDirectory in Compile := baseDirectory.value / "resources"

libraryDependencies ++= Seq(
	"com.badlogicgames.gdx" % "gdx" % gdxVersion,
	"com.badlogicgames.gdx" % "gdx-backend-lwjgl3" % gdxVersion,
	"com.badlogicgames.gdx" % "gdx-platform" % gdxVersion classifier "natives-desktop",
	"com.badlogicgames.gdx" % "gdx-freetype" % gdxVersion,
	"com.badlogicgames.gdx" % "gdx-freetype-platform" % gdxVersion classifier "natives-desktop",
	"com.esotericsoftware" % "minlog" % "1.3.0",
	"com.google.guava" % "guava" % "17.0",
	"org.apache.xmlgraphics" % "batik-transcoder" % "1.7",
	"org.apache.xmlgraphics" % "batik-codec" % "1.7"
)

fork in Test := true

//javaOptions in Test += "-XstartOnFirstThread"

javacOptions += "-g"

javaOptions in Test += "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"

scalaSource in Test := baseDirectory.value / "testsrc"

resourceDirectory in Test := baseDirectory.value / "testresources"
