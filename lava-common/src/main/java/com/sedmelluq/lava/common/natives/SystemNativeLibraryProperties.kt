package com.sedmelluq.lava.common.natives

public class SystemNativeLibraryProperties(private val libraryName: String, private val propertyPrefix: String) : NativeLibraryProperties {
    override val libraryPath: String?
        get() = get("path")

    override val libraryDirectory: String?
        get() = get("dir")

    override val extractionPath: String?
        get() = get("extractPath")

    override val systemName: String?
        get() = get("system")

    override val architectureName: String?
        get() = get("arch")

    override val libraryFileNamePrefix: String?
        get() = get("libPrefix")

    override val libraryFileNameSuffix: String?
        get() = get("libSuffix")

    private operator fun get(property: String): String? {
        return System.getProperty(
            "$propertyPrefix$libraryName.$property",
            System.getProperty(propertyPrefix + property)
        )
    }
}
