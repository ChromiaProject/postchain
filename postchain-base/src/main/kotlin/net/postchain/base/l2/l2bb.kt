// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base.l2

import net.postchain.base.*
import net.postchain.base.data.BaseBlockBuilder
import net.postchain.base.snapshot.EventPageStore
import net.postchain.base.snapshot.LeafStore
import net.postchain.base.snapshot.SnapshotPageStore
import net.postchain.common.data.Hash
import net.postchain.core.*
import net.postchain.crypto.DigestSystem
import net.postchain.ethereum.contracts.ERC20Token
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvByteArray
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtx.GTXDataBuilder
import net.postchain.gtx.GTXModule
import org.web3j.abi.EventEncoder
import org.web3j.abi.datatypes.Event
import org.web3j.protocol.core.DefaultBlockParameterName
import java.util.*
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameter
import org.web3j.protocol.core.methods.response.EthBlockNumber
import org.web3j.protocol.http.HttpService
import org.web3j.tx.ClientTransactionManager
import org.web3j.tx.gas.DefaultGasProvider
import java.math.BigInteger


interface L2EventProcessor {
    fun emitL2Event(evt: Gtv)
    fun emitL2State(state_n: Long, state: Gtv)
}

interface L2Implementation: L2EventProcessor {
    fun init(blockEContext: BlockEContext)
    fun finalize(): Map<String, Gtv>
}

open class L2TxEContext(
    private val ectx: TxEContext, private val bb: L2BlockBuilder
) : TxEContext by ectx, L2EventProcessor {

    val events = mutableListOf<Gtv>()
    val states = mutableListOf<Pair<Long, Gtv>>()

    override fun emitL2Event(evt: Gtv) {
        events.add(evt)
    }

    override fun emitL2State(state_n: Long, state: Gtv) {
        states.add(Pair(state_n, state))
    }

    override fun done() {
        for (evt in events) bb.emitL2Event(evt)
        for ((state_n, state) in states) bb.emitL2State(state_n, state)
    }

    override fun <T> getInterface(c: Class<T>): T? {
        return if (c == L2TxEContext::class.java) {
            this as T?
        } else
            ectx.getInterface(c)
    }

    companion object {
        fun emitL2Event(ectx: BlockEContext, evt: Gtv) {
            val l2Ctx = ectx.queryInterface<L2TxEContext>()
            if (l2Ctx == null) {
                throw ProgrammerMistake("Blockchain configuration does not support L2")
            } else {
                l2Ctx.emitL2Event(evt)
            }
        }

        fun emitL2AccountState(ectx: BlockEContext, state_n: Long, state: Gtv) {
            val l2Ctx = ectx.queryInterface<L2TxEContext>()
            if (l2Ctx == null) {
                throw ProgrammerMistake("Blockchain configuration does not support L2")
            } else {
                l2Ctx.emitL2State(state_n, state)
            }
        }
    }
}

class L2BlockBuilder(blockchainRID: BlockchainRid,
                     private val module: GTXModule,
                     cryptoSystem: CryptoSystem,
                     eContext: EContext,
                     store: BlockStore,
                     txFactory: TransactionFactory,
                     specialTxHandler: SpecialTransactionHandler,
                     subjects: Array<ByteArray>,
                     blockSigMaker: SigMaker,
                     blockchainRelatedInfoDependencyList: List<BlockchainRelatedInfo>,
                     usingHistoricBRID: Boolean,
                     private val l2Implementation: L2Implementation,
                     private val layer2: Gtv?,
                     maxBlockSize: Long = 20 * 1024 * 1024, // 20mb
                     maxBlockTransactions: Long = 100
): BaseBlockBuilder(blockchainRID, cryptoSystem, eContext, store, txFactory, specialTxHandler, subjects, blockSigMaker,
        blockchainRelatedInfoDependencyList, usingHistoricBRID,
        maxBlockSize, maxBlockTransactions), L2EventProcessor by l2Implementation {

    override fun begin(partialBlockHeader: BlockHeader?) {
        super.begin(partialBlockHeader)
        l2Implementation.init(bctx)

        val url = layer2?.get("eth_rpc_api_node_url")?.asString() ?: "http://localhost:8545"
        val contractAddress = layer2?.get("contract_address")?.asString() ?: "0x0"
        val web3j = Web3j.build(HttpService(url))
        val ethBlockNumber: EthBlockNumber = web3j.ethBlockNumber().send()
        val startBlock = ethBlockNumber.blockNumber.minus(BigInteger.valueOf(200L))
        val contract = ERC20Token.load(
            contractAddress,
            web3j,
            ClientTransactionManager(web3j, "0x0"),
            DefaultGasProvider())
        contract.transferEventFlowable(
            DefaultBlockParameter.valueOf(startBlock), DefaultBlockParameterName.LATEST)
            .subscribe {
                if (module.getOperations().contains("__l2_transfer")) {
                    val b = GTXDataBuilder(blockchainRID, arrayOf(), cryptoSystem)
                    b.addOperation("nop", arrayOf(gtv(System.currentTimeMillis())))
                    b.addOperation("__l2_transfer", arrayOf(
                        gtv(it.log.blockNumber), gtv(it.log.blockHash), gtv(it.log.transactionHash),
                        gtv(it.log.logIndex), gtv(EventEncoder.encode(ERC20Token.TRANSFER_EVENT)),
                        gtv(contract.contractAddress), gtv(it.from), gtv(it.to), gtv(it.value)))
                    val tx = txFactory.decodeTransaction(b.serialize())
                    appendTransaction(tx)
                }
            }
    }

    override fun getExtraData(): Map<String, Gtv> {
        return l2Implementation.finalize()
    }
}

class EthereumL2Implementation(
    private val ds: DigestSystem,
    private val levelsPerPage: Int): L2Implementation {

    private lateinit var bctx: BlockEContext
    lateinit var store: LeafStore
    lateinit var snapshot: SnapshotPageStore
    lateinit var event: EventPageStore

    val events = mutableListOf<Hash>()
    val states = TreeMap<Long, Hash>()

    override fun init(blockEContext: BlockEContext) {
        bctx = blockEContext
        store = LeafStore()
        snapshot = SnapshotPageStore(blockEContext, levelsPerPage, ds)
        event = EventPageStore(blockEContext, levelsPerPage, ds)
    }

    /**
     * Compute event (as a simple Merkle tree) and state hashes (using updateSnapshot)
     */
    override fun finalize(): Map<String, Gtv> {
        val extra = mutableMapOf<String, Gtv>()
        val stateRootHash = snapshot.updateSnapshot(bctx.height, states)
        val eventRootHash = event.writeEventTree(bctx.height, events)
        extra["l2RootState"] = GtvByteArray(stateRootHash)
        extra["l2RootEvent"] = GtvByteArray(eventRootHash)
        return extra
    }

    /**
     * Serialize, write to leaf store, hash using keccak256.
     * Hashes are remembered and later combined into a Merkle tree
     */
    override fun emitL2Event(evt: Gtv) {
        val data = GtvEncoder.simpleEncodeGtv(evt)
        val hash = ds.digest(data)
        events.add(hash)
        store.writeEvent(bctx, hash, data)
    }

    /**
     * Serialize, write to leaf store,
     * hash using keccak256. (state_n, hash) pairs are submitted to updateSnapshot
     * during finalization
     */
    override fun emitL2State(state_n: Long, state: Gtv) {
        val data = GtvEncoder.simpleEncodeGtv(state)
        val hash = ds.digest(data)
        states[state_n] = hash
        store.writeState(bctx, state_n, data)
    }
}