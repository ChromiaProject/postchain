package net.postchain.base

import net.postchain.base.data.BaseBlockWitnessProvider
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.extension.CONFIG_HASH_EXTRA_HEADER
import net.postchain.base.gtv.GtvToBlockchainRidFactory
import net.postchain.common.BlockchainRid
import net.postchain.configurations.GTXTestModule
import net.postchain.configurations.GTX_TEST_OP_NAME
import net.postchain.core.EContext
import net.postchain.core.Storage
import net.postchain.core.Transaction
import net.postchain.core.TxEContext
import net.postchain.core.block.InitialBlockData
import net.postchain.crypto.KeyPair
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.crypto.devtools.KeyPairHelper
import net.postchain.crypto.sha256Digest
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash
import net.postchain.gtx.GTXTransaction
import net.postchain.gtx.GTXTransactionFactory
import net.postchain.gtx.GtxBuilder


class TestBlockChainBuilder(
        val storage: Storage,
        configData0: Gtv
) {
    private val blockchainRid = GtvToBlockchainRidFactory.calculateBlockchainRid(configData0, ::sha256Digest)
    val cryptoSystem = Secp256K1CryptoSystem()
    val hashCalculator = GtvMerkleHashCalculator(cryptoSystem)
    val chainId = 1L

    // Build blocks with given list of test transactions (only name needed as input)
    fun buildBlockchainWithEmptyBlocks(configurations: List<Pair<Long, Gtv>>, blocks: Long,
                                            witnesses: List<KeyPair> = (0..3).map { KeyPairHelper.keyPair(it) })
            : List<Pair<BaseBlockHeader, List<Transaction>>> {

        val txs = (0 until blocks).map { listOf(buildTransaction("$it")) }

        return buildBlockchainWithTransactions(configurations, txs, witnesses)
    }

    // Build blocks with given list of test transactions (only name needed as input)
    fun buildBlockchainWithTestTransactions(configurations: List<Pair<Long, Gtv>>, blockTransactions: List<List<String>>,
                                            witnesses: List<KeyPair> = (0..3).map { KeyPairHelper.keyPair(it) })
            : List<Pair<BaseBlockHeader, List<Transaction>>> {

        val txs = blockTransactions.map { blockTransactionList -> blockTransactionList.map { buildTransaction(it) } }

        return buildBlockchainWithTransactions(configurations, txs, witnesses)
    }

    // Build blocks with given list of transactions
    fun buildBlockchainWithTransactions(configurations: List<Pair<Long, Gtv>>, blockTransactions: List<List<GTXTransaction>>,
                                        witnesses: List<KeyPair> = (0..3).map { KeyPairHelper.keyPair(it) })
            : List<Pair<BaseBlockHeader, List<Transaction>>> =
            withReadWriteConnection(storage, chainId) { ctx ->

                val db = DatabaseAccess.of(ctx)

                db.initializeBlockchain(ctx, blockchainRid)

                for ((height, configData) in configurations) {
                    db.addConfigurationData(ctx, height, GtvEncoder.encodeGtv(configData))
                }

                var prevBlockRID = blockchainRid.data
                var height = 0L
                buildList {
                    for (transactions in blockTransactions) {
                        val configData = configurations.filter { it.first <= height }.maxByOrNull { it.first }!!.second
                        val block = addBlock(ctx, db, blockchainRid, height, prevBlockRID, configData, transactions, witnesses)
                        prevBlockRID = block.first.blockRID
                        height++
                        add(block)
                    }
                }
            }

    fun buildTransaction(param: String): GTXTransaction =
            GTXTransactionFactory(blockchainRid, GTXTestModule(), cryptoSystem)
                    .build(GtxBuilder(blockchainRid, listOf(), cryptoSystem)
                            .addOperation(GTX_TEST_OP_NAME, GtvFactory.gtv(1), GtvFactory.gtv(param))
                            .finish().buildGtx())

    private fun addBlock(ctx: EContext, db: DatabaseAccess, blockchainRid: BlockchainRid, blockHeight: Long,
                         prevBlockRID: ByteArray, configData: Gtv, transactions: List<Transaction>,
                         witnesses: List<KeyPair>): Pair<BaseBlockHeader, List<Transaction>> {
        val blockIID = db.insertBlock(ctx, blockHeight)
        val rootHash = GtvFactory.gtv(transactions.map { GtvFactory.gtv(it.getHash()) }).merkleHash(hashCalculator)
        val timestamp = 10000L + blockHeight
        var nextTransactionNumber = db.getLastTransactionNumber(ctx) + 1
        val blockData =
                InitialBlockData(blockchainRid, blockIID, ctx.chainID, prevBlockRID, blockHeight, timestamp, null)
        val blockHeader = BaseBlockHeader.make(hashCalculator, blockData, rootHash, timestamp,
                mapOf(CONFIG_HASH_EXTRA_HEADER to GtvFactory.gtv(GtvToBlockchainRidFactory.calculateBlockchainRid(configData, ::sha256Digest).data)))
        val blockEContext = BaseBlockEContext(
                ctx,
                height = 0,
                blockIID,
                timestamp,
                mapOf(),
                object : TxEventSink {
                    override fun processEmittedEvent(ctxt: TxEContext, type: String, data: Gtv) {}
                }
        )
        for (tx in transactions) {
            db.insertTransaction(blockEContext, tx, nextTransactionNumber++)
        }
        db.finalizeBlock(blockEContext, blockHeader)
        val witnessBuilder = BaseBlockWitnessProvider(cryptoSystem, cryptoSystem.buildSigMaker(KeyPairHelper.keyPair(0)),
                witnesses.map { it.pubKey.data }.toTypedArray<ByteArray>()).createWitnessBuilderWithoutOwnSignature(blockHeader) as BaseBlockWitnessBuilder
        for (witness in witnesses) {
            witnessBuilder.applySignature(cryptoSystem.buildSigMaker(witness).signDigest(blockHeader.blockRID))
        }

        db.commitBlock(blockEContext, witnessBuilder.getWitness())
        return blockHeader to transactions
    }
}
