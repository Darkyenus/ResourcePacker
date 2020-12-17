@file:Suppress("unused")
import wemi.compile.JavaCompilerFlags
import wemi.configuration
import wemi.dependency.NoClassifier
import wemi.publish.artifacts
import wemi.util.SystemInfo

val ResourcePacker by project {

	projectGroup set { "com.darkyen" }
	projectName set { "ResourcePacker" }
	projectVersion set { "2.6-SNAPSHOT" }

	compilerOptions[JavaCompilerFlags.sourceVersion] = { "7" }
	compilerOptions[JavaCompilerFlags.targetVersion] = { "7" }

	publishMetadata modify {
		it.apply {
			child("inceptionYear", "2014")
		}
	}

	val gdxVersion = "1.9.10"
	val lwjglVersion = "3.2.3"

	libraryDependencies addAll { setOf(
			dependency("com.badlogicgames.gdx", "gdx", gdxVersion),
			dependency("com.badlogicgames.gdx", "gdx-backend-lwjgl3", gdxVersion, scope = ScopeTest),
			dependency("com.badlogicgames.gdx", "gdx-platform", gdxVersion, classifier ="natives-desktop"),
			dependency("com.badlogicgames.gdx", "gdx-freetype", gdxVersion),
			dependency("com.badlogicgames.gdx", "gdx-freetype-platform", gdxVersion, classifier="natives-desktop"),
			dependency("com.esotericsoftware", "minlog", "1.3.1"),
			dependency("org.apache.xmlgraphics", "batik-transcoder", "1.12"),
			dependency("org.apache.xmlgraphics", "batik-codec", "1.12"),
			dependency("org.lwjgl", "lwjgl-stb", lwjglVersion),
			dependency("org.lwjgl", "lwjgl-stb", lwjglVersion, classifier="natives-macos"),
			dependency("org.lwjgl", "lwjgl-stb", lwjglVersion, classifier="natives-linux"),
			dependency("org.lwjgl", "lwjgl-stb", lwjglVersion, classifier="natives-windows")
	) }

	runOptions add { "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005" }

	extend(testing) {
		if (SystemInfo.IS_MAC_OS) {
			runOptions add { "-XstartOnFirstThread" }
		}
	}

	// Workaround for Wemi 0.11 which broke this
	publishArtifacts set { artifacts(NoClassifier, includeSources = false, includeDocumentation = false) }
}

// ./wemi testing:resourcePackTest:run
val resourcePackTest by configuration("Test of Resource Packer") {
	mainClass set { "ResourcePackerTestKt" }
}

// ./wemi testing:usingResourcePackTest:run
val usingResourcePackTest by configuration("Test using results of resourcePackTest") {
	mainClass set { "UsingPackedResourcesTestKt" }
}

// ./wemi testing:freeTypePackerTest:run
val freeTypePackerTest by configuration("Test of FreeType packer") {
	mainClass set { "FreeTypePackerTest" }
}
