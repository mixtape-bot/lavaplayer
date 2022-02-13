package com.sedmelluq.lava.common.natives.architecture

import com.sedmelluq.lava.common.natives.NativeLibraryProperties

public class SystemType(
    public val architectureType: ArchitectureType,
    public val osType: OperatingSystemType
) {
    public fun formatSystemName(): String? {
        return if (osType.identifier != null) {
            osType.identifier + "-" + architectureType.identifier
        } else {
            architectureType.identifier
        }
    }

    public fun formatLibraryName(libraryName: String): String {
        return osType.libraryFilePrefix + libraryName + osType.libraryFileSuffix
    }

    private data class UnknownOperatingSystem(override val libraryFilePrefix: String, override val libraryFileSuffix: String) : OperatingSystemType {
        override val identifier: String?
            get() = null
    }

    public companion object {
        @JvmStatic
        public fun detect(properties: NativeLibraryProperties): SystemType {
            val systemName = properties.systemName
            if (systemName != null) {
                val osType = UnknownOperatingSystem(
                    libraryFilePrefix = properties.libraryFileNamePrefix ?: "lib",
                    libraryFileSuffix = properties.libraryFileNameSuffix ?: ".so"
                )

                return SystemType(ArchitectureType(systemName), osType)
            }

            val architectureType = properties.architectureName?.let { ArchitectureType(it) }
                ?: DefaultArchitectureTypes.detect()

            return SystemType(
                architectureType = architectureType,
                osType = DefaultOperatingSystemTypes.detect()
            )
        }
    }
}
