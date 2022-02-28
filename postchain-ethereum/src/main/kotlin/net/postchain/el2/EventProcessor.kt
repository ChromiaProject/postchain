package net.postchain.el2

import net.postchain.base.snapshot.SimpleDigestSystem
import net.postchain.common.data.KECCAK256
import net.postchain.common.toHex
import net.postchain.core.BlockchainEngine
import net.postchain.core.ProgrammerMistake
import net.postchain.core.framework.AbstractBlockchainProcess
import net.postchain.ethereum.contracts.ChrL2
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtx.OpData
import org.web3j.abi.EventEncoder
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
    fun getEventData(): Pair<Array<Gtv>, List<Array<Gtv>>>
    fun isValidEventData(ops: Array<OpData>): Boolean
}

class L2TestEventProcessor : EventProcessor {

    private val ds = SimpleDigestSystem(KECCAK256)

    private var lastBlock = 0

    override fun shutdown() {
        return
    }

    override fun getEventData(): Pair<Array<Gtv>, List<Array<Gtv>>> {
        val out = mutableListOf<Array<Gtv>>()
        val start = lastBlock + 1
        for (i in start..start + 10) {
            lastBlock++
            out.add(generateData(i.toLong(), i))
        }
        return Pair(arrayOf(), out)
    }

    override fun isValidEventData(ops: Array<OpData>): Boolean {
        return true
    }

    private fun generateData(height: Long, i: Int): Array<Gtv> {
        val blockHash = ds.digest(BigInteger.valueOf(i.toLong()).toByteArray()).toHex()
        val transactionHash = ds.digest(BigInteger.valueOf((100 - i).toLong()).toByteArray()).toHex()
        val contractAddress = ds.digest(BigInteger.valueOf(999L).toByteArray()).toHex()
        val from = ds.digest(BigInteger.valueOf(1L).toByteArray()).toHex()
        val to = ds.digest(BigInteger.valueOf(2L).toByteArray()).toHex()
        return arrayOf(
            gtv(height), gtv(blockHash), gtv(transactionHash),
            gtv(i.toLong()), gtv(EventEncoder.encode(ChrL2.DEPOSITED_EVENT)),
            gtv(contractAddress), gtv(from), gtv(to), gtv(BigInteger.valueOf(i.toLong()))
        )
    }
}

/**
 * Reads DEPOSIT events from ethereum.
 *
 * @param readOffset Will return events with this specified offset from the last event we have seen from ethereum
 * (so that slower nodes may have a chance to validate the events)
 */
