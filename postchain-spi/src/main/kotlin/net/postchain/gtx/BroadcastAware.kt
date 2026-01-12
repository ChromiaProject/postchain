package net.postchain.gtx

import net.postchain.gtv.Gtv
import net.postchain.gtx.special.GTXSpecialTxExtension

/**
 * A [GTXSpecialTxExtension] can implement this interface to communicate with other signer nodes within the cluster.
 */
interface BroadcastAware {
    /** Called at startup to provide the [BroadcastContext] */
    fun initializeBroadcastContext(context: BroadcastContext)
    /** Called when a broadcast message is received from another signer node in the cluster. */
    fun receiveBroadcast(data: Gtv)
}

fun interface BroadcastContext {
    /** Broadcast a message to all other signer nodes in the cluster. */
    fun broadcast(data: Gtv)
}
