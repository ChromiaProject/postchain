package net.postchain.async

import mu.KLogger
import nl.komponents.kovenant.Deferred
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 *
 */
class AsynchronousTaskQueue(private val logger: KLogger?, private val workerName: String = "AsynchronousTaskQueue") {

    constructor() : this(null)

    private var hasPreviousTaskFailed = AtomicBoolean(false)

    // The executor will only execute one thing at a time, in order
    private val taskExecutor = ThreadPoolExecutor(1, 1,
            0L, TimeUnit.MILLISECONDS,
            LinkedBlockingQueue()
    ) { r: Runnable ->
        Thread(r, workerName).apply {
            isDaemon = true // So it can't block the JVM from exiting if still running
        }
    }

    /**
     * Stops the execution of the underlying thread worker.
     * No more tasks can be added after this operation is completed.
     */
    fun shutdownQueue() {
        taskExecutor.shutdownNow()
    }

    fun reset() {
        hasPreviousTaskFailed.set(false)
    }

    fun <V> queueTask(name: String, failOnPreviousFail: Boolean = false, task: () -> V): Promise<V, Exception> {
        logger?.trace{ "addTask() - $workerName putting job $name on queue" }
        val deferred = deferred<V, Exception>()

        taskExecutor.execute {
            if (failOnPreviousFail && hasPreviousTaskFailed.get()) { rejectPromise(name, deferred); return@execute }
            try {
                logger?.trace { "Starting job: $name" }
                val res = task()
                logger?.trace{ "Finished job: $name" }
                deferred.resolve(res)
                hasPreviousTaskFailed.set(false)
            } catch (e: Exception) {
                logger?.error(e) { "Failed job: $name" }
                rejectPromise(name, deferred, e)
            }
        }

        return deferred.promise
    }

   private fun <V> rejectPromise(name: String, deferred: Deferred<V, Exception>, exception: Exception = PreviousTaskFailedException()): Promise<V, Exception> {
       logger?.error(exception) { "Job $name was rejected due to previous task failure" }
       hasPreviousTaskFailed.set(true)
       deferred.reject(exception)
       return deferred.promise
   }

    class PreviousTaskFailedException: RuntimeException("Previous task has failed in AsynchronousTaskQueue")
}