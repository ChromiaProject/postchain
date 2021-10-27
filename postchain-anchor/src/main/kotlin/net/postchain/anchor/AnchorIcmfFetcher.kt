package net.postchain.anchor

import mu.KLogging
import net.postchain.base.Storage
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.icmf.IcmfFetcher
import net.postchain.base.icmf.IcmfPackage
import net.postchain.base.withReadConnection
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvByteArray
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvFactory

/**
 * Implements the [IcmfFetcher] for Anchoring, i.e. fetches the header and witness data from the DB fo the
 * source chain, and packs this into the [IcmfPackage].
 */
class AnchorIcmfFetcher(
    protected val storage: Storage
) : IcmfFetcher {

    companion object: KLogging()

    var lastHeight: Long? = null // Just to spot strange behaviour, not strictly needed

    override fun fetch(sourceChainIid: Long, height: Long): IcmfPackage? {
        heightCheck(sourceChainIid, height)

        var pkg: IcmfPackage? = null
        withReadConnection(storage, sourceChainIid) { eContext ->
            val dba = DatabaseAccess.of(eContext)

            val blockRID = dba.getBlockRID(eContext, height)
            if (blockRID != null) {
                val gtvBlockRid: Gtv = GtvByteArray(blockRID)

                // Get raw data
                val rawHeader = dba.getBlockHeader(eContext, blockRID)  // Note: expensive
                val rawWitness = dba.getWitnessData(eContext, blockRID)

                // Transform raw bytes to GTV
                val gtvHeader: Gtv = GtvDecoder.decodeGtv(rawHeader)
                val gtvWitness: Gtv = GtvFactory.gtv(rawWitness)  // This is a primitive GTV encoding, but all we have

                pkg = IcmfPackage.build(height, gtvBlockRid, gtvHeader, gtvWitness)
            } else {
                // Not a problem, this happens when we get up to speed with the underlying chain
                if (logger.isDebugEnabled) {
                    logger.debug("Block not found, chainId $sourceChainIid, height: $height, nothing to anchor")
                }
            }
        }
        return pkg
    }


    /**
     * NOTE! Not sure about this, so these warnings might be overkill.
     * We ARE allowed to fetch the same height multiple times, when a block failed to build
     * and must be re-built again for whatever reason.
     */
    private fun heightCheck(sourceChainIid: Long, newHeight: Long) {
        if (lastHeight == null) {
            // Too expensive to see what the last height was, so we choose not to check it
            logger.info("Assuming this is first time chain: $sourceChainIid is anchored since upstart, use height $newHeight as last height.")

        } else {
            val nextHeight = lastHeight!! + 1
            if (nextHeight == newHeight)  {
                return // All is well
            } else if (lastHeight == newHeight) {
                logger.warn("This is a re-build of the block for source chain: $sourceChainIid, height $lastHeight (happens, but unusual), OR one block has gone missing and we have a bug.")
            } else  {
                logger.warn("Don't understand why we ask for height is $newHeight from source chain: $sourceChainIid, last seen height $lastHeight?")
            }
        }
    }
}