plugins {
    alias(libs.plugins.neoforge.moddev)
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
        this["minecraft"] = "[%s,%s]".format(*prop("mod.minecraft").split('-').toTypedArray())
        this["java"] = "[${requiredJava},)"
    }

    filesMatching(listOf("fabric.mod.json", "META-INF/neoforge.mods.toml", "META-INF/mods.toml")) {
        expand(props)
    }
}

version = "${property("mod.version")}+${property("mod.minecraft")}-neoforge"
base.archivesName = property("mod.id") as String

jsonlang {
    languageDirectories = listOf("assets/${property("mod.id")}/lang")
    prettyPrint = true
}

repositories {
    maven("https://maven.parchmentmc.org") { name = "ParchmentMC" }
}

neoForge {
    version = property("deps.neoforge") as String
    validateAccessTransformers = true

    if (hasProperty("deps.parchment")) {
        parchment {
            mappingsVersion = property("deps.parchment") as String
            minecraftVersion = stonecutter.current.version
        }
    }

    runs {
        register("client") {
            jvmArgument("-Dmixin.debug.export=true")
            gameDirectory = file("../../run/")
            client()
        }
    }

    mods {
        register(property("mod.id") as String) {
            sourceSet(sourceSets["main"])
        }
    }
    sourceSets["main"].resources.srcDir("src/main/generated")
}

tasks {
    processResources {
        exclude("**/fabric.mod.json", "**/*.accesswidener", "**/mods.toml")
    }

    named("createMinecraftArtifacts") {
        dependsOn("stonecutterGenerate")
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        from(jar.map { it.archiveFile })
        into(rootProject.layout.buildDirectory.file("libs/${project.property("mod.version")}"))
        dependsOn("build")
    }
}

java {
    //withSourcesJar()
    sourceCompatibility = requiredJava
    targetCompatibility = requiredJava
}

val additionalVersionsStr = findProperty("publish.versions") as String?
val additionalVersions: List<String> = additionalVersionsStr
    ?.split(",")
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() }
    ?: emptyList()

publishMods {
    file = tasks.jar.map { it.archiveFile.get() }

    type = STABLE
    displayName = "${property("mod.name")} v${property("mod.version")} for ${property("mod.minecraft")} Neoforge"
    version = "${property("mod.version")}+${property("mod.minecraft")}-neoforge"
    modLoaders.add("neoforge")

    modrinth {
        projectId = property("publish.modrinth") as String
        accessToken = env.MODRINTH_API_KEY.orNull()
        minecraftVersions.add(stonecutter.current.version)
        minecraftVersions.addAll(additionalVersions)
    }

    curseforge {
        projectId = property("publish.curseforge") as String
        accessToken = env.CURSEFORGE_API_KEY.orNull()
        minecraftVersions.add(stonecutter.current.version)
        minecraftVersions.addAll(additionalVersions)
    }
}
