package net.postchain.d1.icmf

import net.postchain.common.BlockchainRid
import net.postchain.core.EContext

interface IcmfDatabaseOperations {
    fun initialize(ctx: EContext)
    fun loadLastAnchoredHeight(ctx: EContext, clusterName: String, topic: String): Long
    fun loadLastAnchoredHeights(ctx: EContext): List<AnchorHeight>
    fun saveLastAnchoredHeight(ctx: EContext, clusterName: String, topic: String, anchorHeight: Long)
    fun loadAllLastMessageHeights(ctx: EContext): List<MessageHeightForSender>
    fun loadLastMessageHeight(ctx: EContext, sender: BlockchainRid, topic: String): Long
    fun saveLastMessageHeight(ctx: EContext, sender: BlockchainRid, topic: String, height: Long)
}

data class AnchorHeight(
        val cluster: String,
        val topic: String,
        val height: Long
)

data class MessageHeightForSender(
        val sender: BlockchainRid,
        val topic: String,
        val height: Long
)
