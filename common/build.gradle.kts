plugins {
    `java-library`
    `maven-publish`
}

val moduleName = "lava-common"
version = "1.2.9"

dependencies {
    implementation("io.github.microutils:kotlin-logging:2.1.21")
    implementation("commons-io:commons-io:2.11.0")

    api("org.slf4j:slf4j-api:1.7.33")
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
