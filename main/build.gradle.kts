import lol.dimensional.gradle.dsl.Version
import lol.dimensional.gradle.dsl.ReleaseType

plugins {
    `java-library`
    `maven-publish`

    kotlin("plugin.serialization")
}

val moduleName = "lavaplayer"
val versionDef = Version(1, 6, 2, release = ReleaseType.Final)

project.version = versionDef

dependencies {
    /* kotlin */
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.2")

    /* lavaplayer */
    implementation("com.sedmelluq:lavaplayer-natives:2.0.0")
    api("com.sedmelluq:lava-track-info:1.0.1")
    api("com.sedmelluq:lava-common:1.2.9")

    /* other */
    implementation("com.github.walkyst.JAADec-fork:jaadec-ext-aac:0.1.3")
    implementation("org.jetbrains.kotlinx:atomicfu:0.17.0")
    implementation("commons-io:commons-io:2.11.0")
    implementation("org.jsoup:jsoup:1.14.3")
    implementation("io.github.microutils:kotlin-logging:2.1.21")

    api("org.slf4j:slf4j-api:1.7.35")
    api("org.apache.httpcomponents:httpclient:4.5.13")

    /* test */
    testImplementation("ch.qos.logback:logback-classic:1.2.10")
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
