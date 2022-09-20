package net.postchain.d1.icmf

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KLogging
import net.postchain.core.Shutdownable
import net.postchain.crypto.CryptoSystem
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlin.time.Duration.Companion.minutes

class GlobalTopicIcmfReceiver(topics: List<String>,
                              private val cryptoSystem: CryptoSystem) : IcmfReceiver<GlobalTopicsRoute, String, Long>, Shutdownable {
    companion object : KLogging() {
        val pollInterval = 1.minutes
    }

    private val route = GlobalTopicsRoute(topics)
    private val pipes: ConcurrentMap<String, GlobalTopicPipe> = ConcurrentHashMap()
    private val job: Job

    init {
        val clusters = lookupAllClustersInD1()
        for (clusterName in clusters) {
            pipes[clusterName] = GlobalTopicPipe(route, clusterName, cryptoSystem)
        }
        job = CoroutineScope(Dispatchers.IO).launch(CoroutineName("clusters-updater")) {
            while (true) {
                delay(pollInterval)
                try {
                    updateClusters()
                } catch (e: Exception) {
                    logger.error("Cluster update failed: ${e.message}", e)
                }
            }
        }
    }

    private fun updateClusters() {
        logger.info("Updating set of clusters")
        val currentClusters = pipes.keys
        val updatedClusters = lookupAllClustersInD1()
        val removedClusters = currentClusters - updatedClusters
        val addedClusters = updatedClusters - currentClusters
        for (clusterName in removedClusters) {
            pipes.remove(clusterName)?.shutdown()
        }
        for (clusterName in addedClusters) {
            pipes[clusterName] = GlobalTopicPipe(route, clusterName, cryptoSystem)
        }
        logger.info("Updated set of clusters")
    }

    override fun getRelevantPipes(): List<GlobalTopicPipe> = pipes.values.toList()

    override fun shutdown() {
        job.cancel()
        for (pipe in pipes.values) {
            pipe.shutdown()
        }
    }

    private fun lookupAllClustersInD1(): Set<String> = TODO("lookupAllClustersInD1")
}
