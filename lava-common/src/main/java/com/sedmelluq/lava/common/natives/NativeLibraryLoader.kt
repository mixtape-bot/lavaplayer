package com.sedmelluq.lava.common.natives

import com.sedmelluq.lava.common.natives.architecture.SystemType
import com.sedmelluq.lava.common.natives.architecture.SystemType.Companion.detect
import mu.KotlinLogging
import org.apache.commons.io.IOUtils
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.PosixFilePermissions
import java.util.function.Predicate

/**
 * Loads native libraries by name. Libraries are expected to be in classpath /natives/[arch]/[prefix]name[suffix]
 */
public class NativeLibraryLoader(
    private val libraryName: String,
    private val systemFilter: Predicate<SystemType>?,
    private val properties: NativeLibraryProperties,
    private val binaryProvider: NativeLibraryBinaryProvider
) {
    public companion object {
        private val log = KotlinLogging.logger { }

        private const val DEFAULT_PROPERTY_PREFIX = "lava.native."
        private const val DEFAULT_RESOURCE_ROOT = "/natives/"

        public fun create(libraryName: String, classLoaderSample: Class<*>? = null): NativeLibraryLoader =
            createFiltered(libraryName, classLoaderSample, null)

        public fun createFiltered(
            libraryName: String,
            classLoaderSample: Class<*>? = null,
            systemFilter: Predicate<SystemType>? = null
        ): NativeLibraryLoader = NativeLibraryLoader(
            libraryName,
            systemFilter,
            SystemNativeLibraryProperties(libraryName, DEFAULT_PROPERTY_PREFIX),
            ResourceNativeLibraryBinaryProvider(classLoaderSample, DEFAULT_RESOURCE_ROOT)
        )

        @Throws(IOException::class)
        private fun createDirectoriesWithFullPermissions(path: Path) {
            val isPosix = FileSystems.getDefault()
                .supportedFileAttributeViews()
                .contains("posix")

            if (!isPosix) {
                Files.createDirectories(path)
            } else {
                val attributes = PosixFilePermissions.asFileAttribute(
                    PosixFilePermissions.fromString("rwxrwxrwx")
                )

                Files.createDirectories(path, attributes)
            }
        }
    }

    private val lock: Any = Any()

    @Volatile
    private var previousResult: LoadResult? = null

    public fun load() {
        var result = previousResult
        if (result == null) {
            synchronized(lock) {
                result = previousResult
                if (result == null) {
                    result = loadWithFailureCheck()
                    previousResult = result
                }
            }
        }

        if (result?.success == false) {
            throw result!!.exception!!
        }
    }

    private fun loadWithFailureCheck(): LoadResult {
        log.info { "Native library $libraryName: loading with filter $systemFilter" }
        return try {
            loadInternal()
            LoadResult(true, null)
        } catch (e: Throwable) {
            log.error(e) { "Native library $libraryName: loading failed." }
            LoadResult(false, RuntimeException(e))
        }
    }

    private fun loadInternal() {
        val explicitPath = properties.libraryPath
        if (explicitPath != null) {
            log.debug { "Native library $libraryName: explicit path provided $explicitPath" }
            loadFromFile(Paths.get(explicitPath).toAbsolutePath())
        } else {
            val systemType = detectMatchingSystemType()
            if (systemType != null) {
                val explicitDirectory = properties.libraryDirectory
                if (explicitDirectory != null) {
                    log.debug { "Native library $libraryName: explicit directory provided $explicitDirectory" }
                    val path = Paths
                        .get(explicitDirectory, systemType.formatLibraryName(libraryName))
                        .toAbsolutePath()

                    loadFromFile(path)
                } else {
                    loadFromFile(extractLibraryFromResources(systemType))
                }
            }
        }
    }

    private fun loadFromFile(libraryFilePath: Path) {
        log.debug { "Native library $libraryName: attempting to load library at $libraryFilePath" }
        System.load(libraryFilePath.toAbsolutePath().toString())
        log.info { "Native library $libraryName: successfully loaded." }
    }

    private fun extractLibraryFromResources(systemType: SystemType): Path {
        try {
            val stream = binaryProvider.getLibraryStream(systemType, libraryName)
                ?: throw UnsatisfiedLinkError("Required library was not found")

            stream.use {
                val extractedLibraryPath = prepareExtractionDirectory()
                    .resolve(systemType.formatLibraryName(libraryName))

                FileOutputStream(extractedLibraryPath.toFile()).use { fileStream -> IOUtils.copy(it, fileStream) }
                return extractedLibraryPath
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    @Throws(IOException::class)
    private fun prepareExtractionDirectory(): Path {
        val extractionDirectory = detectExtractionBaseDirectory().resolve(System.currentTimeMillis().toString())
        if (!Files.isDirectory(extractionDirectory)) {
            log.debug { "Native library $libraryName: extraction directory $extractionDirectory does not exist, creating." }

            try {
                createDirectoriesWithFullPermissions(extractionDirectory)
            } catch (ignored: FileAlreadyExistsException) {
                // All is well
            } catch (e: IOException) {
                throw IOException("Failed to create directory for unpacked native library.", e)
            }
        } else {
            log.debug { "Native library $libraryName: extraction directory $extractionDirectory already exists, using." }
        }

        return extractionDirectory
    }

    private fun detectExtractionBaseDirectory(): Path {
        val explicitExtractionBase = properties.extractionPath
        if (explicitExtractionBase != null) {
            log.debug { "Native library $libraryName: explicit extraction path provided - $explicitExtractionBase" }
            return Paths.get(explicitExtractionBase).toAbsolutePath()
        }

        val path = Paths
            .get(System.getProperty("java.io.tmpdir", "/tmp"), "lava-jni-natives")
            .toAbsolutePath()

        log.debug { "Native library $libraryName: detected $path as base directory for extraction." }
        return path
    }

    private fun detectMatchingSystemType(): SystemType? {
        val systemType: SystemType = try {
            detect(properties)
        } catch (e: IllegalArgumentException) {
            return if (systemFilter != null) {
                log.info {
                    "Native library $libraryName: could not detect system type, but system filter is $systemFilter - assuming it does " +
                        "not match and skipping library."
                }

                null
            } else {
                throw e
            }
        }

        if (systemFilter != null && !systemFilter.test(systemType)) {
            log.debug {
                "Native library $libraryName: system filter does not match detected system ${systemType.formatSystemName()}, skipping"
            }

            return null
        }

        return systemType
    }

    public data class LoadResult(val success: Boolean, val exception: RuntimeException?)
}
