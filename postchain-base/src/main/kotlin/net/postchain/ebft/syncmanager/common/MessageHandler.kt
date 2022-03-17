package net.postchain.ebft.syncmanager.common

import net.postchain.core.NodeRid
import net.postchain.ebft.message.Message

interface MessageHandler {
    fun handleMessage(nodeId: NodeRid, message: Message)
}