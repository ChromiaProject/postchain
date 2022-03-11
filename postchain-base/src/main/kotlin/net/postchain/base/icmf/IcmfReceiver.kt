package net.postchain.base.icmf

import net.postchain.base.BlockchainRelatedInfo
import net.postchain.core.BlockchainRid
import net.postchain.core.ProgrammerMistake

/**
 * Receives the messages sent from the [IcmfDispatcher] (via [IcmfPipe]).
 */
class IcmfReceiver {

    // listener -> source map
    // (On the receiving end we want to know all pipes a listener should listen to)
    val listenerChainToPipes: MutableMap<Long, MutableList<IcmfPipe>> = HashMap()

    /**
     * Throw away all pipes immediately (don't try to empty them first)
     */
    fun chainShuttingDown(chainIid: Long) {
        listenerChainToPipes.remove(chainIid)
    }

    /**
     * @return true if given source and target chains are already connected
     */
    fun isSourceAndTargetConnected(sourceChainIid: Long, listenerChainIid: Long): Boolean {
        val listenerPipes = getListOrAddIfNotExists(listenerChainIid)
        for (pipe in listenerPipes) {
            if (pipe.sourceChainIid == sourceChainIid) {
                return true
            }
        }
        return false
    }

    /**
     * Return all pipes for the given chain
     *
     * @param listenerChainIid is the target chain we want to check pipes for
     * @return a list of pipes
     */
    fun getPipesForListenerChain(listenerChainIid: Long): List<IcmfPipe> = getListOrAddIfNotExists(listenerChainIid)

    /**
     * Adds a pipe for the given (listener) chain and returns the new pipe
     *
     * @param sourceChainIid is the chain that should send messages
     * @param listenerChainIid is the chain that should receive messages
     * @param fetcher is used to fetch new [IcmfPackage] from the source chain.
     */
    fun connectPipe(sourceChainIid: Long, listenerChainIid: Long, fetcher: IcmfFetcher): IcmfPipe {
        val newPipe = IcmfPipe(sourceChainIid, listenerChainIid, fetcher)
        val pipes = getListOrAddIfNotExists(listenerChainIid)
        pipes.add(newPipe)
        return newPipe
    }

    // Internal helper
    private fun getListOrAddIfNotExists(listenerChainIid: Long): MutableList<IcmfPipe> {
        var pipes: MutableList<IcmfPipe>? = listenerChainToPipes[listenerChainIid]
        if (pipes == null) {
            pipes = mutableListOf<IcmfPipe>()
            listenerChainToPipes[listenerChainIid] = pipes
        }
        return pipes
    }

}