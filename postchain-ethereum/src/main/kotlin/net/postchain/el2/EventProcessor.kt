package net.postchain.el2

import io.reactivex.disposables.Disposable
import mu.KLogging
import net.postchain.base.snapshot.SimpleDigestSystem
import net.postchain.common.data.KECCAK256
import net.postchain.common.toHex
import net.postchain.core.BlockQueries
import net.postchain.core.ProgrammerMistake
import net.postchain.ethereum.contracts.ChrL2
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtx.OpData
import okhttp3.OkHttpClient
import org.web3j.abi.EventEncoder
import org.web3j.protocol.Web3j
import org.web3j.protocol.Web3jService
import org.web3j.protocol.core.DefaultBlockParameter
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.http.HttpService
import org.web3j.protocol.ipc.UnixIpcService
import org.web3j.protocol.ipc.WindowsIpcService
import org.web3j.tx.ClientTransactionManager
import org.web3j.tx.gas.DefaultGasProvider
import java.math.BigInteger
import java.util.*
import java.util.concurrent.TimeUnit

interface EventProcessor {
    fun shutdown()
    fun getEventData(): Pair<Array<Gtv>, List<Array<Gtv>>>
    fun isValidEventData(ops: Array<OpData>): Boolean
}

abstract class BaseEventProcessor(
    val blockQueries: BlockQueries
) : EventProcessor

class L2TestEventProcessor(
    blockQueries: BlockQueries
) : BaseEventProcessor(blockQueries) {

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
    url: String,
    contractAddress: String,
    blockQueries: BlockQueries
) : BaseEventProcessor(blockQueries) {

    private val events: Queue<ChrL2.DepositedEventResponse> = LinkedList()
    private val subscription: Disposable
    private var web3c: Web3Connector
    private var contract: ChrL2

    init {
        val web3jConfig = Web3jConfig()
        val web3j = Web3j.build(web3jConfig.buildService(url))
        web3c = Web3Connector(web3j, contractAddress)
        contract = ChrL2.load(
            web3c.contractAddress,
            web3c.web3j,
            ClientTransactionManager(web3c.web3j, "0x0"),
            DefaultGasProvider()
        )

        val nextBlockHeight = getNextUnprocessedBlockHeight()
        val from = if (nextBlockHeight != null) {
            DefaultBlockParameter.valueOf(nextBlockHeight)
        } else {
            DefaultBlockParameter.valueOf(DefaultBlockParameterName.LATEST.name)
        }
        subscription = contract
            .depositedEventFlowable(from, DefaultBlockParameter.valueOf(DefaultBlockParameterName.LATEST.name))
            .subscribe(
                ::processLogEvent
            ) {
                logger.error("Cannot read data from eth via web3j", it)
            }
    }

    companion object : KLogging()

    override fun shutdown() {
        subscription.dispose()
        web3c.shutdown()
    }

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
        val to = web3c.web3j.ethBlockNumber().send().blockNumber.minus(BigInteger.valueOf(100L))
        val lastEthBlock = web3c.web3j.ethGetBlockByNumber(DefaultBlockParameter.valueOf(to), false).send()
        val from = getNextUnprocessedBlockHeight() ?: to.minus(BigInteger.valueOf(100L))

        val out = parseEvents(from, to)
        val toBlock: Array<Gtv> = arrayOf(gtv(lastEthBlock.block.number), gtv(lastEthBlock.block.hash))
        return Pair(toBlock, out)
    }

    private fun getNextUnprocessedBlockHeight(): BigInteger? {
        val block = blockQueries.query("get_last_eth_block", gtv(mutableMapOf())).get()
        if (block == GtvNull) {
            return null
        }

        val blockHeight = block.asDict()["eth_block_height"] ?: throw ProgrammerMistake("Last eth block has no height stored")
        return blockHeight.asBigInteger().plus(BigInteger.ONE)
    }

    @Synchronized
    private fun parseEvents(from: BigInteger, to: BigInteger): List<Array<Gtv>> {
        val out = mutableListOf<Array<Gtv>>()
        var nextLogEvent = events.peek()
        while (nextLogEvent != null && nextLogEvent.log.blockNumber <= to) {
            val event = events.poll()
            if (event.log.blockNumber >= from) {
                out.add(
                    arrayOf(
                        gtv(event.log.blockNumber), gtv(event.log.blockHash), gtv(event.log.transactionHash),
                        gtv(event.log.logIndex), gtv(EventEncoder.encode(ChrL2.DEPOSITED_EVENT)),
                        gtv(contract.contractAddress), gtv(event.owner), gtv(event.token), gtv(event.value)
                    )
                )
            }
            nextLogEvent = events.peek()
        }
        return out
    }

    @Synchronized
    private fun processLogEvent(event: ChrL2.DepositedEventResponse) {
        // See if we can prune any old events every time we have filled 100 blocks
        // to avoid queue filling up on the nodes that are not BP
        if (!events.isEmpty() && events.size % 100 == 0) {
            val nextBlockHeight = getNextUnprocessedBlockHeight()

            if (nextBlockHeight != null) {
                pruneEvents(nextBlockHeight)
            }
        }

        events.add(event)
    }

    private fun pruneEvents(to: BigInteger) {
        var nextLogEvent = events.peek()
        while (nextLogEvent != null && nextLogEvent.log.blockNumber <= to) {
            events.poll()
            nextLogEvent = events.peek()
        }
    }
}

class Web3jConfig {
    fun buildService(clientAddress: String?): Web3jService {
        val web3jService: Web3jService
        if (clientAddress == null || clientAddress == "") {
            web3jService = HttpService(createOkHttpClient())
        } else if (clientAddress.startsWith("http")) {
            web3jService = HttpService(clientAddress, createOkHttpClient(), false)
        } else if (System.getProperty("os.name").toLowerCase().startsWith("win")) {
            web3jService = WindowsIpcService(clientAddress)
        } else {
            web3jService = UnixIpcService(clientAddress)
        }
        return web3jService
    }

    private fun createOkHttpClient(): OkHttpClient {
        val builder: OkHttpClient.Builder = OkHttpClient.Builder()
        configureTimeouts(builder)
        return builder.build()
    }

    private fun configureTimeouts(builder: OkHttpClient.Builder) {
        val tos = 300L
        builder.connectTimeout(tos, TimeUnit.SECONDS)
        builder.readTimeout(tos, TimeUnit.SECONDS) // Sets the socket timeout too
        builder.writeTimeout(tos, TimeUnit.SECONDS)
    }
}