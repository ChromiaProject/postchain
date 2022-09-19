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

class GlobalTopicIcmfReceiver(topics: List<String>, databaseOperations: DatabaseOperations, cryptoSystem: CryptoSystem) : IcmfReceiver<GlobalTopicsRoute, String, Long>, Shutdownable {
    companion object : KLogging()

    private val pipes: ConcurrentMap<String, GlobalTopicPipe> = ConcurrentHashMap()
    private val job: Job

    init {
        val clusters = lookupAllClustersInD1()
        for (clusterName in clusters) {
            pipes[clusterName] = GlobalTopicPipe(GlobalTopicsRoute(topics), clusterName, cryptoSystem)
        }
        logger.info("Starting coroutine from ${Thread.currentThread().name}")
        job = CoroutineScope(Dispatchers.IO).launch(CoroutineName("clusters-updater")) {
            while (true) {
                delay(1000)
                logger.info("In coroutine ${Thread.currentThread().name}")
                // TODO update clusters
            }
        }
        logger.info("Started coroutine from ${Thread.currentThread().name}")
    }

    override fun getRelevantPipes(): List<GlobalTopicPipe> = pipes.values.toList()

    override fun shutdown() {
        logger.info("Stopping coroutine from ${Thread.currentThread().name}")
        job.cancel()
        logger.info("Stopped coroutine from ${Thread.currentThread().name}")
        for (pipe in pipes.values) {
            pipe.shutdown()
        }
    }

    private fun lookupAllClustersInD1(): Set<String> = setOf()  //  TODO("Not yet implemented")
}
