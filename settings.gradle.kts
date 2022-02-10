include("common")
include("main")
include("track-info")
include("natives")
include("testbot")
include("downloader")
include(":extensions:ip-rotator")

pluginManagement {
    resolutionStrategy {
        repositories {
            gradlePluginPortal()
            maven("https://maven.dimensional.fun/releases")
        }

        eachPlugin {
            if (requested.id.id == "kotlinx-atomicfu") {
                useModule("org.jetbrains.kotlinx:atomicfu-gradle-plugin:${requested.version}")
            }
        }
    }
}
