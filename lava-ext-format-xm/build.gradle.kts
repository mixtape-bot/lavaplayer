plugins {
    `lava-module`
    `lava-published-module`
}

val moduleName = "lavaplayer-ext-format-xm"
version = "0.1.0"

kotlin {
    explicitApi()
}

dependencies {
    compileOnly(project(":lavaplayer"))

    implementation(libs.bundles.common)

    implementation(libs.kotlin.logging)
    implementation(libs.ibxm)

    api(libs.slf4j.api)
}

val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets["main"].allSource)
}

publishing {
    publications {
        create<MavenPublication>("ExtFormatXM") {
            from(components["java"])
            artifactId = moduleName
            artifact(sourcesJar)
        }
    }
}
