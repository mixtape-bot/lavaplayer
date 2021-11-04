plugins {
    `java-library`
    `maven-publish`

    id("kotlinx-atomicfu")
}

val moduleName = "lava-common"
version = "1.2.2"

dependencies {
    implementation("io.github.microutils:kotlin-logging:2.0.11")
    implementation("commons-io:commons-io:2.11.0")

    api("org.slf4j:slf4j-api:1.7.32")
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
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = moduleName
            artifact(sourcesJar)
        }
    }
}
