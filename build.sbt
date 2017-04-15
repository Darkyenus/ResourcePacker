
organization := "com.github.Darkyenus" //Default jitpack organization

name := "ResourcePacker"

version := "2.0-SNAPSHOT"

startYear := Some(2014)

javacOptions ++= Seq("-source", "7", "-target", "7", "-g")

javacOptions in doc := Seq("-source", "7")

val gdxVersion = "1.9.6"

crossPaths := false

autoScalaLibrary := false

kotlinVersion := "1.1.1"

kotlinLib("stdlib")

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

javaOptions in Test += "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"
