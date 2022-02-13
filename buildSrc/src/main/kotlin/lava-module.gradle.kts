import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java

    kotlin("jvm")
    kotlin("plugin.serialization")
}

repositories {
    maven("https://maven.dimensional.fun/releases")
    maven("https://oss.sonatype.org/content/repositories/snapshots")
    maven("https://m2.dv8tion.net/releases")
    maven("https://jitpack.io")

    mavenLocal()
    mavenCentral()
}

tasks {
    withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = Compiler.JVM_TARGET
            freeCompilerArgs = Compiler.ARGS.map { "-Xopt-in=$it" }
        }
    }

    withType<JavaCompile> {
        sourceCompatibility = Compiler.JVM_TARGET
        targetCompatibility = Compiler.JVM_TARGET
    }

    java {
        // We don't use java, but this prevents a Gradle warning,
        // telling you to target the same java version for java and kt
        sourceCompatibility = JavaVersion.VERSION_11
    }
}
