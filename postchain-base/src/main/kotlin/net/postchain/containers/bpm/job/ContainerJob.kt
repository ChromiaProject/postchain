package net.postchain.containers.bpm.job

import net.postchain.containers.bpm.Chain
import net.postchain.containers.bpm.ContainerName

/**
 * Basic class for all jobs
 */
open class Job(val name: String)

/**
 * Indicates that health check should be done for all containers.
 * Usually, it means to start stopped containers that should be run.
 */
class HealthcheckJob : Job(DefaultContainerJobManager.JOB_TAG_HEALTHCHECK)

/**
 * Describes actions over chains that should be done for the container [containerName].
 * Actions are: stop chain, start chain.
 */
class ContainerJob(val containerName: ContainerName) : Job(containerName.name) {

    val key = containerName.name
    val chainsToStop = mutableSetOf<Chain>()
    val chainsToStart = mutableSetOf<Chain>()
    var done: Boolean = false
    private var nextExecutionTime = 0L

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
        nextExecutionTime = System.currentTimeMillis() + delay
    }

    fun shouldRun(): Boolean {
        return !done && nextExecutionTime <= System.currentTimeMillis()
    }

    override fun toString(): String {
        return "Job(container: $containerName, " +
                "toStop: ${chainsToStop.toTypedArray().contentToString()}, " +
                "toStart: ${chainsToStart.toTypedArray().contentToString()}, " +
                "done: $done)"
    }
}
