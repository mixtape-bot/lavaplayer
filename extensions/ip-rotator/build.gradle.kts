plugins {
    `java-library`
    `maven-publish`
}

val moduleName = "lavaplayer-ext-ip-rotator"
version = "0.3.1"

kotlin {
    explicitApi()
}

dependencies {
    compileOnly(project(":main"))
    implementation("io.github.microutils:kotlin-logging:2.1.0")

    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0-RC")
    api("org.slf4j:slf4j-api:1.7.32")
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
