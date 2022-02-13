package com.sedmelluq.lava.common.natives.architecture

public enum class DefaultOperatingSystemTypes(
    override val identifier: String,
    override val libraryFilePrefix: String,
    override val libraryFileSuffix: String
) : OperatingSystemType {
    LINUX("linux", "lib", ".so"),
    WINDOWS("win", "", ".dll"),
    DARWIN("darwin", "lib", ".dylib"),
    SOLARIS("solaris", "lib", ".so");

    public companion object {
        @JvmStatic
        public fun detect(): OperatingSystemType {
            val osFullName = System.getProperty("os.name")
            return if (osFullName.startsWith("Windows", true)) {
                WINDOWS
            } else if (osFullName.startsWith("Mac OS X", true)) {
                DARWIN
            } else if (osFullName.startsWith("Solaris", true)) {
                SOLARIS
            } else if (osFullName.startsWith("linux", true)) {
                LINUX
            } else {
                throw IllegalArgumentException("Unknown operating system: $osFullName")
            }
        }
    }
}