class EthereumEventProcessor(
    private val web3c: Web3Connector,
    private val contract: ChrL2,
    private val readOffset: BigInteger,
    contractDeployBlock: BigInteger,
    blockchainEngine: BlockchainEngine
) : EventProcessor, AbstractBlockchainProcess("ethereum-event-processor", blockchainEngine) {

    private val events: Queue<Log> = LinkedList()
    private var lastReadLogBlockHeight = contractDeployBlock
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

        val currentBlockHeight = sendWeb3jRequestWithRetry(web3c.web3j.ethBlockNumber()).blockNumber
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
            contract.contractAddress
        )
        filter.addSingleTopic(EventEncoder.encode(ChrL2.DEPOSITED_EVENT))

        val logResponse = sendWeb3jRequestWithRetry(web3c.web3j.ethGetLogs(filter))
        // Fetch and store the block at read offset for the consumer thread (so it can emit it along with prior events)
        val newReadOffsetBlock = if (to - readOffset >= BigInteger.ZERO) {
            sendWeb3jRequestWithRetry(
                web3c.web3j.ethGetBlockByNumber(DefaultBlockParameter.valueOf(to - readOffset), false)
            ).block
        } else {
            null
        }
        val sortedLogs = logResponse.logs
            .map { (it as EthLog.LogObject).get() }
            .sortedBy { it.blockNumber }
        processLogEventsAndUpdateOffsets(sortedLogs, to, newReadOffsetBlock)

        while (isQueueFull()) {
            logger.debug("Wait for events to be consumed until we read more")
            sleep(500)
        }
    }

    override fun cleanup() {}

    @Synchronized
    override fun isValidEventData(ops: Array<OpData>): Boolean {
        val lastCommittedBlock = getLastCommittedEthereumBlockHeight()
        // If we have any old events in the queue we may prune them
        if (lastCommittedBlock != null) {
            pruneEvents(lastCommittedBlock)
        }

        for (op in ops) {
            if (op.opName == OP_ETH_BLOCK) {
                // We don't store all blocks, so we need to go fetch it over network
                val blockNumber = op.args[0].asBigInteger()
                val lastEthBlock = sendWeb3jRequestWithRetry(
                    web3c.web3j.ethGetBlockByNumber(
                        DefaultBlockParameter.valueOf(blockNumber),
                        false
                    ), 3, 0
                )

                if (lastEthBlock.hasError() || lastEthBlock.block.hash != op.args[1].asString()) {
                    return false
                }
            }
            if (op.opName == OP_ETH_EVENT) {
                val eventBlockNumber = op.args[0].asBigInteger()
                val eventTransactionHash = op.args[2].asString()
                val eventLogIndex = op.args[3].asBigInteger()

                val eventLog = if (eventBlockNumber > lastReadLogBlockHeight) {
                    // Checking over network if we are behind
                    val eventTransaction =
                        sendWeb3jRequestWithRetry(web3c.web3j.ethGetTransactionReceipt(eventTransactionHash), 3, 0)
                    if (eventTransaction.hasError() || !eventTransaction.transactionReceipt.isPresent) {
                        null
                    } else {
                        eventTransaction.transactionReceipt.get().logs.find { it.logIndex == eventLogIndex }
                    }
                } else {
                    // We have stored the event, so we can just check for it, no network call needed
                    events.find { it.transactionHash == eventTransactionHash && it.logIndex == eventLogIndex }
                }

                if (eventLog == null || !isMatchingEvents(op.args, eventLog)) {
                    return false
                }
            }
        }
        return true
    }

    private fun isMatchingEvents(eventArgs: Array<Gtv>, eventLog: Log): Boolean {
        val eventParameters = ChrL2.staticExtractEventParameters(ChrL2.DEPOSITED_EVENT, eventLog)
        return eventArgs[0].asBigInteger() == eventLog.blockNumber &&
                eventArgs[1].asString() == eventLog.blockHash &&
                eventArgs[6].asString() == eventParameters.indexedValues[0].value &&
                eventArgs[7].asString() == eventParameters.indexedValues[1].value &&
                eventArgs[8].asBigInteger() == eventParameters.nonIndexedValues[0].value
    }

    @Synchronized
    override fun getEventData(): Pair<Array<Gtv>, List<Array<Gtv>>> {
        val lastCommittedBlock = getLastCommittedEthereumBlockHeight()
        // If we have any old events in the queue we may prune them
        if (lastCommittedBlock != null) {
            pruneEvents(lastCommittedBlock)
        }

        if (!::readOffsetBlock.isInitialized || (lastCommittedBlock != null && readOffsetBlock.number <= lastCommittedBlock)) {
            logger.debug("No events to process yet")
            return Pair(emptyArray(), emptyList())
        }

        val out = events.stream()
            .takeWhile { it.blockNumber <= readOffsetBlock.number }
            .map {
                val eventParameters = ChrL2.staticExtractEventParameters(ChrL2.DEPOSITED_EVENT, it)
                arrayOf<Gtv>(
                    gtv(it.blockNumber),
                    gtv(it.blockHash),
                    gtv(it.transactionHash),
                    gtv(it.logIndex),
                    gtv(EventEncoder.encode(ChrL2.DEPOSITED_EVENT)),
                    gtv(contract.contractAddress),
                    gtv(eventParameters.indexedValues[0].value as String),       // owner
                    gtv(eventParameters.indexedValues[1].value as String),       // token
                    gtv(eventParameters.nonIndexedValues[0].value as BigInteger) // value
                )
            }.toList()

        val toBlock: Array<Gtv> = arrayOf(gtv(readOffsetBlock.number), gtv(readOffsetBlock.hash))
        return Pair(toBlock, out)
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
        logs: List<Log>,
        newLastReadLogBlockHeight: BigInteger,
        newReadOffsetBlock: EthBlock.Block?
    ) {
        events.addAll(logs)
        lastReadLogBlockHeight = newLastReadLogBlockHeight
        if (newReadOffsetBlock != null) {
            readOffsetBlock = newReadOffsetBlock
        }
    }

    @Synchronized
    private fun isQueueFull(): Boolean {
        return events.size > MAX_QUEUE_SIZE
    }

    private fun pruneEvents(lastCommittedBlock: BigInteger) {
        var nextLogEvent = events.peek()
        while (nextLogEvent != null && nextLogEvent.blockNumber <= lastCommittedBlock) {
            events.poll()
            nextLogEvent = events.peek()
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

            if (maxRetries == null || maxRetries > 0) {
                if (retryTimeout > 0) {
                    sleep(retryTimeout)
                }
                return sendWeb3jRequestWithRetry(request, maxRetries?.minus(1), retryTimeout)
            }
        }

        return response
    }
}
