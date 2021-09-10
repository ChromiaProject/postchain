package net.postchain.base.icmf

import mu.KLogging
import net.postchain.base.BaseBlockWitness
import net.postchain.base.Storage
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadConnection
import net.postchain.core.BlockWitness
import net.postchain.core.ProgrammerMistake
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvFactory

/**
 * Responsible for sending message(s) when a block was built to the relevant pipes
 * Messages are received by [IcmfReceiver].
 *
 * TODO: Olle: We don't want to lose messages when the node goes down, so upon startup it must be clear what messages have been pushed to a pipe but never been consumed.
 */
class IcmfDispatcher {

    companion object : KLogging()

    val icmfsourceToPipesMap = HashMap<Long, ArrayList<IcmfPipe>>()
    val icmfsourceToHeightMap = HashMap<Long, Long>() // Keeps track of the last seen height (mostly to check for errors)


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
     * Put new message in all pipes linked to the given source chain.
     *
     * TODO: Olle: currently we get the header from DB, but better performance would be to have it sent to us.
     *
     * @param sourceChainIid is the BC we want to dispatch messages for
     * @param newHeight is the new height of the source chain
     */
    fun newBlockHeight(sourceChainIid: Long, newHeight: Long, storage: Storage) {
        var pipes = icmfsourceToPipesMap[sourceChainIid]
        if (pipes == null) {
            logger.warn("newBlockHeight() -- chain: $sourceChainIid doesn't have any pipes") // Isn't this strange? All chains have anchor chain at least?
        } else {
            if (logger.isDebugEnabled) {
                logger.debug("newBlockHeight() -- trigger chain: $sourceChainIid for height: $newHeight with ${pipes.size} pipes")
            }

            // Slow to fetch the header from DB. Investigate if it can be passed.
            heightCheck(sourceChainIid, newHeight)
            var gtvHeader: Gtv? = null
            var gtvWitness: Gtv? = null
            withReadConnection(storage, sourceChainIid) { eContext ->
                val dba = DatabaseAccess.of(eContext)
                val blockRID = dba.getBlockRID(eContext, newHeight)
                if (blockRID != null) {
                    // Get raw data
                    val rawHeader = dba.getBlockHeader(eContext, blockRID)
                    val rawWitness = dba.getWitnessData(eContext, blockRID)

                    // Transform raw bytes to GTV
                    gtvHeader = GtvDecoder.decodeGtv(rawHeader)
                    gtvWitness = GtvFactory.gtv(rawWitness)  // This is a primitive GTV encoding, but all we have
                } else {
                    throw ProgrammerMistake("Block not found, chainId $sourceChainIid, height: $newHeight")
                }
            }


            for (pipe in pipes) {
                val pkg = IcmfPackage.build(newHeight, gtvHeader!!, gtvWitness!!) // TODO: Olle: should add messages
                pipe.push(pkg)
            }

            icmfsourceToHeightMap[sourceChainIid] = newHeight
        }
    }

    private fun heightCheck(sourceChainIid: Long, newHeight: Long) {
        var lastHeight = icmfsourceToHeightMap[sourceChainIid]
        if (lastHeight != null) {
            if (lastHeight + 1 != newHeight) {
                throw ProgrammerMistake("One block has gone missing for source chain: $sourceChainIid , last seen height $lastHeight but new height is $newHeight")
            }
        } else {
            throw ProgrammerMistake("Heights for source chain: $sourceChainIid hasn't been initialized properly.")
        }
    }

}