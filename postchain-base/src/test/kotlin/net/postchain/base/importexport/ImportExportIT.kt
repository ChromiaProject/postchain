package net.postchain.base.importexport

import assertk.assertions.isEqualTo
import assertk.isContentEqualTo
import net.postchain.StorageBuilder
import net.postchain.base.BaseBlockEContext
import net.postchain.base.BaseBlockHeader
import net.postchain.base.BaseBlockWitness
import net.postchain.base.BaseBlockWitnessBuilder
import net.postchain.base.TxEventSink
import net.postchain.base.data.BaseBlockWitnessProvider
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.data.SQLDatabaseAccess
import net.postchain.base.data.testDbConfig
import net.postchain.base.extension.CONFIG_HASH_EXTRA_HEADER
import net.postchain.base.gtv.GtvToBlockchainRidFactory
import net.postchain.base.withReadConnection
import net.postchain.base.withReadWriteConnection
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.TransactionIncorrect
import net.postchain.common.exception.UserMistake
import net.postchain.common.wrap
import net.postchain.config.app.AppConfig
import net.postchain.configurations.GTXTestModule
import net.postchain.configurations.GTX_TEST_OP_NAME
import net.postchain.configurations.table_gtx_test_value
import net.postchain.core.EContext
import net.postchain.core.Storage
import net.postchain.core.Transaction
import net.postchain.core.TxDetail
import net.postchain.core.TxEContext
import net.postchain.core.block.BlockHeader
import net.postchain.core.block.InitialBlockData
import net.postchain.crypto.KeyPair
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.crypto.devtools.KeyPairHelper
import net.postchain.crypto.sha256Digest
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvEncoder.encodeGtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash
import net.postchain.gtx.GTXTransaction
import net.postchain.gtx.GTXTransactionFactory
import net.postchain.gtx.GtxBuilder
import org.apache.commons.dbutils.handlers.ColumnListHandler
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.FileInputStream
import java.nio.file.Path

class ImportExportIT {

    private val appConfig: AppConfig = testDbConfig("import_export_it")

    private val cryptoSystem = Secp256K1CryptoSystem()
    private val hashCalculator = GtvMerkleHashCalculator(cryptoSystem)
    private val chainId = 1L
    private val configData0 = GtvMLParser.parseGtvML(javaClass.getResource("blockchain_configuration_0.xml")!!.readText())
    private val configData2 = GtvMLParser.parseGtvML(javaClass.getResource("blockchain_configuration_2.xml")!!.readText())
    private val blockchainRid = GtvToBlockchainRidFactory.calculateBlockchainRid(configData0, ::sha256Digest)

    @Test
    fun exportAll(@TempDir tempDir: Path) {
        val configurationsFile = tempDir.resolve("configurations.gtv")
        val blocksFile = tempDir.resolve("blocks.gtv")

        val blocks = StorageBuilder.buildStorage(appConfig, wipeDatabase = true).use { storage ->
            val blocks = buildBlockchain(storage, listOf(0L to configData0, 2L to configData2),
                    listOf(listOf(buildTransaction("first")), listOf(), listOf(buildTransaction("second"), buildTransaction("third"))))
            ImporterExporter.exportBlockchain(storage, chainId, configurationsFile, blocksFile, logNBlocks = 1)
            blocks
        }

        FileInputStream(configurationsFile.toFile()).use {
            assertk.assert(GtvDecoder.decodeGtv(it).asByteArray()).isContentEqualTo(blockchainRid.data)
            assertExportedConfiguration(0, configData0, GtvDecoder.decodeGtv(it))
            assertExportedConfiguration(2, configData2, GtvDecoder.decodeGtv(it))
            assertk.assert(GtvDecoder.decodeGtv(it).isNull())
            assertk.assert(it.read()).isEqualTo(-1) // EOF
        }

        FileInputStream(blocksFile.toFile()).use {
            assertExportedBlock(blocks[0], GtvDecoder.decodeGtv(it))
            assertExportedBlock(blocks[1], GtvDecoder.decodeGtv(it))
            assertExportedBlock(blocks[2], GtvDecoder.decodeGtv(it))
            assertk.assert(GtvDecoder.decodeGtv(it).isNull())
            assertk.assert(it.read()).isEqualTo(-1) // EOF
        }
    }

