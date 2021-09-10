package net.postchain.base.icmf

import net.postchain.base.BlockchainRelatedInfo
import net.postchain.core.BlockchainRid

/**
 * Receives the messages sent from the [IcmfDispatcher] (via [IcmfPipe]).
 */
class IcmfReceiver {

    // On the receiving end we want to be able to access all pipes via listener chain RID
    val listenerChainToPipes: MutableMap<BlockchainRid, MutableList<IcmfPipe>> = HashMap()


    /**
     * @return true if given source and target chains are already connected
     */
    fun isSourceAndTargetConnected(sourceChainIid: Long, listenerChainRid: BlockchainRid): Boolean {
        val listenerPipes = getListOrAddIfNotExists(listenerChainRid)
        for (pipe in listenerPipes) {
            if (pipe.sourceChainIid == sourceChainIid) {
                return true
            }
        }
        return false
    }

    /**
     * Return all pipes for the given chain that have new data
     *
     * @param listenerChainRid is the target chain we want to check updates for
     * @return a mutable list of pipes (not the original pipe list)
     */
    fun getPipesForChain(listenerChainRid: BlockchainRid): List<IcmfPipe> {
        val allPipes = getListOrAddIfNotExists(listenerChainRid)

        val dataPipes = ArrayList<IcmfPipe>() // Make a defensive copy of the pipe collection
        for (pipe in allPipes) {
            if (!pipe.isEmpty()) { // Only put non-empty pipes in here (to save time)
                dataPipes.add(pipe)
            }
        }
        return dataPipes
    }

    /**
     * Adds a pipe for the given (listener) chain and returns the new pipe
     *
     * @param sourceChainIid is the chain that should send messages
     * @param listenerChainInfo is the chain that should receive messages (we might only have BcRID at this point)
     */
    fun connectPipe(sourceChainIid: Long, listenerChainInfo: BlockchainRelatedInfo): IcmfPipe {
        val newPipe = IcmfPipe(sourceChainIid, listenerChainInfo)
        val pipes = getListOrAddIfNotExists(listenerChainInfo.blockchainRid)
        pipes.add(newPipe)
        return newPipe
    }


    private fun getListOrAddIfNotExists(listenerChainRid: BlockchainRid): MutableList<IcmfPipe> {
        var pipes: MutableList<IcmfPipe>? = listenerChainToPipes[listenerChainRid]
        if (pipes == null) {
            pipes = ArrayList<IcmfPipe>()
            listenerChainToPipes[listenerChainRid] = pipes
        }
        return pipes
    }

}