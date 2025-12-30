pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.8.1"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    create(rootProject) {
        fun match(mc: String, vararg loaders: String) = loaders
            .forEach { version("$mc-$it", mc).buildscript = "build.$it.gradle.kts" }

        match("1.21.1", "fabric", "neoforge")
        match("1.20.4", "fabric", "neoforge")
        match("1.19.2", "fabric")
        match("1.18.2", "fabric")
        match("1.16.5", "fabric")
        match("1.15.2", "fabric")
        match("1.14.4", "fabric")

        vcsVersion = "1.21.1-fabric"
    }
}
