package net.postchain.base.icmf

import mu.KLogging
import net.postchain.base.BaseBlockWitness
import net.postchain.base.Storage
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadConnection
import net.postchain.core.BlockWitness
import net.postchain.core.BlockchainRid
import net.postchain.core.ProgrammerMistake
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvFactory

/**
 * Responsible for sending message(s) when a block was built to the relevant pipes
 * Messages are received by [IcmfReceiver].
 */
class IcmfDispatcher {

    companion object : KLogging()

    // source -> pipe -> listener
    // (On the sending side we need to know who a source should send to)
    val icmfsourceToPipesMap = HashMap<Long, ArrayList<IcmfPipe>>()
    val icmfsourceToHeightMap = HashMap<Long, Long>() // Keeps track of the last seen height (mostly to check for errors)


    /**
     * Throw away all pipes immediately (don't try to empty them first)
     */
    fun chainShuttingDown(chainIid: Long) {
        icmfsourceToPipesMap.remove(chainIid)
    }

    /**
     * @param sourceChainIid is the source chain we use for lookup
     * @return is all pipes where sourceChainIid is the source.
     */
    fun getAllPipesForSourceChain(sourceChainIid: Long): List<IcmfPipe> {
        val x = icmfsourceToPipesMap[sourceChainIid]
        return if (x == null) {
            listOf()
        } else {
            x.toList()
        }
    }

    /**
     * @param pipe is the pipe we should add
     * @param sourceHeight is the current height of the source chain
     */
    fun addMessagePipe(pipe: IcmfPipe, sourceHeight: Long) {
        val sourceChainIid = pipe.sourceChainIid
        var pipes = icmfsourceToPipesMap[sourceChainIid]
        if (pipes == null) {
            // First time we set a pipe for this source chain
            pipes = ArrayList()
            icmfsourceToPipesMap[sourceChainIid] = pipes
            icmfsourceToHeightMap[sourceChainIid] = sourceHeight
        }
        pipes.add(pipe)
    }

    /**
     * Future work: Here we COULD put new message in all pipes where the given chain is the source.
     *              Currently we don't do any pre-fetching but we let the [IcmfFetcher] do everything from scratch.
     *
     * @param sourceChainIid is the BC we want to dispatch messages for
     * @param newHeight is the new height of the source chain
     * @param storage is the DB
     */
    fun newBlockHeight(sourceChainIid: Long, newHeight: Long, storage: Storage) {
        /*
        var pipes = icmfsourceToPipesMap[sourceChainIid]
        if (pipes != null) {
            if (logger.isDebugEnabled) {
                logger.debug("newBlockHeight() -- trigger chain: $sourceChainIid for height: $newHeight with ${pipes.size} pipes")
            }
        } else {
            if (logger.isDebugEnabled) {
                logger.debug("newBlockHeight() -- chain: $sourceChainIid doesn't have any pipes, probably a pure listening chain")
            }
        }
         */
    }

}