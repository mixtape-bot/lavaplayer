import lol.dimensional.gradle.dsl.Version
import org.gradle.authentication.http.BasicAuthentication
import org.gradle.kotlin.dsl.create

plugins {
    `maven-publish`
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
