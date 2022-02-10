plugins {
    java
    application

    id("com.github.johnrengelman.shadow") version "7.1.2"
}

dependencies {
    implementation(project(":main"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.6.10")
    implementation("com.github.kokorin.jaffree:jaffree:2021.12.30")
    implementation("ch.qos.logback:logback-classic:1.2.10")
    implementation("com.squareup.okio:okio:1.17.2")
}

application {
    mainClass.set("lavaplayer.downloader.MainKt")
}
