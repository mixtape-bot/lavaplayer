plugins {
    application
    `lava-module`
}

dependencies {
    implementation(project(":lavaplayer"))
    implementation(project(":lava-ext-format-xm"))

    implementation(libs.bundles.common)
    implementation("com.soywiz.korlibs.korau:korau-jvm:2.4.10")

    implementation(libs.logback)
    implementation(libs.jda) {
        exclude(group = "club.minnced", module = "opus-java")
    }
}

application {
    mainClass.set("lavaplayer.demo.Bootstrap")
}
