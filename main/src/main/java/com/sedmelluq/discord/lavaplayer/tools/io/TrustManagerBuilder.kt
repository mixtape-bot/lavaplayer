package com.sedmelluq.discord.lavaplayer.tools.io

import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools
import com.sedmelluq.discord.lavaplayer.tools.extensions.closeWithWarnings
import mu.KotlinLogging
import java.security.KeyStore
import java.security.cert.Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Builder for a trust manager with custom certificates.
 */
class TrustManagerBuilder {
    companion object {
        private val log = KotlinLogging.logger { }
    }

    private val certificates = mutableListOf<Certificate>()

    /**
     * Add certificates from the default trust store
     *
     * @return this
     * @throws Exception In case anything explodes.
     */
    @Throws(Exception::class)
    fun addBuiltinCertificates(): TrustManagerBuilder {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as KeyStore?)

        val builtInTrustManager = findFirstX509TrustManager(factory)
        builtInTrustManager?.let { addFromTrustManager(it) }

        return this
    }

    /**
     * Add certificates from the specified resource directory, using {path}/bundled.txt and {path}/extended.txt as the
     * list of JKS file names to load from that directory.
     *
     * @param path Path to the resource directory.
     * @return this
     * @throws Exception In case anything explodes.
     */
    @Throws(Exception::class)
    fun addFromResourceDirectory(path: String): TrustManagerBuilder {
        addFromResourceList(path, "$path/bundled.txt")
        addFromResourceList(path, "$path/extended.txt")

        return this
    }

    /**
     * @return A trust manager with the loaded certificates.
     * @throws Exception In case anything explodes.
     */
    @Throws(Exception::class)
    fun build(): X509TrustManager? {
        val keyStore = KeyStore.getInstance("JKS")
        keyStore.load(null, null)

        certificates.forEachIndexed { i, c ->
            keyStore.setCertificateEntry(i.toString(), c)
        }

        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(keyStore)

        return findFirstX509TrustManager(factory)
    }

    private fun findFirstX509TrustManager(factory: TrustManagerFactory): X509TrustManager? =
        factory.trustManagers.firstNotNullOfOrNull { it as? X509TrustManager }

    private fun addFromTrustManager(trustManager: X509TrustManager) {
        certificates.addAll(trustManager.acceptedIssuers)
    }

    @Throws(Exception::class)
    private fun addFromResourceList(basePath: String, listPath: String) {
        val listFileStream = TrustManagerBuilder::class.java.getResourceAsStream(listPath)
            ?: return log.debug { "Certificate list $listPath not present in classpath." }

        try {
            for (line in DataFormatTools.streamToLines(listFileStream)) {
                val fileName = line.trim { it <= ' ' }
                if (fileName.isNotEmpty()) {
                    addFromResourceFile("$basePath/$fileName")
                }
            }
        } finally {
            listFileStream.closeWithWarnings()
        }
    }

    @Throws(Exception::class)
    private fun addFromResourceFile(resourcePath: String) {
        val fileStream = TrustManagerBuilder::class.java.getResourceAsStream(resourcePath)
            ?: return log.warn { "Certificate $resourcePath not present in classpath." }

        try {
            val keyStore = KeyStore.getInstance("JKS")
            keyStore.load(fileStream, null)
            addFromKeyStore(keyStore)
        } finally {
            fileStream.closeWithWarnings()
        }
    }

    @Throws(Exception::class)
    private fun addFromKeyStore(keyStore: KeyStore) {
        val enumeration = keyStore.aliases()
        while (enumeration.hasMoreElements()) {
            val alias = enumeration.nextElement()
            if (keyStore.isCertificateEntry(alias)) {
                certificates.add(keyStore.getCertificate(alias))
            }
        }
    }
}
