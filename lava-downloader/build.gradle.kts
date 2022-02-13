plugins {
    application
    `lava-module`

    id("com.github.johnrengelman.shadow") version "7.1.2"
}

dependencies {
    implementation(project(":lavaplayer"))

    implementation(libs.bundles.common)

    implementation(libs.kotlin.logging)
    implementation(libs.jaffree)
    implementation(libs.logback)
    implementation(libs.okio)

    api(libs.slf4j.api)
}

application {
    mainClass.set("lavaplayer.downloader.MainKt")
}
