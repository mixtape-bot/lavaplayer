import lol.dimensional.gradle.dsl.Version
import lol.dimensional.gradle.dsl.ReleaseType

plugins {
    `lava-module`
    `lava-published-module`
}

val moduleName = "lava-track-info"
val versionDef = Version(1, 0, 1, release = ReleaseType.Final)

project.version = versionDef

dependencies {
    implementation(libs.bundles.common)

    implementation(libs.kotlin.logging)
    implementation(libs.kx.ser.core)

    api(libs.slf4j.api)
    api(libs.lava.common)
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
