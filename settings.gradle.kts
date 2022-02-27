include("lavaplayer")
include("lava-downloader")
include("lava-common")
include("lava-track-info")
include("lava-natives")
include("lava-ext-ip-rotator")
include("lava-ext-format-xm")

include("testbot")

enableFeaturePreview("VERSION_CATALOGS")

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            common()
            logging()
            lava()
        }
    }
}

fun VersionCatalogBuilder.lava() {
    alias("lava-common").to("com.sedmelluq", "lava-common").version("1.2.9")
    alias("lava-natives").to("com.sedmelluq", "lava-natives").version("2.0.0")
    alias("lava-track-info").to("com.sedmelluq", "lava-track-info").version("1.0.1")
}

fun VersionCatalogBuilder.common() {
    version("kotlinx-coroutines", "1.6.0")

    /* serialization */
    alias("kx-ser-json").to("org.jetbrains.kotlinx", "kotlinx-serialization-json").version("1.3.1")
    alias("kx-ser-core").to("org.jetbrains.kotlinx", "kotlinx-serialization-core").version("1.3.1")

    /* kotlin */
    alias("kotlinx-coroutines").to("org.jetbrains.kotlinx", "kotlinx-coroutines-core").versionRef("kotlinx-coroutines")
    alias("kotlinx-atomicfu").to("org.jetbrains.kotlinx", "atomicfu").version("0.17.0")
    alias("kotlin-reflect").to("org.jetbrains.kotlin", "kotlin-reflect").version("1.6.10")

    // apache
    alias("apache-commons-io").to("commons-io", "commons-io").version("2.11.0")
    alias("apache-http-components-client").to("org.apache.httpcomponents", "httpclient").version("4.5.13")

    // misc
    alias("okio").to("com.squareup.okio", "okio").version("1.17.2")
    alias("jaffree").to("com.github.kokorin.jaffree", "jaffree").version("2021.12.30")
    alias("jsoup").to("org.jsoup", "jsoup").version("1.14.3")
    alias("jda").to("net.dv8tion", "JDA").version("4.4.0_352")

    alias("aac").to("com.github.walkyst.JAADec-fork", "jaadec-ext-aac").version("0.1.3")
    alias("ibxm").to("com.github.walkyst", "ibxm-fork").version("a75")

    /* bundles */
    bundle("common", listOf("kotlinx-coroutines", "kotlinx-atomicfu", "kotlin-reflect"))
}

/* logging */
fun VersionCatalogBuilder.logging() {
    version("slf4j", "1.7.32")

    alias("kotlin-logging").to("io.github.microutils", "kotlin-logging").version("2.1.21")
    alias("logback").to("ch.qos.logback", "logback-classic").version("1.2.9")
    alias("slf4j-api").to("org.slf4j", "slf4j-api").versionRef("slf4j")
}
