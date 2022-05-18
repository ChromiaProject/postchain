package net.postchain.el2

import mu.KLogging
import net.postchain.core.BlockchainEngine
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.core.framework.AbstractBlockchainProcess
import net.postchain.ethereum.contracts.ChrL2
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtx.OpData
import org.web3j.abi.EventEncoder
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameter
import org.web3j.protocol.core.Request
import org.web3j.protocol.core.Response
import org.web3j.protocol.core.methods.request.EthFilter
import org.web3j.protocol.core.methods.response.EthBlock
import org.web3j.protocol.core.methods.response.EthLog
import org.web3j.protocol.core.methods.response.Log
import java.lang.Thread.sleep
import java.math.BigInteger
import java.util.*
import kotlin.streams.toList

interface EventProcessor {
    fun shutdown()
    fun getEventData(): List<Array<Gtv>>
    fun isValidEventData(ops: Array<OpData>): Boolean
}

/**
 * This event processor is used for nodes that are not connected to ethereum.
 * No EIF special operations will be produced and no operations will be validated against ethereum.
 */
class NoOpEventProcessor : EventProcessor {

    companion object : KLogging()

    override fun shutdown() {}

    override fun getEventData(): List<Array<Gtv>> = emptyList()

    /**
     * We can at least validate structure
     */
    override fun isValidEventData(ops: Array<OpData>): Boolean {
        for (op in ops) {
            if (op.opName == OP_ETH_BLOCK) {
                if (!isValidEthereumBlockFormat(op.args)) {
                    logger.error("Received malformed operation of type $OP_ETH_BLOCK")
                    return false
                }
            } else {
                logger.error("Unknown operation: ${op.opName}")
                return false
            }
        }
        return true
    }

    private fun isValidEthereumEventFormat(opArgs: Array<out Gtv>) = opArgs.size == 6 &&
            opArgs[0].asPrimitive() is String &&
            opArgs[1].asPrimitive() is BigInteger &&
            opArgs[2].asString() == EventEncoder.encode(ChrL2.DEPOSITED_EVENT) &&
            opArgs[3].asPrimitive() is String &&
            opArgs[4].asPrimitive() is BigInteger &&
            opArgs[5].asPrimitive() is Array<*>

    private fun isValidEthereumBlockFormat(opArgs: Array<Gtv>) = opArgs.size == 3 &&
            opArgs[0].asPrimitive() is BigInteger &&
            opArgs[1].asPrimitive() is String &&
            opArgs[2].asArray().all { isValidEthereumEventFormat(it.asArray()) }
}

/**
 * Reads DEPOSIT events from ethereum.
 *
 * @param readOffset Will return events with this specified offset from the last event we have seen from ethereum
 * (so that slower nodes may have a chance to validate the events)
 */
