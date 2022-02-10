import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import lol.dimensional.gradle.dsl.Version

buildscript {
    dependencies {
        classpath("fun.dimensional.gradle:gradle-tools:1.0.2")
    }
}

plugins {
    java
    `maven-publish`

    id("kotlinx-atomicfu") version "0.17.0" apply false

    kotlin("jvm")                  version "1.6.10" apply false
    kotlin("plugin.serialization") version "1.6.10" apply false
}

group = "com.sedmelluq"

allprojects {
    group = rootProject.group

    repositories {
        maven("https://maven.dimensional.fun/releases")
        maven("https://oss.sonatype.org/content/repositories/snapshots")
        maven("https://m2.dv8tion.net/releases")
        maven("https://jitpack.io")

        mavenLocal()
        mavenCentral()
    }

    apply(plugin = "kotlinx-atomicfu")
    apply(plugin = "kotlin")
    apply(plugin = "java")
    apply(plugin = "maven-publish")

    java {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    publishing {
        repositories {
            maven {
                url = uri((project.version as? Version)?.repository?.fullUrl ?: "https://maven.dimensional.fun/releases")

                authentication {
                    create<BasicAuthentication>("basic")
                }

                credentials {
                    username = System.getenv("MAVEN_ALIAS")?.toString()
                    password = System.getenv("MAVEN_TOKEN")?.toString()
                }
            }
        }
    }

    tasks.withType<KotlinCompile> {
        sourceCompatibility = "1.8"
        targetCompatibility = "1.8"

        kotlinOptions {
            jvmTarget = "1.8"
            incremental = true
            freeCompilerArgs = listOf("-Xopt-in=kotlin.RequiresOptIn")
        }
    }
}
