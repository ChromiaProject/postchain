package net.postchain.containers.bpm.job

import net.postchain.containers.bpm.Chain
import net.postchain.containers.bpm.ContainerName
import java.time.Clock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.min
import kotlin.math.pow
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Basic class for all jobs
 */
open class Job(val name: String)

/**
 * Describes actions over chains that should be done for the container [containerName].
 * Actions are: stop chain, start chain.
 */
class ContainerJob(val containerName: ContainerName, private val clock: Clock) : Job(containerName.name) {

    private val maxBackoffTime = 5.toDuration(DurationUnit.MINUTES)
    private val lock = ReentrantLock()
    private val internalChainsToStop = mutableSetOf<Chain>()
    private val internalChainsToStart = mutableSetOf<Chain>()
    private val internalFailedStartCount = AtomicInteger(0)
    private val internalNextExecutionTime = AtomicLong(0)
    private val internalDone = AtomicBoolean(false)
    val chainsToStart get() = internalChainsToStart.toSet()
    val chainsToStop get() = internalChainsToStop.toSet()
    val failedStartCount: Int get() = internalFailedStartCount.get()
    val nextExecutionTime: Long get() = internalNextExecutionTime.get()
    var done: Boolean
        get() = internalDone.get()
        set(value) = internalDone.set(value)

    fun <T> withLock(action: () -> T): T {
        return lock.withLock(action)
    }

    fun stopChain(chain: Chain) {
        lock.withLock {
            if (internalChainsToStart.contains(chain)) {
                internalChainsToStart.remove(chain)
            } else {
                internalChainsToStop.add(chain)
            }
        }
    }

    fun startChain(chain: Chain) {
        lock.withLock {
            if (internalChainsToStop.contains(chain)) {
                internalChainsToStop.remove(chain)
            } else {
                internalChainsToStart.add(chain)
            }
        }
    }

    fun restartChain(chain: Chain) {
        lock.withLock {
            internalChainsToStop.add(chain)
            internalChainsToStart.add(chain)
        }
    }

    fun isEmpty(): Boolean {
        lock.withLock {
            return internalChainsToStop.isEmpty() && internalChainsToStart.isEmpty()
        }
    }

    fun isNotEmpty() = !isEmpty()

    /**
     * Incrementally merges chains to stop/start of given [job] into this job
     */
    fun merge(job: Job): ContainerJob {
        lock.withLock {
            if (job is ContainerJob && containerName == job.containerName) {
                job.internalChainsToStop.forEach(::stopChain)
                job.internalChainsToStart.forEach(::startChain)
            }
            return this
        }
    }

    /**
     * Subtracts sets of chains (to stop/start) of a given job
     * from correspondent sets of chains of this job
     */
    fun minus(job: Job): ContainerJob {
        lock.withLock {
            if (job is ContainerJob && containerName == job.containerName) {
                internalChainsToStop.removeAll(job.internalChainsToStop)
                internalChainsToStart.removeAll(job.internalChainsToStart)
            }
            return this
        }
    }

    fun postpone(delay: Long) {
        internalNextExecutionTime.set(currentTimeMillis() + delay)
    }

    fun postponeWithBackoff() {
        val backoffTimeMs = min(2.0.pow(internalFailedStartCount.get()).toLong() * 1000, maxBackoffTime.inWholeMilliseconds)
        if (backoffTimeMs < maxBackoffTime.inWholeMilliseconds) internalFailedStartCount.incrementAndGet()
        internalNextExecutionTime.set(currentTimeMillis() + backoffTimeMs)
    }

    fun shouldRun(): Boolean {
        return !internalDone.get() && internalNextExecutionTime.get() <= currentTimeMillis()
    }

    private fun currentTimeMillis() = clock.millis()

    fun resetFailedStartCount() = internalFailedStartCount.set(0)

    override fun toString(): String {
        return "Job(container: $containerName, " +
                "toStop: ${internalChainsToStop.toTypedArray().contentToString()}, " +
                "toStart: ${internalChainsToStart.toTypedArray().contentToString()}, " +
                "done: $internalDone)"
    }
}
