package net.postchain.eif

import mu.KLogging
import net.postchain.core.BlockchainEngine
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.hexStringToByteArray
import net.postchain.core.framework.AbstractBlockchainProcess
import net.postchain.gtv.*
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtx.OpData
import org.web3j.abi.EventEncoder
import org.web3j.abi.datatypes.Event
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameter
import org.web3j.protocol.core.Request
import org.web3j.protocol.core.Response
import org.web3j.protocol.core.methods.request.EthFilter
import org.web3j.protocol.core.methods.response.EthLog
import org.web3j.protocol.core.methods.response.Log
import org.web3j.tx.Contract
import java.lang.Thread.sleep
import java.math.BigInteger
import java.util.*
import kotlin.streams.toList

enum class EncodedBlock(val index: Int) {
    NUMBER(0),
    HASH(1),
    EVENTS(2)
}

enum class EncodedEvent(val index: Int) {
    TX_HASH(0),
    LOG_INDEX(1),
    SIGNATURE(2),
    CONTRACT(3),
    NAME(4),
    INDEXED_VALUES(5),
    NON_INDEXED_VALUES(6)
}

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

    private fun isValidEthereumEventFormat(opArgs: Array<out Gtv>) = opArgs.size == 7 &&
            opArgs[EncodedEvent.TX_HASH.index].asPrimitive() is ByteArray &&
            opArgs[EncodedEvent.LOG_INDEX.index].asPrimitive() is BigInteger &&
            opArgs[EncodedEvent.SIGNATURE.index].asPrimitive() is ByteArray &&
            opArgs[EncodedEvent.CONTRACT.index].asPrimitive() is ByteArray &&
            opArgs[EncodedEvent.NAME.index].asPrimitive() is String &&
            opArgs[EncodedEvent.INDEXED_VALUES.index].asPrimitive() is Array<*> &&
            opArgs[EncodedEvent.NON_INDEXED_VALUES.index].asPrimitive() is Array<*>

    private fun isValidEthereumBlockFormat(opArgs: Array<Gtv>) = opArgs.size == 3 &&
            opArgs[EncodedBlock.NUMBER.index].asPrimitive() is BigInteger &&
            opArgs[EncodedBlock.HASH.index].asPrimitive() is ByteArray &&
            opArgs[EncodedBlock.EVENTS.index].asArray().all { isValidEthereumEventFormat(it.asArray()) }
}

/**
 * Reads events from ethereum.
 *
 * @param readOffset Will return events with this specified offset from the last event we have seen from ethereum
 * (so that slower nodes may have a chance to validate the events)
 */
