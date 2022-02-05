package com.sedmelluq.lava.common.tools

import kotlinx.atomicfu.atomic
import mu.KotlinLogging
import java.lang.Runnable
import java.util.concurrent.ThreadFactory

/**
 * Thread factory for daemon threads.
 *
 * @param name         Name that will be included in thread names.
 * @param nameFormat   The name format to use.
 * @param exitCallback Runnable to be executed when the thread exits.
 */
public class DaemonThreadFactory @JvmOverloads constructor(
    name: String?,
    nameFormat: String? = DEFAULT_NAME_FORMAT,
    exitCallback: Runnable? = null,
) : ThreadFactory {
    public companion object {
        private val log = KotlinLogging.logger {  }
        private var poolNumber by atomic(0)

        public var DEFAULT_NAME_FORMAT: String = "lava-daemon-pool-%s-%d-thread-"
    }

    private val group: ThreadGroup
    private var threadNumber by atomic(0)
    private val namePrefix: String
    private val exitCallback: Runnable?

    init {
        val securityManager = System.getSecurityManager()
        group = if (securityManager != null) securityManager.threadGroup else Thread.currentThread().threadGroup
        namePrefix = String.format(nameFormat!!, name, poolNumber++)

        this.exitCallback = exitCallback
    }

    override fun newThread(runnable: Runnable): Thread {
        val thread = Thread(group, getThreadRunnable(runnable), namePrefix + threadNumber++, 0)
        thread.isDaemon = true
        thread.priority = Thread.NORM_PRIORITY

        return thread
    }

    private fun getThreadRunnable(target: Runnable): Runnable {
        return if (exitCallback == null) target else ExitCallbackRunnable(target)
    }

    private inner class ExitCallbackRunnable(private val original: Runnable) : Runnable {
        override fun run() {
            try {
                original.run()
            } finally {
                wrapExitCallback()
            }
        }

        private fun wrapExitCallback() {
            val wasInterrupted = Thread.interrupted()
            try {
                exitCallback?.run()
            } catch (throwable: Throwable) {
                log.error(throwable) { "Thread exit notification threw an exception." }
            } finally {
                if (wasInterrupted) {
                    Thread.currentThread().interrupt()
                }
            }
        }
    }
}
