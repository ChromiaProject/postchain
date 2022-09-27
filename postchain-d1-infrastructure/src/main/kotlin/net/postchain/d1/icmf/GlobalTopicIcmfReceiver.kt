package net.postchain.d1.icmf

import kotlinx.coroutines.CancellationException
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

class GlobalTopicIcmfReceiver(topics: List<String>,
                              private val cryptoSystem: CryptoSystem,
                              private val storage: Storage,
                              private val chainID: Long,
                              private val clusterManagement: ClusterManagement,
                              private val postchainClientProvider: PostchainClientProvider,
                              private val dbOperations: IcmfDatabaseOperations)
    : IcmfReceiver<GlobalTopicRoute, String, Long>, Shutdownable {
    companion object : KLogging() {
        val pollInterval = 1.minutes
    }

    private val routes = topics.map { GlobalTopicRoute(it) }
    private val pipes: ConcurrentMap<Pair<String, GlobalTopicRoute>, ClusterGlobalTopicPipe> = ConcurrentHashMap()
    private val job: Job

    init {
        val lastMessageHeights = withReadConnection(storage, chainID) {
            dbOperations.loadAllLastMessageHeights(it)
        }

        val clusters = clusterManagement.getAllClusters()
        for (clusterName in clusters) {
            for (route in routes) {
                pipes[clusterName to route] = createPipe(clusterName, route, lastMessageHeights.filter { it.topic == route.topic }.map { it.sender to it.height })
            }
        }

        job = CoroutineScope(Dispatchers.IO).launch(CoroutineName("clusters-updater")) {
            while (isActive) {
                delay(pollInterval)
                try {
                    logger.info("Updating set of clusters")
                    updateClusters()
                    logger.info("Updated set of clusters")
                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    logger.error("Clusters update failed: ${e.message}", e)
                }
            }
        }
    }

    private fun updateClusters() {
        val currentClusters = pipes.keys.map { it.first }.toSet()
        val updatedClusters = clusterManagement.getAllClusters().toSet()
        val removedClusters = currentClusters - updatedClusters
        val addedClusters = updatedClusters - currentClusters
        for (clusterName in removedClusters) {
            for (route in routes) {
                pipes.remove(clusterName to route)?.shutdown()
            }
        }
        for (clusterName in addedClusters) {
            for (route in routes) {
                pipes[clusterName to route] = createPipe(clusterName, route, listOf())
            }
        }
    }

    private fun createPipe(clusterName: String, route: GlobalTopicRoute, lastMessageHeights: List<Pair<BlockchainRid, Long>>): ClusterGlobalTopicPipe {
        val lastAnchorHeight = withReadConnection(storage, chainID) {
            dbOperations.loadLastAnchoredHeight(it, clusterName, route.topic)
        }

        return ClusterGlobalTopicPipe(route, clusterName, cryptoSystem, lastAnchorHeight, postchainClientProvider,
                clusterManagement,
                lastMessageHeights)
    }

    override fun getRelevantPipes(): List<ClusterGlobalTopicPipe> = pipes.values.toList()

    override fun shutdown() {
        job.cancel()
        for (pipe in pipes.values) {
            pipe.shutdown()
        }
    }
}
