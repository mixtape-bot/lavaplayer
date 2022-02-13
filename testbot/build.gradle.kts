plugins {
    application
    `lava-module`
}

dependencies {
    implementation(project(":lavaplayer"))

    implementation(libs.bundles.common)

    implementation(libs.logback)
    implementation(libs.jda) {
        exclude(group = "club.minnced", module = "opus-java")
    }
}

application {
    mainClass.set("lavaplayer.demo.Bootstrap")
}
