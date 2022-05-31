package net.postchain.containers.bpm

import mu.KLogging
import net.postchain.core.Shutdownable
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

internal interface ContainerJobManager {
    fun <T> withLock(action: () -> T): T
    fun stopChain(chain: Chain)
    fun startChain(chain: Chain)
    fun restartChain(chain: Chain)
    fun executeHealthcheck()
}

internal class DefaultContainerJobManager(
        val jobHandler: ContainerJobHandler,
        val healthcheckJobHandler: HealthcheckJobHandler
) : ContainerJobManager, Shutdownable {

    private val containerJobsThread: Thread
    private var isProcessJobs = true
    private val jobs = LinkedHashMap<ContainerName, Job>()
    private var currentJob: Job? = null
    private val lockJobs = ReentrantLock()

    companion object : KLogging() {
        private const val JOB_TAG_HEALTHCHECK = "healthcheck"
    }

    init {
        containerJobsThread = startThread()
    }

    override fun <T> withLock(action: () -> T): T {
        return lockJobs.withLock(action)
    }

    override fun stopChain(chain: Chain) {
        val job = jobOf(chain.containerName)
        job.stopChain(chain)
        if (job.isEmpty()) {
            jobs.remove(job.containerName)
        }
    }

    override fun startChain(chain: Chain) {
        val job = jobOf(chain.containerName)
        job.startChain(chain)
        if (job.isEmpty()) {
            jobs.remove(job.containerName)
        }
    }

    override fun restartChain(chain: Chain) {
        jobOf(chain.containerName).restartChain(chain)
    }

    override fun executeHealthcheck() {
        jobOf(ContainerName(JOB_TAG_HEALTHCHECK, ""))
    }

    private fun startThread(): Thread {
        return thread(name = "containerJobThread") {
            while (isProcessJobs) {
                // Get next job
                lockJobs.withLock {
                    currentJob = null
                    if (jobs.isNotEmpty()) {
                        val first = jobs.iterator().next()
                        jobs.remove(first.key)
                        currentJob = first.value
                    }
                }

                // Process the job
                if (currentJob != null) {
                    try {
                        if (currentJob!!.containerName.name == JOB_TAG_HEALTHCHECK) {
                            healthcheckJobHandler()
                        } else {
                            jobHandler(currentJob!!.containerName, currentJob!!.toStop, currentJob!!.toStart)
                        }
                    } catch (e: Exception) {
                        logger.error("Can't handle the container job: ${currentJob!!.containerName}", e)
                    }
                }

                Thread.sleep(100)
            }
        }
    }

    override fun shutdown() {
        isProcessJobs = false
        containerJobsThread.join()
    }

    private fun jobOf(containerName: ContainerName): Job {
        return jobs.computeIfAbsent(containerName) {
            Job(containerName)
        }
    }
}

internal class Job(val containerName: ContainerName) {

    val toStop = mutableSetOf<Chain>()
    val toStart = mutableSetOf<Chain>()

    fun stopChain(chain: Chain) {
        if (toStart.contains(chain)) {
            toStart.remove(chain)
        } else {
            toStop.add(chain)
        }
    }

    fun startChain(chain: Chain) {
        if (toStop.contains(chain)) {
            toStop.remove(chain)
        } else {
            toStart.add(chain)
        }
    }

    fun restartChain(chain: Chain) {
        toStop.add(chain)
        toStart.add(chain)
    }

    fun isEmpty(): Boolean {
        return toStop.isEmpty() && toStart.isEmpty()
    }
}

internal typealias ContainerJobHandler = (containerName: ContainerName, toStop: Set<Chain>, toStart: Set<Chain>) -> Unit
internal typealias HealthcheckJobHandler = () -> Unit

