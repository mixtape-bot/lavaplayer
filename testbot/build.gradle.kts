plugins {
    java
    application
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.6.10")

    implementation(project(":main"))

    implementation("net.dv8tion:JDA:4.3.0_285") {
        exclude(group = "club.minnced", module = "opus-java")
    }

    implementation("ch.qos.logback:logback-classic:1.2.10")
}

application {
    mainClass.set("lavaplayer.demo.Bootstrap")
}
