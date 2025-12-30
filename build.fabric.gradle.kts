@file:Suppress("UnstableApiUsage")

import org.gradle.kotlin.dsl.register


plugins {
    alias(libs.plugins.fabric.loom)
    alias(libs.plugins.jsonlang)
    alias(libs.plugins.modpublish)
}

val requiredJava = when {
    stonecutter.current.parsed >= "1.20.6" -> JavaVersion.VERSION_21
    stonecutter.current.parsed >= "1.18" -> JavaVersion.VERSION_17
    stonecutter.current.parsed >= "1.17" -> JavaVersion.VERSION_16
    else -> JavaVersion.VERSION_1_8
}

tasks.named<ProcessResources>("processResources") {
    fun prop(name: String) = project.property(name) as String

    val props = HashMap<String, String>().apply {
        this["version"] = prop("mod.version")
        this["minecraft"] = ">=%s <=%s".format(*prop("mod.minecraft").split('-').toTypedArray())
        this["java"] = ">=${requiredJava}"
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
    maven("https://maven.parchmentmc.org") { name = "ParchmentMC" }
}

dependencies {
    minecraft("com.mojang:minecraft:${stonecutter.current.version}")
    mappings(loom.layered {
        officialMojangMappings()
        if (hasProperty("deps.parchment"))
            parchment("org.parchmentmc.data:parchment-${stonecutter.current.version}:${property("deps.parchment")}@zip")
    })
    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric-loader")}")
}

loom {
    //fabricModJsonPath = rootProject.file("src/main/resources/fabric.mod.json")

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

val versionsStr = findProperty("publish.versions") as String?
val versions: List<String> = versionsStr
    ?.split(",")
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() }
    ?: emptyList()

publishMods {
    file = tasks.remapJar.map { it.archiveFile.get() }
    additionalFiles.from(tasks.remapSourcesJar.map { it.archiveFile.get() })

    type = STABLE
    displayName = "${property("mod.name")} v${property("mod.version")} for ${property("mod.minecraft")} Fabric"
    version = "${property("mod.version")}+${property("mod.minecraft")}-fabric"
    modLoaders.add("fabric")

    modrinth {
        projectId = property("publish.modrinth") as String
        accessToken = env.MODRINTH_API_KEY.orNull()
        minecraftVersions.addAll(versions)
    }

    curseforge {
        projectId = property("publish.curseforge") as String
        accessToken = env.CURSEFORGE_API_KEY.orNull()
        minecraftVersions.addAll(versions)
    }
}
