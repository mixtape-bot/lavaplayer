package com.sedmelluq.lava.common.natives.architecture

import java.lang.IllegalArgumentException
import java.util.*

public enum class DefaultArchitectureTypes(override val identifier: String, public val aliases: List<String>) : ArchitectureType {
    ARM("arm", listOf("arm", "armeabi", "armv7b", "armv7l")),
    ARM_HF("armhf", listOf("armeabihf", "armeabi-v7a")),
    ARMv8_32("aarch32", listOf("armv8b", "armv8l")),
    ARMv8_64("aarch64", listOf("arm64", "aarch64", "aarch64_be", "arm64-v8a")),

    MIPS_32("mips", listOf("mips")),
    MIPS_32_LE("mipsel", listOf("mipsel", "mipsle")),
    MIPS_64("mips64", listOf("mips64")),
    MIPS_64_LE("mips64el", listOf("mips64el", "mips64le")),

    PPC_32("powerpc", listOf("ppc", "powerpc")),
    PPC_32_LE("powerpcle", listOf("ppcel", "ppcle")),
    PPC_64("ppc64", listOf("ppc64")),
    PPC_64_LE("ppc64le", listOf("ppc64el", "ppc64le")),
    X86_32("x86", listOf("x86", "i386", "i486", "i586", "i686")),

    X86_64("x86-64", listOf("x86_64", "amd64"));

    public companion object {
        private val aliasMap = createAliasMap()

        @JvmStatic
        public fun detect(): ArchitectureType {
            val architectureName = System.getProperty("os.arch")
            return aliasMap[architectureName]
                ?: throw IllegalArgumentException("Unknown architecture: $architectureName")
        }

        private fun createAliasMap(): Map<String, ArchitectureType> {
            val aliases: MutableMap<String, ArchitectureType> = HashMap()
            for (value in values()) {
                for (alias in value.aliases) {
                    aliases[alias] = value
                }
            }
            return aliases
        }
    }
}
