@file:Suppress("UnstableApiUsage")

import org.gradle.kotlin.dsl.register
import kotlin.collections.component1

plugins {
    alias(libs.plugins.fabric.loom)
    alias(libs.plugins.jsonlang)
    alias(libs.plugins.modpublish)
}

val (minVersion, maxVersion) = (property("mod.minecraft") as String).split('-')

val requiredJava = when {
    stonecutter.eval(minVersion, ">=1.20.5") -> JavaVersion.VERSION_21
    stonecutter.eval(minVersion, ">=1.18") -> JavaVersion.VERSION_17
    stonecutter.eval(minVersion, ">=1.17") -> JavaVersion.VERSION_16
    else -> JavaVersion.VERSION_1_8
}

stonecutter.replacements.regex(requiredJava.isJava9Compatible) {
    replace(
        " \\((.+) \\* (.+?) \\+ (.+?)\\)" to $$" Math.fma($1, $2, $3)",
        " Math\\.fma\\((.*?), (.*?), (.*?)\\)" to $$" ($1 * $2 + $3)"
    )
}

tasks.named<ProcessResources>("processResources") {
    fun prop(name: String) = project.property(name) as String

    val props = HashMap<String, String>().apply {
        this["version"] = prop("mod.version")
        this["java"] = ">=${requiredJava.majorVersion}"
    }

    filesMatching(listOf("fabric.mod.json", "META-INF/neoforge.mods.toml", "META-INF/mods.toml")) {
        expand(props)
    }
}

version = "${property("mod.version")}+${property("mod.minecraft")}-fabric"
base.archivesName = property("mod.id") as String

jsonlang {
    languageDirectories = listOf("assets/${property("mod.id")}/lang")
    prettyPrint = true
}

repositories {
    mavenLocal()
}

dependencies {
    minecraft("com.mojang:minecraft:${stonecutter.current.version}")
    mappings(loom.layered {
        officialMojangMappings()
    })
    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric-loader")}")
}

loom {
    fabricModJsonPath = rootProject.file("src/main/resources/fabric.mod.json")
    accessWidenerPath = rootProject.file("src/main/resources/delightmap.classtweaker")

    decompilerOptions.named("vineflower") {
        options.put("mark-corresponding-synthetics", "1")
    }

    runConfigs.named("client") {
        ideConfigGenerated(true)
        vmArgs("-Dmixin.debug.export=true")
        runDir = "../../run"
    }
}

tasks {
    processResources {
        exclude("**/neoforge.mods.toml", "**/mods.toml")
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        from(remapJar.map { it.archiveFile })
        into(rootProject.layout.buildDirectory.file("libs/${project.property("mod.version")}"))
        dependsOn("build")
    }
}

java {
    sourceCompatibility = requiredJava
    targetCompatibility = requiredJava
}

publishMods {
    file = tasks.remapJar.map { it.archiveFile.get() }

    type = STABLE
    displayName = "${property("mod.name")} v${property("mod.version")} for ${property("mod.minecraft")} Fabric"
    version = "${property("mod.version")}+${property("mod.minecraft")}-fabric"
    changelog = provider { rootProject.file("CHANGELOG.md").readText() }
    modLoaders.add("fabric")

    modrinth {
        projectId = property("publish.modrinth") as String
        accessToken = providers.environmentVariable("MODRINTH_TOKEN")
        minecraftVersionRange {
            start = minVersion
            end = maxVersion
        }
    }
}
