package net.postchain.containers.bpm.job

import com.google.common.util.concurrent.ThreadFactoryBuilder
import mu.KLogging
import net.postchain.containers.bpm.Chain
import net.postchain.containers.bpm.ContainerName
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.core.Shutdownable
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal interface ContainerJobManager {
    fun <T> withLock(action: () -> T): T
    fun stopChain(chain: Chain)
    fun startChain(chain: Chain)
    fun restartChain(chain: Chain)
    fun hasPendingJobs(containerName: ContainerName): Boolean
}

internal class DefaultContainerJobManager(
        val containerNodeConfig: ContainerNodeConfig,
        private val containerJobHandler: ContainerJobHandler,
        private val containerHealthcheckHandler: ContainerHealthcheckHandler,
        private val housekeepingHandler: () -> Unit
) : ContainerJobManager, Shutdownable {

    private val jobs = LinkedHashMap<String, Job>() // name -> job
    private var currentJob: Job? = null
    private val lockJobs = ReentrantLock()
    private val jobsExecutor: ScheduledExecutorService
    private var nextHealthcheckTime: Long
    private val healthcheckPeriod = containerNodeConfig.healthcheckRunningContainersCheckPeriod

    companion object : KLogging() {
        private const val EXECUTION_PERIOD = 200L
    }

    init {
        jobsExecutor = Executors.newSingleThreadScheduledExecutor(
                ThreadFactoryBuilder().setNameFormat("containerJobThread").build()
        ).also {
            it.scheduleWithFixedDelay({
                try {
                    checkJobs()
                } catch (e: Exception) {
                    logger.error("Unexpected exception while checking jobs", e)
                }
            }, EXECUTION_PERIOD, EXECUTION_PERIOD, TimeUnit.MILLISECONDS)
        }

        nextHealthcheckTime = if (healthcheckPeriod > 0) System.currentTimeMillis() + healthcheckPeriod else 0
    }

    override fun <T> withLock(action: () -> T): T {
        return lockJobs.withLock(action)
    }

    override fun stopChain(chain: Chain) {
        val job = jobOf(chain.containerName)
        job.stopChain(chain)
        if (job.isEmpty()) {
            lockJobs.withLock {
                jobs.remove(job.name)
            }
        }
    }

    override fun startChain(chain: Chain) {
        val job = jobOf(chain.containerName)
        job.startChain(chain)
        if (job.isEmpty()) {
            lockJobs.withLock {
                jobs.remove(job.name)
            }
        }
    }

    override fun restartChain(chain: Chain) {
        jobOf(chain.containerName).restartChain(chain)
    }

    override fun hasPendingJobs(containerName: ContainerName) = jobs[containerName.name]?.let {
        (it as ContainerJob).isNotEmpty()
    } ?: false

    override fun shutdown() {
        jobsExecutor.shutdownNow()
        jobsExecutor.awaitTermination(2000, TimeUnit.MILLISECONDS)
    }

    private fun checkJobs() {
        if (runHealthCheck()) return

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
                if (cur is ContainerJob) {
                    if (cur.shouldRun()) {
                        cur.withLock {
                            containerJobHandler.handleJob(cur)
                        }
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

        housekeepingHandler()
    }

    private fun runHealthCheck(): Boolean =
            if (nextHealthcheckTime > 0 && nextHealthcheckTime <= System.currentTimeMillis()) {
                nextHealthcheckTime = System.currentTimeMillis() + healthcheckPeriod
                val containersInProgress = jobs.keys.toSet()
                try {
                    containerHealthcheckHandler.check(containersInProgress)
                } catch (e: Exception) {
                    logger.error("Can't handle health check job", e)
                }
                true
            } else false


    private fun jobOf(containerName: ContainerName): ContainerJob {
        lockJobs.withLock {
            return jobs.computeIfAbsent(containerName.name) {
                ContainerJob(containerName)
            } as ContainerJob
        }
    }
}
