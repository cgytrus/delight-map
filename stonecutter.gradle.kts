plugins {
    id("dev.kikugie.stonecutter")
    alias(libs.plugins.dotenv)
    alias(libs.plugins.fabric.loom) apply false
    alias(libs.plugins.neoforge.moddev) apply false
    alias(libs.plugins.jsonlang) apply false
    alias(libs.plugins.modpublish) apply false
}

stonecutter active "1.14.4-fabric"

stonecutter parameters {
    constants.match(node.metadata.project.substringAfterLast('-'), "fabric", "neoforge")
}

stonecutter tasks {
    order("publishModrinth")
    //order("publishCurseforge")
}

for (version in stonecutter.versions.map { it.version }.distinct()) tasks.register("publish$version") {
    group = "publishing"
    dependsOn(stonecutter.tasks.named("publishMods") { metadata.version == version })
}
