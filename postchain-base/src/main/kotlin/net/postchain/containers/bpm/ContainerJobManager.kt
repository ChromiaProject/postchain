package net.postchain.containers.bpm

import net.postchain.core.Shutdownable
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

internal interface ContainerJobManager {
    fun lock()
    fun unlock()
    fun stopChain(chain: Chain)
    fun startChain(chain: Chain)
    fun restartChain(chain: Chain)
}

internal class DefaultContainerJobManager(val jobHandler: ContainerJobHandler) : ContainerJobManager, Shutdownable {

    private val containerJobsThread: Thread
    private var isProcessJobs = true
    private val jobs = LinkedHashMap<ContainerName, Job>()
    private var currentJob: Job? = null
    private val lockJobs = ReentrantLock()

    init {
        containerJobsThread = startThread()
    }

    override fun lock() {
        lockJobs.lock()
    }

    override fun unlock() {
        lockJobs.unlock()
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
                    jobHandler(currentJob!!.containerName, currentJob!!.toStop, currentJob!!.toStart)
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

