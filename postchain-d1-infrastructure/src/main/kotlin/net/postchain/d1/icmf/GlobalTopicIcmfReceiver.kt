package net.postchain.d1.icmf

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mu.KLogging
import net.postchain.base.withReadConnection
import net.postchain.client.core.PostchainClientProvider
import net.postchain.common.BlockchainRid
import net.postchain.core.Shutdownable
import net.postchain.core.Storage
import net.postchain.crypto.CryptoSystem
import net.postchain.d1.cluster.ClusterManagement
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlin.time.Duration.Companion.minutes

class GlobalTopicIcmfReceiver(private val topics: List<String>,
                              private val cryptoSystem: CryptoSystem,
                              private val storage: Storage,
                              private val chainID: Long,
                              private val clusterManagement: ClusterManagement,
                              private val postchainClientProvider: PostchainClientProvider)
    : IcmfReceiver<GlobalTopicRoute, String, Long>, Shutdownable {
    companion object : KLogging() {
        val pollInterval = 1.minutes
    }

    private val pipes: ConcurrentMap<Pair<String, String>, GlobalTopicPipe> = ConcurrentHashMap()
    private val job: Job

    init {
        val lastMessageHeights = withReadConnection(storage, chainID) {
            IcmfDatabaseOperations.loadAllLastMessageHeights(it)
        }

        val clusters = clusterManagement.getAllClusters()
        for (clusterName in clusters) {
            for (topic in topics) {
                pipes[clusterName to topic] = createPipe(clusterName, topic, lastMessageHeights.filter { it.topic == topic }.map { it.sender to it.height })
            }
        }
        job = CoroutineScope(Dispatchers.IO).launch(CoroutineName("clusters-updater")) {
            while (isActive) {
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
        val currentClusters = pipes.keys.map { it.first }.toSet()
        val updatedClusters = clusterManagement.getAllClusters().toSet()
        val removedClusters = currentClusters - updatedClusters
        val addedClusters = updatedClusters - currentClusters
        for (clusterName in removedClusters) {
            for (topic in topics) {
                pipes.remove(clusterName to topic)?.shutdown()
            }
        }
        for (clusterName in addedClusters) {
            for (topic in topics) {
                pipes[clusterName to topic] = createPipe(clusterName, topic, listOf())
            }
        }
        logger.info("Updated set of clusters")
    }

    private fun createPipe(clusterName: String, topic: String, lastMessageHeights: List<Pair<BlockchainRid, Long>>): GlobalTopicPipe {
        val lastAnchorHeight = withReadConnection(storage, chainID) {
            IcmfDatabaseOperations.loadLastAnchoredHeight(it, clusterName, topic)
        }

        return GlobalTopicPipe(GlobalTopicRoute(topic), clusterName, cryptoSystem, lastAnchorHeight, postchainClientProvider,
                clusterManagement,
                lastMessageHeights)
    }

    override fun getRelevantPipes(): List<GlobalTopicPipe> = pipes.values.toList()

    override fun shutdown() {
        job.cancel()
        for (pipe in pipes.values) {
            pipe.shutdown()
        }
    }
}
