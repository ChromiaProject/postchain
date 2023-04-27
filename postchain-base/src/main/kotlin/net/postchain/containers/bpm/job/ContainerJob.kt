package net.postchain.containers.bpm.job

import net.postchain.common.toHex
import net.postchain.containers.bpm.Chain
import net.postchain.containers.bpm.ContainerName
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Basic class for all jobs
 */
open class Job(val name: String)

/**
 * Indicates that health check should be done for all containers.
 * Usually, it means to start stopped containers that should be run.
 */
class HealthcheckJob : Job(NAME) {
    companion object {
        val NAME = "healthcheck_" + Random.Default.nextBytes(8).toHex()
    }
}

/**
 * Describes actions over chains that should be done for the container [containerName].
 * Actions are: stop chain, start chain.
 */
open class ContainerJob(val containerName: ContainerName) : Job(containerName.name) {

    val chainsToStop = mutableSetOf<Chain>()
    val chainsToStart = mutableSetOf<Chain>()
    var done: Boolean = false
    var nextExecutionTime = 0L
        private set
    var failedStartCount = 0
    private val maxBackoffTime = 5.toDuration(DurationUnit.MINUTES)

    fun stopChain(chain: Chain) {
        if (chainsToStart.contains(chain)) {
            chainsToStart.remove(chain)
        } else {
            chainsToStop.add(chain)
        }
    }

    fun startChain(chain: Chain) {
        if (chainsToStop.contains(chain)) {
            chainsToStop.remove(chain)
        } else {
            chainsToStart.add(chain)
        }
    }

    fun restartChain(chain: Chain) {
        chainsToStop.add(chain)
        chainsToStart.add(chain)
    }

    fun isEmpty(): Boolean {
        return chainsToStop.isEmpty() && chainsToStart.isEmpty()
    }

    fun isNotEmpty() = !isEmpty()

    /**
     * Incrementally merges chains to stop/start of given [job] into this job
     */
    fun merge(job: Job): ContainerJob {
        if (job is ContainerJob && containerName == job.containerName) {
            job.chainsToStop.forEach(::stopChain)
            job.chainsToStart.forEach(::startChain)
        }
        return this
    }

    /**
     * Subtracts sets of chains (to stop/start) of a given job
     * from correspondent sets of chains of this job
     */
    fun minus(job: Job): ContainerJob {
        if (job is ContainerJob && containerName == job.containerName) {
            chainsToStop.removeAll(job.chainsToStop)
            chainsToStart.removeAll(job.chainsToStart)
        }
        return this
    }

    fun postpone(delay: Long) {
        nextExecutionTime = currentTimeMillis() + delay
    }

    fun postponeWithBackoff() {
        val backoffTimeMs = min(2.0.pow(failedStartCount).toLong() * 1000, maxBackoffTime.inWholeMilliseconds)
        if (backoffTimeMs < maxBackoffTime.inWholeMilliseconds) failedStartCount++
        nextExecutionTime = currentTimeMillis() + backoffTimeMs
    }

    fun shouldRun(): Boolean {
        return !done && nextExecutionTime <= currentTimeMillis()
    }

    open fun currentTimeMillis() = System.currentTimeMillis()

    override fun toString(): String {
        return "Job(container: $containerName, " +
                "toStop: ${chainsToStop.toTypedArray().contentToString()}, " +
                "toStart: ${chainsToStart.toTypedArray().contentToString()}, " +
                "done: $done)"
    }
}