class EthereumEventProcessor(
        private val web3j: Web3j,
        private val contractAddresses: List<String>,
        private val readOffset: BigInteger,
        skipToHeight: BigInteger,
        blockchainEngine: BlockchainEngine
) : EventProcessor, AbstractBlockchainProcess("ethereum-event-processor", blockchainEngine) {

    data class EthereumBlock(val number: BigInteger, val hash: String)

    private val eventBlocks: Queue<Pair<EthereumBlock, List<Log>>> = LinkedList()
    private var lastReadLogBlockHeight = skipToHeight
    private lateinit var readOffsetBlock: EthBlock.Block

    companion object {
        // The idea here is to avoid too big log queries and
        // also potentially filling up our event queue too much
        private const val MAX_READ_AHEAD = 10_000L
        private const val MAX_QUEUE_SIZE = 1_000L
    }

    /**
     * Producer thread will read events from ethereum ond add to queue in this action. Main thread will consume them.
     */
    override fun action() {
        val lastCommittedBlock = getLastCommittedEthereumBlockHeight()
        val from = if (lastCommittedBlock != null) {
            // Skip ahead if we are behind last committed block
            maxOf(lastReadLogBlockHeight, lastCommittedBlock) + BigInteger.ONE
        } else {
            lastReadLogBlockHeight + BigInteger.ONE
        }

        val currentBlockHeight = sendWeb3jRequestWithRetry(web3j.ethBlockNumber()).blockNumber
        // Pacing the reading of logs
        val to = minOf(currentBlockHeight, from + BigInteger.valueOf(MAX_READ_AHEAD))

        if (to < from) {
            logger.debug { "No new blocks to read. We are at height: $to" }
            // Sleep a bit until next attempt
            sleep(500)
            return
        }

        val filter = EthFilter(
            DefaultBlockParameter.valueOf(from),
            DefaultBlockParameter.valueOf(to),
            contractAddresses
        )
        filter.addSingleTopic(EventEncoder.encode(ChrL2.DEPOSITED_EVENT))

        val logResponse = sendWeb3jRequestWithRetry(web3j.ethGetLogs(filter))
        // Fetch and store the block at read offset for the consumer thread (so it can emit it along with prior events)
        val newReadOffsetBlock = if (to - readOffset >= BigInteger.ZERO) {
            sendWeb3jRequestWithRetry(
                web3j.ethGetBlockByNumber(DefaultBlockParameter.valueOf(to - readOffset), false)
            ).block
        } else {
            null
        }

        // Ensure events are sorted on txIndex + logIndex, blocks sorted on block number
        val sortedLogs = logResponse.logs
                .map { (it as EthLog.LogObject).get() }
                .groupBy { EthereumBlock(it.blockNumber, it.blockHash) }
                .mapValues { it.value.sortedWith(compareBy({ event -> event.transactionIndex }, { event -> event.logIndex })) }
                .toList()
                .sortedBy { it.first.number }
        processLogEventsAndUpdateOffsets(sortedLogs, to, newReadOffsetBlock)

        while (isQueueFull()) {
            logger.debug("Wait for events to be consumed until we read more")
            sleep(500)
        }
    }

    override fun cleanup() {
        web3j.shutdown()
    }

    @Synchronized
    override fun isValidEventData(ops: Array<OpData>): Boolean {
        val lastCommittedBlock = getLastCommittedEthereumBlockHeight()
        // If we have any old events in the queue we may prune them
        if (lastCommittedBlock != null) {
            pruneEvents(lastCommittedBlock)
        }

        // We are strict here, if we have not seen something we will not try to go and fetch it.
        // We simply verify that the same blocks and events are coming in the same order that we have seen them
        // If there are too many rejections, readOffset should be increased
        if (ops.size > eventBlocks.size) {
            // We don't have all these blocks
            logger.error("Received unexpected blocks")
            return false
        }
        for ((index, event) in eventBlocks.withIndex()) {
            if (index >= ops.size) break

            val op = ops[index]
            if (op.opName == OP_ETH_BLOCK) {
                val opBlockNumber = op.args[0].asBigInteger()
                val opBlockHash = op.args[1].asString()

                val opEvents = op.args[2].asArray()
                if (opBlockNumber != event.first.number || opBlockHash != event.first.hash) {
                    logger.error("Received unexpected block $opBlockNumber with hash $opBlockHash. Expected block ${event.first.number} with hash ${event.first.hash}")
                    return false
                }
                if (!hasMatchingEvents(opEvents, event.second)) {
                    logger.error("Events in received block $opBlockNumber do not match expected events")
                    return false
                }
            } else {
                logger.error("Unknown operation: ${op.opName}")
                return false
            }
        }
        return true
    }

    private fun hasMatchingEvents(opEvents: Array<out Gtv>, eventLogs: List<Log>): Boolean {
        if (opEvents.size != eventLogs.size) return false

        for ((index, opEvent) in opEvents.withIndex()) {
            val eventLog = eventLogs[index]
            val eventParameters = ChrL2.staticExtractEventParameters(ChrL2.DEPOSITED_EVENT, eventLog)
            if (opEvent[0].asString() != eventLog.transactionHash ||
                    opEvent[1].asBigInteger() != eventLog.logIndex ||
                    opEvent[2].asString() != EventEncoder.encode(ChrL2.DEPOSITED_EVENT) ||
                    opEvent[3].asString() != eventLog.address ||
                    opEvent[4].asBigInteger() != eventParameters.indexedValues[0].value ||
                    !opEvent[5].asArray().contentEquals(
                            GtvDecoder.decodeGtv(eventParameters.nonIndexedValues[0].value as ByteArray).asArray()
                    )
            ) {
                return false
            }
        }
        return true
    }

    @Synchronized
    override fun getEventData(): List<Array<Gtv>> {
        val lastCommittedBlock = getLastCommittedEthereumBlockHeight()
        // If we have any old events in the queue we may prune them
        if (lastCommittedBlock != null) {
            pruneEvents(lastCommittedBlock)
        }

        if (!::readOffsetBlock.isInitialized || (lastCommittedBlock != null && readOffsetBlock.number <= lastCommittedBlock)) {
            logger.debug("No events to process yet")
            return emptyList()
        }

        return eventBlocks.stream()
            .takeWhile { it.first.number <= readOffsetBlock.number }
            .map {
                val events = it.second.map { event ->
                    val eventParameters = ChrL2.staticExtractEventParameters(ChrL2.DEPOSITED_EVENT, event)
                    gtv(listOf(
                            gtv(event.transactionHash),
                            gtv(event.logIndex),
                            gtv(EventEncoder.encode(ChrL2.DEPOSITED_EVENT)),
                            gtv(event.address),
                            gtv(eventParameters.indexedValues[0].value as BigInteger),   // asset type
                            GtvDecoder.decodeGtv(eventParameters.nonIndexedValues[0].value as ByteArray) // payload
                    ))
                }
                arrayOf<Gtv>(
                    gtv(it.first.number),
                    gtv(it.first.hash),
                    gtv(events)
                )
            }.toList()
    }

    private fun getLastCommittedEthereumBlockHeight(): BigInteger? {
        val block = blockchainEngine.getBlockQueries().query("get_last_eth_block", gtv(mutableMapOf())).get()
        if (block == GtvNull) {
            return null
        }

        val blockHeight = block.asDict()["eth_block_height"]
            ?: throw ProgrammerMistake("Last eth block has no height stored")

        return blockHeight.asBigInteger()
    }

    @Synchronized
    private fun processLogEventsAndUpdateOffsets(
            logs: List<Pair<EthereumBlock, List<Log>>>,
            newLastReadLogBlockHeight: BigInteger,
            newReadOffsetBlock: EthBlock.Block?
    ) {
        eventBlocks.addAll(logs)
        lastReadLogBlockHeight = newLastReadLogBlockHeight
        if (newReadOffsetBlock != null) {
            readOffsetBlock = newReadOffsetBlock
        }
    }

    @Synchronized
    private fun isQueueFull(): Boolean {
        return eventBlocks.size > MAX_QUEUE_SIZE
    }

    private fun pruneEvents(lastCommittedBlock: BigInteger) {
        var nextLogEvent = eventBlocks.peek()
        while (nextLogEvent != null && nextLogEvent.first.number <= lastCommittedBlock) {
            eventBlocks.poll()
            nextLogEvent = eventBlocks.peek()
        }
    }

    private fun <T : Response<*>> sendWeb3jRequestWithRetry(
        request: Request<*, T>,
        maxRetries: Int? = null,
        retryTimeout: Long = 500
    ): T {
        val response = request.send()
        if (response.hasError()) {
            logger.error("Web3j request failed with error code: ${response.error.code} and message: ${response.error.message}")

            if ((maxRetries == null) || (maxRetries > 0)) {
                if (retryTimeout > 0) {
                    sleep(retryTimeout)
                }
                return sendWeb3jRequestWithRetry(request, maxRetries?.minus(1), retryTimeout)
            }
        }

        return response
    }
}
