package net.postchain.d1.icmf

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KLogging
import net.postchain.base.Storage
import net.postchain.base.withReadConnection
import net.postchain.client.core.PostchainClientProvider
import net.postchain.core.Shutdownable
import net.postchain.crypto.CryptoSystem
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlin.time.Duration.Companion.minutes

class GlobalTopicIcmfReceiver(topics: List<String>,
                              private val cryptoSystem: CryptoSystem,
                              private val storage: Storage,
                              private val chainID: Long,
                              private val postchainClientProvider: PostchainClientProvider) : IcmfReceiver<GlobalTopicsRoute, String, Long>, Shutdownable {
    companion object : KLogging() {
        val pollInterval = 1.minutes
    }

    private val route = GlobalTopicsRoute(topics)
    private val pipes: ConcurrentMap<String, GlobalTopicPipe> = ConcurrentHashMap()
    private val job: Job

    init {
        val clusters = lookupAllClustersInD1()
        for (clusterName in clusters) {
            pipes[clusterName] = createPipe(clusterName)
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
            pipes[clusterName] = createPipe(clusterName)
        }
        logger.info("Updated set of clusters")
    }

    private fun createPipe(clusterName: String): GlobalTopicPipe {
        val lastAnchorHeight = withReadConnection(storage, chainID) {
            IcmfDatabaseOperations.loadLastAnchoredHeight(it, clusterName)
        }

        return GlobalTopicPipe(route, clusterName, cryptoSystem, lastAnchorHeight, postchainClientProvider)
    }

    override fun getRelevantPipes(): List<GlobalTopicPipe> = pipes.values.toList()

    override fun shutdown() {
        job.cancel()
        for (pipe in pipes.values) {
            pipe.shutdown()
        }
    }

    private fun lookupAllClustersInD1(): Set<String> = setOf("cluster0") // TODO Implement
}
