package net.postchain.metrics

import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.Timer
import net.postchain.common.BlockchainRid
import net.postchain.core.NodeRid
import net.postchain.ebft.message.EbftMessage
import net.postchain.logging.BLOCKCHAIN_RID_TAG
import net.postchain.logging.CHAIN_IID_TAG
import net.postchain.logging.MESSAGE_TYPE_TAG
import net.postchain.logging.TARGET_NODE_TAG
import net.postchain.logging.SOURCE_NODE_TAG

class MessageDurationTrackerMetricsFactory(chainIID: Long, blockchainRid: BlockchainRid, senderPeer: String) {

    private val ebftResponseTimeBuilder: Timer.Builder = Timer.builder("ebftResponseTime")
            .description("EBFT message response time")
            .tag(CHAIN_IID_TAG, chainIID.toString())
            .tag(BLOCKCHAIN_RID_TAG, blockchainRid.toHex())
            .tag(SOURCE_NODE_TAG, senderPeer)

    fun createTimer(recipientPeer: NodeRid, receivedMessage: EbftMessage): Timer =
            ebftResponseTimeBuilder
                    .tag(TARGET_NODE_TAG, recipientPeer.toHex())
                    .tag(MESSAGE_TYPE_TAG, receivedMessage::class.java.simpleName)
                    .register(Metrics.globalRegistry)
}