package net.postchain.base.icmf

import net.postchain.core.BlockchainProcess
import net.postchain.core.BlockchainRid

/**
 * The a simple ICMF pipe connection strategy, just listen to all chains with lower level.
 *
 * Note1: This strategy arise out of necessity, due to the [IcmfFetcher] we cannot listen to chains started before
 *        the current chain. Since we have the startup sequence limiting who can listen to what, we also avoid
 *        the potential problem of two chains listening to each other (which is impossible with this architecture).
 *
 * Note2: If two chains have the same level they will ignore each other. This is by design, b/c that might be what
 *        you need.
 */
class LevelConnectionChecker(
    val myBcRid: BlockchainRid,
    val myListeningLevel: Int // We listen to all chains with a lower level (chains are started reversed level order)
    ) : ConnectionChecker {

    companion object {
        const val NOT_LISTENER = -1 // A low number will automatically be listened too
        const val MID_LEVEL = 5 // All [ConnectionChecker] that don't use Levels get 5
        const val HIGH_LEVEL = 10 // Listening to most other chains. Reserved for essential chains, like Anchoring
                                  // (i.e. if you use higher than 10 Anchoring won't see the chain)
    }

    /**
     * @param listeningChainRid is the potential listener chain of the connection
     * @param bcProcess represents the potential source process
     * @param controller is the [IcmfController] (could be needed for something)
     * @return true if the source chain has no level or a lower level than this listener has
     */
    override fun shouldConnect(listeningChainRid: BlockchainRid, bcProcess: BlockchainProcess, controller: IcmfController): Boolean {
        val conf = bcProcess.getEngine().getConfiguration()
        val sourceChainIid: Long = conf.chainID

        // Verify we don't have errors
        if (myBcRid != listeningChainRid) {
            controller.logger.warn("shouldConnect() -- listening chain id: ${listeningChainRid.toShortHex()} used on a ConnectionChecker for another listener: ${myBcRid.toShortHex()}. Why?")
        }
        if (controller.icmfReceiver.isSourceAndTargetConnected(sourceChainIid, listeningChainRid)) {
            controller.logger.warn("shouldConnect() -- source chain id: $sourceChainIid and listening chain id: ${listeningChainRid.toShortHex()} already connected")
            return false
        }

        // Compare levels
        val listenerSetting = conf.icmfListener
        val sourceLevel: Int = if (listenerSetting != null) {
            listenerSetting.toIntOrNull()?: MID_LEVEL // A listener, but we don't know the level
        } else {
            NOT_LISTENER // If this setting is null this is a source chain
        }
        return myListeningLevel > sourceLevel
    }
}