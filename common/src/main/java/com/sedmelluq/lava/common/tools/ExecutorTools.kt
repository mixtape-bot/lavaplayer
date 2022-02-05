package com.sedmelluq.lava.common.tools

import mu.KotlinLogging
import java.util.concurrent.*

/**
 * Utility methods for working with executors.
 */
public object ExecutorTools {
    private val log = KotlinLogging.logger {  }
    private const val WAIT_TIME = 1000L

    /**
     * Shut down an executor and log the shutdown result. The executor is given a fixed amount of time to shut down, if it
     * does not manage to do it in that time, then this method just returns.
     *
     * @param executorService Executor service to shut down
     * @param description     Description of the service to use for logging
     */
    public fun shutdownExecutor(executorService: ExecutorService?, description: String?) {
        if (executorService == null) {
            return
        }

        log.debug { "Shutting down executor $description" }
        executorService.shutdownNow()
        try {
            if (!executorService.awaitTermination(WAIT_TIME, TimeUnit.MILLISECONDS)) {
                log.debug { "Executor $description did not shut down in $WAIT_TIME" }
            } else {
                log.debug { "Executor $description successfully shut down" }
            }
        } catch (e: InterruptedException) {
            log.debug { "Received an interruption while shutting down executor $description" }
            Thread.currentThread().interrupt()
        }
    }

    /**
     * Creates an executor which will use the queue only when maximum number of threads has been reached. The core pool
     * size here only means the number of threads that are always alive, it is no longer used to check whether a new
     * thread should start or not. The maximum size is otherwise pointless unless you have a bounded queue, which in turn
     * would cause tasks to be rejected if it is too small.
     *
     * @param coreSize      Number of threads that are always alive
     * @param maximumSize   The maximum number of threads in the pool
     * @param timeout       Non-core thread timeout in milliseconds
     * @param threadFactory Thread factory to create pool threads with
     * @return An eagerly scaling thread pool executor
     */
    public fun createEagerlyScalingExecutor(
        coreSize: Int,
        maximumSize: Int,
        timeout: Long,
        queueCapacity: Int,
        threadFactory: ThreadFactory
    ): ThreadPoolExecutor {
        val executor = ThreadPoolExecutor(
            coreSize,
            maximumSize,
            timeout,
            TimeUnit.MILLISECONDS,
            EagerlyScalingTaskQueue(queueCapacity),
            threadFactory
        )

        executor.rejectedExecutionHandler = EagerlyScalingRejectionHandler()
        return executor
    }

    private class EagerlyScalingTaskQueue(capacity: Int) : LinkedBlockingQueue<Runnable>(capacity) {
        override fun offer(runnable: Runnable): Boolean = isEmpty() && super.offer(runnable)

        fun offerDirectly(runnable: Runnable): Boolean = super.offer(runnable)
    }

    private class EagerlyScalingRejectionHandler : RejectedExecutionHandler {
        override fun rejectedExecution(runnable: Runnable, executor: ThreadPoolExecutor) {
            if (!(executor.queue as EagerlyScalingTaskQueue).offerDirectly(runnable)) {
                throw RejectedExecutionException("Task $runnable rejected from $runnable")
            }
        }
    }

    public object CompletedVoidFuture : Future<Void?> {
        override fun cancel(mayInterruptIfRunning: Boolean): Boolean = false
        override fun isCancelled(): Boolean = false
        override fun isDone(): Boolean = true

        @Throws(InterruptedException::class, ExecutionException::class)
        override fun get(): Void? = null

        @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
        override fun get(timeout: Long, unit: TimeUnit): Void? = null
    }
}
