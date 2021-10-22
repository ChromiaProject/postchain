package net.postchain.base.icmf

import net.postchain.base.BlockchainRelatedInfo
import net.postchain.core.BlockchainRid
import net.postchain.core.ProgrammerMistake

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
    val myBcIid: Long, // The chain ID that this config belongs to
    val myListeningLevel: Int // We listen to all chains with a lower level (chains are started reversed level order)
    ) : ConnectionChecker {

    companion object {
        const val NOT_LISTENER = -1 // A low number will automatically be listened too
        const val MID_LEVEL = 5 // All [ConnectionChecker] that don't use Levels get 5
        const val HIGH_LEVEL = 10 // Listening to most other chains. Reserved for essential chains, like Anchoring
                                  // (i.e. if you use higher than 10 Anchoring won't see the chain)
    }

    /**
     * For [LevelConnectionChecker] we don't need the listeningChain, we have it from the constructor
     *
     * @param sourceIid represents the potential source process
     * @param listeningIid is the potential listener chain of the connection (not really used).
     * @param controller is the [IcmfController] (could be needed for something)
     * @return true if the source chain has no level or a lower level than this listener has
     */
    override fun shouldConnect(sourceIid: Long, listeningIid: Long, controller: IcmfController): Boolean {

        // Verify we don't have errors
        if (myBcIid != listeningIid) { // Must be used from the listener's perspective (b/c that's where the config was found)
            ProgrammerMistake("shouldConnect() -- Don't use ConnectionChecker of chain id: $myBcIid to check: $sourceIid -> $listeningIid.")
        }
        if (controller.icmfReceiver.isSourceAndTargetConnected(sourceIid, listeningIid)) {
            controller.logger.warn("shouldConnect() -- source chain id: $sourceIid and listening chain id: $listeningIid already connected")
            return false
        }

        // We have the listeners level already, so only have to get the source's to compare
        return myListeningLevel > getSourceLevel(sourceIid, controller)
    }

    /**
     * @param otherSourceConnChecker is a conn checker for another (potential) source chain
     * @return true if we want to use the other chain as a source chain (only based on the given conn checker)
     */
    override fun shouldConnect(otherSourceConnChecker: ConnectionChecker): Boolean {
        val otherLevel = getLevel(otherSourceConnChecker)
        return myListeningLevel > otherLevel
    }

    private fun getSourceLevel(sourceIid: Long, controller: IcmfController): Int {
        // Use controller to find the level of the (potential) source
        val sourceListenerConnChecker = controller.getListenerConnChecker(sourceIid)
        return getLevel(sourceListenerConnChecker)
    }

    private fun getLevel(otherSourceConnChecker: ConnectionChecker?): Int {
        return if (otherSourceConnChecker == null) {
            NOT_LISTENER
        }  else {
            // The given chain is a potential listener
            when (otherSourceConnChecker) {
                is LevelConnectionChecker -> otherSourceConnChecker.myListeningLevel // Get the real level
                else -> MID_LEVEL // We don't know take middle
            }
        }
    }
}