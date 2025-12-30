pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        maven("https://maven.parchmentmc.org") { name = "ParchmentMC" }
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

        vcsVersion = "1.21.1-fabric"
    }
}
