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
    fun getNonEmptyPipesForListenerChain(listenerChainRid: BlockchainRid): List<IcmfPipe> {
        val allPipes = getListOrAddIfNotExists(listenerChainRid)

        val dataPipes = ArrayList<IcmfPipe>() // Make a defensive copy of the pipe collection
        for (pipe in allPipes) {
            if (pipe.hasNewPackets()) { // Only put non-empty pipes in here (to save time)
                dataPipes.add(pipe)
            }
        }
        return dataPipes
    }

    /**
     * Return all pipes for the given chain
     *
     * @param listenerChainRid is the target chain we want to check pipes for
     * @return a mutable list of pipes (not the original pipe list)
     */
    fun getAllPipesForListenerChain(listenerChainRid: BlockchainRid): List<IcmfPipe> = getListOrAddIfNotExists(listenerChainRid).toMutableList()

    /**
     * Adds a pipe for the given (listener) chain and returns the new pipe
     *
     * @param sourceChainIid is the chain that should send messages
     * @param listenerChainInfo is the chain that should receive messages (we might only have BcRID at this point)
     * @param fetcher is used to fetch new [IcmfPackage] from the source chain.
     */
    fun connectPipe(sourceChainIid: Long, listenerChainInfo: BlockchainRelatedInfo, fetcher: IcmfFetcher): IcmfPipe {
        val newPipe = IcmfPipe(sourceChainIid, listenerChainInfo, fetcher)
        val pipes = getListOrAddIfNotExists(listenerChainInfo.blockchainRid)
        pipes.add(newPipe)
        return newPipe
    }

    /**
     * If the given chain is a listener, we should update with its chainIid everywhere
     * (we might only had the BcRID before)
     *
     * Comment: A bit stupid method, only needed for manual mode.
     */
    fun maybeUpdateWithChainIid(chainInfo: BlockchainRelatedInfo) {
        val pipesForListener = listenerChainToPipes[chainInfo.blockchainRid]
        if (pipesForListener != null) {
            for (pipe in pipesForListener!!) {
                if (pipe.listenerChainInfo.blockchainRid == chainInfo.blockchainRid) {
                    if (pipe.listenerChainInfo.chainId == null) {
                        pipe.listenerChainInfo.chainId = chainInfo.chainId
                    }
                } else {
                    throw ProgrammerMistake("Why did we put the pipe ${pipe.pipeId} as a listener to bc RID ${chainInfo.blockchainRid.toShortHex()}")
                }
            }
        }

    }

    // Internal helper
    private fun getListOrAddIfNotExists(listenerChainRid: BlockchainRid): MutableList<IcmfPipe> {
        var pipes: MutableList<IcmfPipe>? = listenerChainToPipes[listenerChainRid]
        if (pipes == null) {
            pipes = ArrayList<IcmfPipe>()
            listenerChainToPipes[listenerChainRid] = pipes
        }
        return pipes
    }

}