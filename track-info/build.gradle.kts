import lol.dimensional.gradle.dsl.Version
import lol.dimensional.gradle.dsl.ReleaseType

plugins {
    `java-library`
    `maven-publish`

    kotlin("plugin.serialization")
}

val moduleName = "lava-track-info"
val versionDef = Version(1, 0, 1, release = ReleaseType.Final)

project.version = versionDef

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.3.2")
    implementation("io.github.microutils:kotlin-logging:2.1.21")

    api("org.slf4j:slf4j-api:1.7.33")
    api("com.sedmelluq:lava-common:1.2.9")
}

val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets["main"].allSource)
}

publishing {
    publications {
        create<MavenPublication>("TrackInfo") {
            from(components["java"])

            artifactId = moduleName
            version = versionDef.asString()

            artifact(sourcesJar)
        }
    }
}
