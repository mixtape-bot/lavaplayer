plugins {
    `lava-module`
    `lava-published-module`
}

val moduleName = "lavaplayer-ext-ip-rotator"
version = "0.3.1"

kotlin {
    explicitApi()
}

dependencies {
    compileOnly(project(":lavaplayer"))

    implementation(libs.bundles.common)

    implementation(libs.kotlin.logging)

    api(libs.slf4j.api)
}

val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets["main"].allSource)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = moduleName
            artifact(sourcesJar)
        }
    }
}
