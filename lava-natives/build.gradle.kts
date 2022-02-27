plugins {
    `lava-module`
    `lava-published-module`
}

val moduleName = "lava-natives"
version = "2.0.0"

publishing {
    publications {
        create<MavenPublication>("Natives") {
            from(components["java"])
            artifactId = moduleName
        }
    }
}

