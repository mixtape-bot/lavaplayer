plugins {
    groovy
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
    maven("https://maven.dimensional.fun/releases")
}

dependencies {
    implementation(kotlin("gradle-plugin", version = "1.6.10"))
    implementation(kotlin("serialization", version = "1.6.10"))
    implementation("org.jetbrains.kotlinx", "atomicfu-gradle-plugin", "0.17.0")
    implementation("fun.dimensional.gradle", "gradle-tools", "1.0.2")

    implementation(gradleApi())
    implementation(localGroovy())
}
