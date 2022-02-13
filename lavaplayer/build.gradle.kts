import lol.dimensional.gradle.dsl.Version
import lol.dimensional.gradle.dsl.ReleaseType

plugins {
    `lava-module`
    `lava-published-module`
}

val moduleName = "lavaplayer"
val versionDef = Version(1, 6, 2, release = ReleaseType.Final)

project.version = versionDef

dependencies {
    implementation(libs.bundles.common)

    implementation(libs.kotlin.logging)
    implementation(libs.kx.ser.json)
    implementation(libs.lava.natives)
    implementation(libs.apache.commons.io)
    implementation(libs.aac)
    implementation(libs.jsoup)

    api(libs.lava.track.info)
    api(libs.lava.common)
    api(libs.slf4j.api)
    api(libs.apache.http.components.client)

    testImplementation(libs.logback)
}

val updateVersion by tasks.registering {
    File("$projectDir/src/main/resources/com/sedmelluq/discord/lavaplayer/tools/version.txt").let {
        it.parentFile.mkdirs()
        it.writeText(versionDef.asString())
    }
}

tasks.classes.configure {
    finalizedBy(updateVersion)
}

val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets["main"].allSource)
}

publishing {
    publications {
        create<MavenPublication>("Lavaplayer") {
            from(components["java"])

            artifactId = moduleName
            version = versionDef.asString()

            artifact(sourcesJar)
        }
    }
}
