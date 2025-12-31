plugins {
    id("dev.kikugie.stonecutter")
    alias(libs.plugins.fabric.loom) apply false
    alias(libs.plugins.neoforge.moddev) apply false
    alias(libs.plugins.jsonlang) apply false
    alias(libs.plugins.modpublish) apply false
}

stonecutter active "1.21.1-fabric"

stonecutter parameters {
    constants.match(node.metadata.project.substringAfterLast('-'), "fabric", "neoforge")
}

stonecutter tasks {
    order("publishModrinth")
    //order("publishCurseforge")
}
