package com.sedmelluq.lava.common.tools.exception

public class EnvironmentInformation private constructor(message: String) : Exception(message, null, false, false) {
    public companion object {
        private val PROPERTIES = arrayOf(
            "os.arch",
            "os.name",
            "os.version",
            "java.vendor",
            "java.version",
            "java.runtime.version",
            "java.vm.version"
        )

        public val INSTANCE: EnvironmentInformation = create()

        private fun create(build: DetailMessageBuilder.() -> Unit = {}): EnvironmentInformation {
            val builder = DetailMessageBuilder()
                .apply(build)
                .toString()

            return EnvironmentInformation(builder)
        }
    }
}
