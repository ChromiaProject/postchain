package net.postchain.network.mastersub

import net.postchain.network.mastersub.protocol.MsMessage

/**
 * Handles a Master-Sub message
 */
interface MsMessageHandler {
    fun onMessage(message: MsMessage)
}