class EthereumEventProcessor(
        private val web3j: Web3j,
        private val contractAddresses: List<String>,
        events: List<Event>,
        private val readOffset: BigInteger,
        skipToHeight: BigInteger,
        blockchainEngine: BlockchainEngine
) : EventProcessor, AbstractBlockchainProcess("ethereum-event-processor", blockchainEngine) {

    data class EthereumBlock(val number: BigInteger, val hash: String)

    private val eventBlocks: Queue<Array<Gtv>> = LinkedList()
    private var lastReadLogBlockHeight = skipToHeight

    private val eventMap = events.associateBy(EventEncoder::encode)
    private val eventSignatures = eventMap.keys.toTypedArray()

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
        filter.addOptionalTopics(*eventSignatures)

        val logResponse = sendWeb3jRequestWithRetry(web3j.ethGetLogs(filter))

        // Ensure events are sorted on txIndex + logIndex, blocks sorted on block number
        val sortedEncodedLogs = logResponse.logs
                .map { (it as EthLog.LogObject).get() }
                .groupBy { EthereumBlock(it.blockNumber, it.blockHash) }
                .mapValues { it.value.sortedWith(compareBy({ event -> event.transactionIndex }, { event -> event.logIndex })) }
                .toList()
                .sortedBy { it.first.number }
                .map(::eventBlockToGtv)
        processLogEventsAndUpdateOffsets(sortedEncodedLogs, to)

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
        for ((index, eventBlock) in eventBlocks.withIndex()) {
            if (index >= ops.size) break

            val op = ops[index]
            if (op.opName == OP_ETH_BLOCK) {
                val opBlockNumber = op.args[EncodedBlock.NUMBER.index].asBigInteger()
                val opBlockHash = op.args[EncodedBlock.HASH.index].asByteArray()

                if (opBlockNumber != eventBlock[EncodedBlock.NUMBER.index].asBigInteger()
                    || !opBlockHash.contentEquals(eventBlock[EncodedBlock.HASH.index].asByteArray())
                ) {
                    logger.error(
                        "Received unexpected block $opBlockNumber with hash $opBlockHash." +
                                " Expected block ${eventBlock[0].asBigInteger()} with hash ${eventBlock[1].asByteArray()}"
                    )
                    return false
                }

                val opEvents = op.args[EncodedBlock.EVENTS.index].asArray()
                if (!hasMatchingEvents(opEvents, eventBlock[EncodedBlock.EVENTS.index].asArray())) {
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

    private fun hasMatchingEvents(opEvents: Array<out Gtv>, eventLogs: Array<out Gtv>): Boolean {
        if (opEvents.size != eventLogs.size) return false

        for ((index, opEvent) in opEvents.withIndex()) {
            val eventLog = eventLogs[index]
            if (!opEvent[EncodedEvent.TX_HASH.index].asByteArray().contentEquals(eventLog[EncodedEvent.TX_HASH.index].asByteArray()) ||
                opEvent[EncodedEvent.LOG_INDEX.index].asBigInteger() != eventLog[EncodedEvent.LOG_INDEX.index].asBigInteger() ||
                !opEvent[EncodedEvent.SIGNATURE.index].asByteArray().contentEquals(eventLog[EncodedEvent.SIGNATURE.index].asByteArray()) ||
                !opEvent[EncodedEvent.CONTRACT.index].asByteArray().contentEquals(eventLog[EncodedEvent.CONTRACT.index].asByteArray()) ||
                opEvent[EncodedEvent.NAME.index].asString() != eventLog[EncodedEvent.NAME.index].asString() ||
                !hasMatchingValues(opEvent[EncodedEvent.INDEXED_VALUES.index].asArray(), eventLog[EncodedEvent.INDEXED_VALUES.index].asArray()) ||
                !hasMatchingValues(opEvent[EncodedEvent.NON_INDEXED_VALUES.index].asArray(), eventLog[EncodedEvent.NON_INDEXED_VALUES.index].asArray())
            ) {
                return false
            }
        }
        return true
    }

    private fun hasMatchingValues(opValues: Array<out Gtv>, eventLogValues: Array<out Gtv>): Boolean {
        if (opValues.size != eventLogValues.size) return false

        for ((index, opValue) in opValues.withIndex()) {
            val eventLogValue = eventLogValues[index]
            when (opValue) {
                is GtvByteArray -> {
                    if (eventLogValue !is GtvByteArray || !opValue.asByteArray().contentEquals(eventLogValue.asByteArray())) {
                        return false
                    }
                }
                is GtvBigInteger, is GtvInteger, is GtvString -> {
                    if (opValue.asPrimitive() != eventLogValue.asPrimitive()) {
                        return false
                    }
                }
                is GtvArray -> {
                    if (eventLogValue !is GtvArray || !hasMatchingValues(opValue.asArray(), eventLogValue.asArray())) {
                        return false
                    }
                }
                else -> throw ProgrammerMistake("Unexpected value gtv type: ${opValue::class}")
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

        return eventBlocks.stream()
            .takeWhile { it[0].asBigInteger() <= lastReadLogBlockHeight - readOffset }
            .toList()
    }

    private fun eventBlockToGtv(eventBlock: Pair<EthereumBlock, List<Log>>): Array<Gtv> {
        val events = eventBlock.second.map { event ->
            val matchingEvent = eventMap[event.topics[0]] ?: throw ProgrammerMistake("No matching event")
            val parameters = Contract.staticExtractEventParameters(matchingEvent, event)
            gtv(listOf(
                gtv(event.transactionHash.substring(2).hexStringToByteArray()),
                gtv(event.logIndex),
                gtv(event.topics[0].substring(2).hexStringToByteArray()),
                gtv(event.address.substring(2).hexStringToByteArray()),
                gtv(matchingEvent.name),
                gtv(parameters.indexedValues.map(TypeToGtvMapper::map)),
                gtv(parameters.nonIndexedValues.map(TypeToGtvMapper::map))
            ))
        }
        return arrayOf(
            gtv(eventBlock.first.number),
            gtv(eventBlock.first.hash.substring(2).hexStringToByteArray()),
            gtv(events)
        )
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
            logs: List<Array<Gtv>>,
            newLastReadLogBlockHeight: BigInteger
    ) {
        eventBlocks.addAll(logs)
        lastReadLogBlockHeight = newLastReadLogBlockHeight
    }

    @Synchronized
    private fun isQueueFull(): Boolean {
        // Just check against the events that we can actually consume
        return eventBlocks.filter {
            it[EncodedBlock.NUMBER.index].asBigInteger() <= lastReadLogBlockHeight - readOffset
        }.size > MAX_QUEUE_SIZE
    }

    private fun pruneEvents(lastCommittedBlock: BigInteger) {
        var nextLogEvent = eventBlocks.peek()
        while (nextLogEvent != null && nextLogEvent[EncodedBlock.NUMBER.index].asBigInteger() <= lastCommittedBlock) {
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
