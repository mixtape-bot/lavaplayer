package com.sedmelluq.lava.common.natives.architecture

public interface OperatingSystemType {
    /**
     * Identifier used in directory names (combined with architecture) for this OS
     */
    public val identifier: String?

    /**
     * Prefix used for library file names. `lib` on most Unix flavors.
     */
    public val libraryFilePrefix: String?

    /**
     * Suffix (extension) used for library file names.
     */
    public val libraryFileSuffix: String?
}
