plugins {
    `java-library`
    `maven-publish`
}

val moduleName = "lavaplayer-natives"
version = "2.0.0"

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = moduleName
        }
    }
}

