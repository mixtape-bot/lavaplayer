package com.sedmelluq.lava.common.natives.architecture

public interface ArchitectureType {
    public companion object {
        public operator fun invoke(identifier: String): ArchitectureType = object : ArchitectureType {
            override val identifier: String = identifier
        }
    }

    /**
     * The identifier used in directory names (combined with OS identifier) for this ABI
     */
    public val identifier: String?
}
