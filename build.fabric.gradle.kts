@file:Suppress("UnstableApiUsage")

import org.gradle.kotlin.dsl.register

plugins {
    alias(libs.plugins.fabric.loom)
    alias(libs.plugins.jsonlang)
    alias(libs.plugins.modpublish)
}

val versionsStr = findProperty("mod.minecraft") as String?
val versions: List<String> = versionsStr
    ?.split(",")
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() }
    ?: emptyList()
val versionRange = if (versions.size == 1) {
    versions.first()
}
else {
    "${versions.first()}-${versions.last()}"
}

val requiredJava = when {
    stonecutter.eval(versions.first(), ">=1.20.5") -> JavaVersion.VERSION_21
    stonecutter.eval(versions.first(), ">=1.18") -> JavaVersion.VERSION_17
    stonecutter.eval(versions.first(), ">=1.17") -> JavaVersion.VERSION_16
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

version = "${property("mod.version")}+$versionRange-fabric"
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
    //withSourcesJar()
    sourceCompatibility = requiredJava
    targetCompatibility = requiredJava
}

publishMods {
    file = tasks.remapJar.map { it.archiveFile.get() }
    additionalFiles.from(tasks.remapSourcesJar.map { it.archiveFile.get() })

    type = STABLE
    displayName = "${property("mod.name")} v${property("mod.version")} for $versionRange Fabric"
    version = "${property("mod.version")}+$versionRange-fabric"
    changelog = provider { rootProject.file("CHANGELOG.md").readText() }
    modLoaders.add("fabric")

    modrinth {
        projectId = property("publish.modrinth") as String
        accessToken = providers.environmentVariable("MODRINTH_TOKEN")
        minecraftVersions.addAll(versions)
    }

    github {
        accessToken = providers.environmentVariable("GITHUB_TOKEN")
        repository = "cgytrus/delight-map"
        commitish = "main"
        tagName = "v${property("mod.version")}"
    }
}
