package net.postchain.containers.bpm.job

import mu.KLogging
import net.postchain.containers.bpm.Chain
import net.postchain.containers.bpm.ContainerName
import net.postchain.core.Shutdownable
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

internal interface ContainerJobManager {
    fun <T> withLock(action: () -> T): T
    fun stopChain(chain: Chain)
    fun startChain(chain: Chain)
    fun restartChain(chain: Chain)
    fun doHealthcheck()
}

internal class DefaultContainerJobManager(
        val jobHandler: (job: ContainerJob) -> Unit,
        val healthcheckJobHandler: (Set<String>) -> Unit,
) : ContainerJobManager, Shutdownable {

    private val containerJobsThread: Thread
    private var isProcessJobs = true
    private val jobs = LinkedHashMap<String, Job>() // name -> job
    private var currentJob: Job? = null
    private val lockJobs = ReentrantLock()

    companion object : KLogging() {
        private const val SLEEP_TIMEOUT = 200L
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
            jobs.remove(job.name)
        }
    }

    override fun startChain(chain: Chain) {
        val job = jobOf(chain.containerName)
        job.startChain(chain)
        if (job.isEmpty()) {
            jobs.remove(job.name)
        }
    }

    override fun restartChain(chain: Chain) {
        jobOf(chain.containerName).restartChain(chain)
    }

    override fun doHealthcheck() {
        jobs[HealthcheckJob.NAME] = HealthcheckJob()
    }

    private fun startThread(): Thread {
        return thread(name = "containerJobThread") {
            while (isProcessJobs) {
                // Getting next job
                lockJobs.withLock {
                    currentJob = null
                    if (jobs.isNotEmpty()) {
                        val first = jobs.iterator().next()
                        jobs.remove(first.key)
                        currentJob = first.value
                    }
                }

                // Processing the job
                if (currentJob != null) {
                    val cur = currentJob
                    try {
                        if (cur is HealthcheckJob) {
                            val containersInProgress = jobs.keys.toSet()
                            healthcheckJobHandler(containersInProgress)

                        } else if (cur is ContainerJob) {
                            if (cur.shouldRun()) {
                                jobHandler(cur)
                            }

                            // Merging the job with a new enqueued one for the same container
                            lockJobs.withLock {
                                if (!cur.done) {
                                    jobs.merge(cur.name, cur) { new, _ -> cur.merge(new) }
                                } else {
                                    jobs.computeIfPresent(cur.name) { _, new ->
                                        (new as ContainerJob).minus(cur).takeIf(ContainerJob::isNotEmpty)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        logger.error("Can't handle container job: $currentJob", e)
                    }
                }

                Thread.sleep(SLEEP_TIMEOUT)
            }
        }
    }

    override fun shutdown() {
        isProcessJobs = false
        containerJobsThread.join()
    }

    private fun jobOf(containerName: ContainerName): ContainerJob {
        return jobs.computeIfAbsent(containerName.name) {
            ContainerJob(containerName)
        } as ContainerJob
    }
}