    @Test
    fun exportPart(@TempDir tempDir: Path) {
        val configurationsFile = tempDir.resolve("configurations.gtv")
        val blocksFile = tempDir.resolve("blocks.gtv")

        val blocks = StorageBuilder.buildStorage(appConfig, wipeDatabase = true).use { storage ->
            val blocks = buildBlockchain(storage, listOf(0L to configData0, 2L to configData2),
                    listOf(listOf(buildTransaction("first")), listOf(), listOf(buildTransaction("second"), buildTransaction("third"))))
            ImporterExporter.exportBlockchain(storage, chainId, configurationsFile, blocksFile, upToHeight = 1, logNBlocks = 1)
            blocks
        }

        FileInputStream(configurationsFile.toFile()).use {
            assertk.assert(GtvDecoder.decodeGtv(it).asByteArray()).isContentEqualTo(blockchainRid.data)
            assertExportedConfiguration(0, configData0, GtvDecoder.decodeGtv(it))
            assertk.assert(GtvDecoder.decodeGtv(it).isNull())
            assertk.assert(it.read()).isEqualTo(-1) // EOF
        }

        FileInputStream(blocksFile.toFile()).use {
            assertExportedBlock(blocks[0], GtvDecoder.decodeGtv(it))
            assertExportedBlock(blocks[1], GtvDecoder.decodeGtv(it))
            assertk.assert(GtvDecoder.decodeGtv(it).isNull())
            assertk.assert(it.read()).isEqualTo(-1) // EOF
        }
    }

