package net.postchain.d1.icmf

import net.postchain.core.Shutdownable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

class GlobalTopicIcmfReceiver(topics: List<String>) : IcmfReceiver<GlobalTopicsRoute, String, Long>, Shutdownable {
    private val pipes: ConcurrentMap<String, GlobalTopicPipe> = ConcurrentHashMap()

    init {
        val clusters = lookupAllClustersInD1()
        for (cluster in clusters) {
            pipes[cluster] = GlobalTopicPipe(GlobalTopicsRoute(topics), cluster)
        }
        // TODO start background process
    }

    override fun getRelevantPipes(): List<GlobalTopicPipe> = pipes.values.toList()

    override fun shutdown() {
        // TODO shutdown background process
    }

    private fun lookupAllClustersInD1(): Set<String> = TODO("Not yet implemented")
}
