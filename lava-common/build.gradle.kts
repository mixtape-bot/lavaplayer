plugins {
    `lava-module`
    `lava-published-module`
}

val moduleName = "lava-common"
version = "1.2.9"

dependencies {
    implementation(libs.bundles.common)
    implementation(libs.kotlin.logging)
    implementation(libs.apache.commons.io)

    api(libs.slf4j.api)
}

kotlin {
    explicitApi()
}

val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets["main"].allSource)
}

publishing {
    publications {
        create<MavenPublication>("Common") {
            from(components["java"])
            artifactId = moduleName
            artifact(sourcesJar)
        }
    }
}
