package net.postchain.ebft.message

import io.micrometer.core.instrument.Timer
import mu.KLogging
import net.postchain.base.BaseBlockHeader
import net.postchain.config.app.AppConfig
import net.postchain.core.NodeRid
import net.postchain.metrics.MessageDurationTrackerMetricsFactory
import net.postchain.network.CommunicationManager
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class MessageDurationTracker(
        appConfig: AppConfig,
        private val commManager: CommunicationManager<EbftMessage>,
        private val metricsFactory: MessageDurationTrackerMetricsFactory,
        private val messageToString: (message: EbftMessage, version: Long) -> String,
        private val nanoTime: () -> Long = { System.nanoTime() }
) {
    private val disabled = appConfig.trackedEbftMessageMaxKeepTimeMs == -1L
    private val maxTrackingTimeNs = TimeUnit.MILLISECONDS.toNanos(appConfig.trackedEbftMessageMaxKeepTimeMs)
    private val trackers: MutableMap<NodeRid, MutableMap<MessageTopic, MutableList<TrackedMessage>>> = mutableMapOf()
    private val timers: MutableMap<Pair<NodeRid, EbftMessage>, Timer> = mutableMapOf()

    companion object : KLogging()

    fun send(target: NodeRid, sentMessage: EbftMessage) {
        if (disabled) return
        send(target, sentMessage, nanoTime())
    }

    fun send(targets: List<NodeRid>, sentMessage: EbftMessage) {
        if (disabled) return
        val now = nanoTime()
        targets.forEach { send(it, sentMessage, now) }
    }

    private fun getVersion(node: NodeRid): Long = commManager.getPeerPacketVersion(node)

    private fun send(target: NodeRid, sentMessage: EbftMessage, time: Long) {
        trackers.getOrPut(target) { mutableMapOf() }
                .getOrPut(sentMessage.topic) { mutableListOf() }
                .add(TrackedMessage(sentMessage, time))
        logger.trace { "Start tracking message of type ${messageToString(sentMessage, getVersion(target))} to ${target.toHex()}" }
    }

    fun receive(source: NodeRid, receivedMessage: EbftMessage, data: Any? = null): Duration? {
        if (disabled) return null
        val responseTime = when (receivedMessage) {
            is BlockHeader -> handleBlockHeader(source, receivedMessage)
            is BlockRange -> handleBlockRange(source, receivedMessage)
            is BlockSignature -> handleBlockSignature(source, receivedMessage)
            is CompleteBlock -> handleCompleteBlock(source, receivedMessage)
            is UnfinishedBlock -> handleUnfinishedBlock(source, data)
            else -> null
        }

        responseTime?.also {
            val timer = timers.getOrPut(source to receivedMessage) { metricsFactory.createTimer(source, receivedMessage) }
            timer.record(it.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            logger.trace { "Received response type ${messageToString(receivedMessage, getVersion(source))} from ${source.toHex()} after ${responseTime.inWholeMilliseconds} ms" }
        }
        return responseTime
    }

    fun cleanup() {
        if (disabled) return
        val now = nanoTime()
        trackers.forEach { nodeEntry ->
            nodeEntry.value.forEach { topicEntry ->
                topicEntry.value.removeIf { trackedMessage ->
                    now - trackedMessage.sentTime >= maxTrackingTimeNs
                }
            }
        }
    }

    private fun handleBlockRange(source: NodeRid, receivedMessage: BlockRange): Duration? =
            handleReceivedMessage<GetBlockRange>(source, MessageTopic.GETBLOCKRANGE) { receivedMessage.startAtHeight == it.startAtHeight }

    private fun handleBlockHeader(source: NodeRid, receivedMessage: BlockHeader): Duration? =
            handleReceivedMessage<GetBlockHeaderAndBlock>(source, MessageTopic.GETBLOCKHEADERANDBLOCK) { receivedMessage.requestedHeight == it.height }

    private fun handleBlockSignature(source: NodeRid, receivedMessage: BlockSignature): Duration? =
            handleReceivedMessage<GetBlockSignature>(source, MessageTopic.GETBLOCKSIG) { receivedMessage.blockRID.contentEquals(it.blockRID) }

    private fun handleCompleteBlock(source: NodeRid, receivedMessage: CompleteBlock): Duration? =
            handleReceivedMessage<GetBlockAtHeight>(source, MessageTopic.GETBLOCKATHEIGHT) { receivedMessage.height == it.height }

    private fun handleUnfinishedBlock(source: NodeRid, data: Any?): Duration? {
        val sentTopics = trackers[source] ?: return null
        val header = (data as? BaseBlockHeader) ?: return null
        val blockHeight = header.blockHeaderRec.getHeight()
        val blockRID = header.blockRID

        var responseTime: Duration? = null
        val sentUnfinishedBlock = sentTopics.getOrDefault(MessageTopic.GETUNFINISHEDBLOCK, mutableListOf())
        val sentBlockHeaderAndBlock = sentTopics.getOrDefault(MessageTopic.GETBLOCKHEADERANDBLOCK, mutableListOf())

        for (trackedMessage in sentUnfinishedBlock) {
            val message = trackedMessage.message as? GetUnfinishedBlock ?: continue
            if (blockRID.contentEquals(message.blockRID)) {
                responseTime = getElapsedTime(trackedMessage)
                break
            }
        }
        if (responseTime == null) {
            for (trackedMessage in sentBlockHeaderAndBlock) {
                val message = trackedMessage.message as? GetBlockHeaderAndBlock ?: continue
                if (blockHeight == message.height) {
                    responseTime = getElapsedTime(trackedMessage)
                    break
                }
            }
        }

        responseTime?.apply {
            sentUnfinishedBlock.removeIf { trackedMessage ->
                (trackedMessage.message as? GetUnfinishedBlock)?.let {
                    blockRID.contentEquals(it.blockRID)
                } ?: false
            }
            sentBlockHeaderAndBlock.removeIf { trackedMessage ->
                (trackedMessage.message as? GetBlockHeaderAndBlock)?.let {
                    blockHeight == it.height
                } ?: false
            }
        }
        return responseTime
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : EbftMessage> handleReceivedMessage(source: NodeRid, messageTopic: MessageTopic, messageFilter: (message: T) -> Boolean): Duration? {
        val sentTopics = trackers[source] ?: return null
        val sentTrackedMessages = sentTopics.getOrDefault(messageTopic, mutableListOf())
        var responseTime: Duration? = null
        for (trackedMessage in sentTrackedMessages) {
            val message = trackedMessage.message as? T ?: continue
            if (messageFilter(message)) {
                responseTime = getElapsedTime(trackedMessage)
                break
            }
        }
        responseTime?.apply {
            sentTrackedMessages.removeIf { trackedMessage ->
                (trackedMessage.message as? T)?.let {
                    messageFilter(it)
                } ?: false
            }
        }
        return responseTime
    }

    private fun getElapsedTime(trackedMessage: TrackedMessage): Duration =
            (nanoTime() - trackedMessage.sentTime).toDuration(DurationUnit.NANOSECONDS)

    private data class TrackedMessage(val message: EbftMessage, val sentTime: Long)
}