    private fun buildBlockchain(storage: Storage, configurations: List<Pair<Long, Gtv>>, blockTransactions: List<List<GTXTransaction>>,
                                witnesses: List<KeyPair> = (0..3).map { KeyPairHelper.keyPair(it) })
            : List<Pair<BaseBlockHeader, List<Transaction>>> =
            withReadWriteConnection(storage, chainId) { ctx ->
                val db = DatabaseAccess.of(ctx)

                db.initializeBlockchain(ctx, blockchainRid)

                for ((height, configData) in configurations) {
                    db.addConfigurationData(ctx, height, encodeGtv(configData))
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

    private fun buildTransaction(param: String): GTXTransaction =
            GTXTransactionFactory(blockchainRid, GTXTestModule(), cryptoSystem)
                    .build(GtxBuilder(blockchainRid, listOf(), cryptoSystem)
                            .addOperation(GTX_TEST_OP_NAME, gtv(1), gtv(param))
                            .finish().buildGtx())

    private fun addBlock(ctx: EContext, db: DatabaseAccess, blockchainRid: BlockchainRid, blockHeight: Long,
                         prevBlockRID: ByteArray, configData: Gtv, transactions: List<Transaction>, witnesses: List<KeyPair>): Pair<BaseBlockHeader, List<Transaction>> {
        val blockIID = db.insertBlock(ctx, blockHeight)
        val rootHash = gtv(transactions.map { gtv(it.getHash()) }).merkleHash(hashCalculator)
        val timestamp = 10000L + blockHeight
        val blockData =
                InitialBlockData(blockchainRid, blockIID, ctx.chainID, prevBlockRID, blockHeight, timestamp, null)
        val blockHeader = BaseBlockHeader.make(hashCalculator, blockData, rootHash, timestamp,
                mapOf(CONFIG_HASH_EXTRA_HEADER to gtv(GtvToBlockchainRidFactory.calculateBlockchainRid(configData, ::sha256Digest).data)))
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
            db.insertTransaction(blockEContext, tx)
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

    private fun assertExportedConfiguration(height: Long, configData: Gtv, gtv: Gtv) {
        assertk.assert(gtv.asArray()[0].asInteger()).isEqualTo(height)
        assertk.assert(gtv.asArray()[1].asByteArray()).isContentEqualTo(encodeGtv(configData))
    }

    private fun assertExportedBlock(expectedBlock: Pair<BaseBlockHeader, List<Transaction>>, block: Gtv) {
        val (expectedBlockHeader, expectedTransactions) = expectedBlock
        val blockWithTransactions = ImporterExporter.blockWithTransactionsFromGtv(block)
        val blockHeader = BaseBlockHeader(blockWithTransactions.blockHeader, hashCalculator)
        val blockWitness = BaseBlockWitness.fromBytes(blockWithTransactions.witness)
        assertk.assert(blockHeader.blockRID).isContentEqualTo(expectedBlockHeader.blockRID)
        assertk.assert(blockHeader.blockHeaderRec).isEqualTo(expectedBlockHeader.blockHeaderRec)
        val blockWitnessProvider = BaseBlockWitnessProvider(cryptoSystem, cryptoSystem.buildSigMaker(KeyPairHelper.keyPair(0)),
                (0..3).map { KeyPairHelper.pubKey(it) }.toTypedArray())
        blockWitnessProvider.validateWitness(blockWitness, blockWitnessProvider.createWitnessBuilderWithoutOwnSignature(blockHeader))

        assertk.assert(blockWithTransactions.transactions.size).isEqualTo(expectedTransactions.size)
        for ((expectedTx, tx) in expectedTransactions.zip(blockWithTransactions.transactions)) {
            assertk.assert(tx).isContentEqualTo(expectedTx.getRawData())
        }
    }

    @Test
    fun importSuccess(@TempDir tempDir: Path) {
        val configurationsFile = tempDir.resolve("configurations.gtv")
        val blocksFile = tempDir.resolve("blocks.gtv")

        val expectedConfigurations = listOf(0L to configData0, 2L to configData2)
        val expectedBlocks = StorageBuilder.buildStorage(appConfig, wipeDatabase = true).use { storage ->
            val expectedBlocks = buildBlockchain(storage, expectedConfigurations,
                    listOf(listOf(buildTransaction("first")), listOf(), listOf(buildTransaction("second"), buildTransaction("third"))))
            ImporterExporter.exportBlockchain(storage, chainId, configurationsFile, blocksFile, logNBlocks = 1)
            expectedBlocks
        }

        StorageBuilder.buildStorage(appConfig, wipeDatabase = true).use { storage ->
            val importedBlockchainRid = ImporterExporter.importBlockchain(
                    KeyPairHelper.keyPair(0),
                    cryptoSystem,
                    storage,
                    chainId,
                    configurationsFile,
                    blocksFile,
                    logNBlocks = 1)

            assertk.assert(importedBlockchainRid).isEqualTo(blockchainRid)

            withReadConnection(storage, chainId) { ctx ->
                val db = DatabaseAccess.of(ctx) as SQLDatabaseAccess
                assertk.assert(db.getBlockchainRid(ctx)).isEqualTo(blockchainRid)

                val configurations = db.getAllConfigurations(ctx)
                assertk.assert(configurations).isEqualTo(expectedConfigurations.map { it.first to encodeGtv(it.second).wrap() })

                val blocks = db.getBlocks(ctx, Long.MAX_VALUE, 1000).sortedBy { it.blockHeight }
                assertk.assert(blocks.size).isEqualTo(expectedBlocks.size)
                for ((block, expectedBlock) in blocks.zip(expectedBlocks)) {
                    val (expectedBlockHeader, expectedTransactions) = expectedBlock
                    assertImportedBlock(block, expectedBlockHeader)

                    val transactions: List<TxDetail> = db.getBlockTransactions(ctx, block.blockRid, hashesOnly = false)
                    assertk.assert(transactions.map { it.data!!.wrap() }).isEqualTo(expectedTransactions.map { it.getRawData().wrap() })
                }

                val expectedOperations = expectedBlocks
                        .flatMap { it.second }
                        .flatMap { (it as GTXTransaction).gtxData.gtxBody.operations }
                        .filter { it.opName == GTX_TEST_OP_NAME }
                        .map { it.args[1].asString() }

                val operations = db.queryRunner.query(
                        ctx.conn,
                        "SELECT value FROM ${table_gtx_test_value(ctx)} ORDER BY tx_iid",
                        ColumnListHandler<String>())

                assertk.assert(operations).isEqualTo(expectedOperations)
            }
        }
    }

    private fun assertImportedBlock(blockInfoExt: DatabaseAccess.BlockInfoExt, expectedBlockHeader: BlockHeader) {
        val blockHeader = BaseBlockHeader(blockInfoExt.blockHeader, hashCalculator)
        assertk.assert(blockHeader.blockRID).isContentEqualTo(expectedBlockHeader.blockRID)
        assertk.assert(blockInfoExt.blockRid).isContentEqualTo(expectedBlockHeader.blockRID)
        assertk.assert(blockInfoExt.blockHeight).isEqualTo(blockHeader.blockHeaderRec.getHeight())
        assertk.assert(blockInfoExt.timestamp).isEqualTo(blockHeader.blockHeaderRec.getTimestamp())

        val blockWitness = BaseBlockWitness.fromBytes(blockInfoExt.witness)
        val blockWitnessProvider = BaseBlockWitnessProvider(cryptoSystem, cryptoSystem.buildSigMaker(KeyPairHelper.keyPair(0)),
                (0..3).map { KeyPairHelper.pubKey(it) }.toTypedArray())
        blockWitnessProvider.validateWitness(blockWitness, blockWitnessProvider.createWitnessBuilderWithoutOwnSignature(blockHeader))
    }

    @Test
    fun importIncorrectTransaction(@TempDir tempDir: Path) {
        val configurationsFile = tempDir.resolve("configurations.gtv")
        val blocksFile = tempDir.resolve("blocks.gtv")

        StorageBuilder.buildStorage(appConfig, wipeDatabase = true).use { storage ->
            val expectedBlocks = buildBlockchain(storage, listOf(0L to configData0),
                    listOf(listOf(GTXTransactionFactory(blockchainRid, GTXTestModule(), cryptoSystem)
                            .build(GtxBuilder(blockchainRid, listOf(), cryptoSystem)
                                    .addOperation(GTX_TEST_OP_NAME, gtv("bogus"))
                                    .finish().buildGtx()))))
            ImporterExporter.exportBlockchain(storage, chainId, configurationsFile, blocksFile, logNBlocks = 1)
            expectedBlocks
        }

        StorageBuilder.buildStorage(appConfig, wipeDatabase = true).use { storage ->
            assertThrows<TransactionIncorrect> {
                ImporterExporter.importBlockchain(
                        KeyPairHelper.keyPair(0),
                        cryptoSystem,
                        storage,
                        chainId,
                        configurationsFile,
                        blocksFile,
                        logNBlocks = 1)
            }
        }
    }

    @Test
    fun importRejectedTransaction(@TempDir tempDir: Path) {
        val configurationsFile = tempDir.resolve("configurations.gtv")
        val blocksFile = tempDir.resolve("blocks.gtv")

        StorageBuilder.buildStorage(appConfig, wipeDatabase = true).use { storage ->
            val expectedBlocks = buildBlockchain(storage, listOf(0L to configData0),
                    listOf(listOf(GTXTransactionFactory(blockchainRid, GTXTestModule(), cryptoSystem)
                            .build(GtxBuilder(blockchainRid, listOf(), cryptoSystem)
                                    .addOperation(GTX_TEST_OP_NAME, gtv(1), gtv("rejectMe"))
                                    .finish().buildGtx()))))
            ImporterExporter.exportBlockchain(storage, chainId, configurationsFile, blocksFile, logNBlocks = 1)
            expectedBlocks
        }

        StorageBuilder.buildStorage(appConfig, wipeDatabase = true).use { storage ->
            assertThrows<UserMistake> {
                ImporterExporter.importBlockchain(
                        KeyPairHelper.keyPair(0),
                        cryptoSystem,
                        storage,
                        chainId,
                        configurationsFile,
                        blocksFile,
                        logNBlocks = 1)
            }
        }
    }

    @Test
    fun importInsufficientWitness(@TempDir tempDir: Path) {
        val configurationsFile = tempDir.resolve("configurations.gtv")
        val blocksFile = tempDir.resolve("blocks.gtv")

        StorageBuilder.buildStorage(appConfig, wipeDatabase = true).use { storage ->
            val expectedBlocks = buildBlockchain(storage, listOf(0L to configData0),
                    listOf(listOf(GTXTransactionFactory(blockchainRid, GTXTestModule(), cryptoSystem)
                            .build(GtxBuilder(blockchainRid, listOf(), cryptoSystem)
                                    .addOperation(GTX_TEST_OP_NAME, gtv(1), gtv("valid"))
                                    .finish().buildGtx()))),
                    (0..1).map { KeyPairHelper.keyPair(it) })
            ImporterExporter.exportBlockchain(storage, chainId, configurationsFile, blocksFile, logNBlocks = 1)
            expectedBlocks
        }

        StorageBuilder.buildStorage(appConfig, wipeDatabase = true).use { storage ->
            assertThrows<UserMistake> {
                ImporterExporter.importBlockchain(
                        KeyPairHelper.keyPair(0),
                        cryptoSystem,
                        storage,
                        chainId,
                        configurationsFile,
                        blocksFile,
                        logNBlocks = 1)
            }
        }
    }
}
