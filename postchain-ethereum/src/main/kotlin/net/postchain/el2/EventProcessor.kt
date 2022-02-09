package net.postchain.el2

import mu.KLogging
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
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.EthFilter
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

class EthereumEventProcessor(
    private val web3c: Web3Connector,
    private val contract: ChrL2,
    private val readOffset: BigInteger,
    blockchainEngine: BlockchainEngine
) : EventProcessor, AbstractBlockchainProcess("ethereum-event-processor", blockchainEngine) {

    private val events: Queue<Log> = LinkedList()
    private var lastReadBlockHeight: BigInteger?

    init {
        lastReadBlockHeight = getLastEthereumBlockHeight()
    }

    companion object : KLogging()

    override fun action() {
        while (isQueueFull()) {
            logger.debug("Event queue is full, waiting for events to be processed before reading more")
            sleep(1000)
        }

        val currentBlockHeight = web3c.web3j.ethBlockNumber().send().blockNumber
        if (currentBlockHeight < readOffset) {
            logger.debug { "Not enough blocks built on ethereum yet, current height: $currentBlockHeight, read offset: $readOffset" }
            return
        }
        val to = currentBlockHeight.minus(readOffset)

        val from = if (lastReadBlockHeight != null) {
            DefaultBlockParameter.valueOf(lastReadBlockHeight!!.plus(BigInteger.ONE))
        } else {
            DefaultBlockParameter.valueOf(DefaultBlockParameterName.EARLIEST.name)
        }

        val filter = EthFilter(from, DefaultBlockParameter.valueOf(to), contract.contractAddress)
        filter.addSingleTopic(EventEncoder.encode(ChrL2.DEPOSITED_EVENT))

        val logResponse = web3c.web3j.ethGetLogs(filter).send()
        if (logResponse.hasError()) {
            logger.error("Cannot read data from eth via web3j", logResponse.error)
        } else {
            logResponse.result.forEach {
                val log = (it as EthLog.LogObject).get()
                processLogEvent(log)
            }
            lastReadBlockHeight = to
        }
        // Sleep a bit until next query
        sleep(500)
    }

    override fun cleanup() {}

    override fun isValidEventData(ops: Array<OpData>): Boolean {
        var isValid = true
        for (op in ops) {
            if (op.opName == OP_ETH_BLOCK) {
                val lastEthBlock =
                    web3c.web3j.ethGetBlockByNumber(DefaultBlockParameter.valueOf(op.args[0].asBigInteger()), false)
                        .send()
                if (lastEthBlock.block.hash != op.args[1].asString()) {
                    isValid = false
                    break
                }
            }
            if (op.opName == OP_ETH_EVENT) {
                var isValidEvent = false
                val tnx = web3c.web3j.ethGetTransactionReceipt(op.args[2].asString()).send()
                val logs = tnx.transactionReceipt.get().logs
                for (log in logs) {
                    if (log.topics[0] == op.args[4].asString()) {
                        if (log.data.takeLast(64).toBigInteger(16) == op.args[8].asBigInteger()
                            && log.topics[1].takeLast(64).takeLast(40) == op.args[6].asString().takeLast(40)
                            && log.topics[2].takeLast(64).takeLast(40) == op.args[7].asString().takeLast(40)
                        ) {
                            isValidEvent = true
                            break
                        }
                    }
                }
                if (!isValidEvent) {
                    isValid = false
                    break
                }
            }
        }
        return isValid
    }

    override fun getEventData(): Pair<Array<Gtv>, List<Array<Gtv>>> {
        val from = getLastEthereumBlockHeight()?.plus(BigInteger.ONE) ?: BigInteger.ZERO

        // If we have any old events in the queue prior to next unprocessed block we may prune them
        pruneEvents(from)

        return parseEvents()
    }

    private fun getLastEthereumBlockHeight(): BigInteger? {
        val block = blockchainEngine.getBlockQueries().query("get_last_eth_block", gtv(mutableMapOf())).get()
        if (block == GtvNull) {
            return null
        }

        val blockHeight = block.asDict()["eth_block_height"]
            ?: throw ProgrammerMistake("Last eth block has no height stored")

        return blockHeight.asBigInteger()
    }

    @Synchronized
    private fun isQueueFull(): Boolean {
        return events.size > 1_000
    }

    @Synchronized
    private fun parseEvents(): Pair<Array<Gtv>, List<Array<Gtv>>> {
        if (events.isEmpty()) {
            logger.debug("No events to process yet")
            return Pair(emptyArray(), emptyList())
        }

        val out = events.stream()
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

        val toBlock: Array<Gtv> = arrayOf(gtv(events.last().blockNumber), gtv(events.last().blockHash))
        return Pair(toBlock, out)
    }

    @Synchronized
    private fun processLogEvent(log: Log) {
        events.add(log)
    }

    @Synchronized
    private fun pruneEvents(upToHeight: BigInteger) {
        var nextLogEvent = events.peek()
        while (nextLogEvent != null && nextLogEvent.blockNumber <= upToHeight) {
            events.poll()
            nextLogEvent = events.peek()
        }
    }
}
