package net.postchain.base.importexport

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.messageContains
import assertk.isContentEqualTo
import net.postchain.StorageBuilder
import net.postchain.base.BaseBlockHeader
import net.postchain.base.BaseBlockWitness
import net.postchain.base.TestBlockChainBuilder
import net.postchain.base.data.BaseBlockWitnessProvider
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.data.SQLDatabaseAccess
import net.postchain.base.data.testDbConfig
import net.postchain.base.gtv.GtvToBlockchainRidFactory
import net.postchain.base.importexport.ImporterExporter.exportBlocks
import net.postchain.base.importexport.ImporterExporter.importBlocks
import net.postchain.base.withReadConnection
import net.postchain.common.exception.TransactionIncorrect
import net.postchain.common.exception.UserMistake
import net.postchain.common.wrap
import net.postchain.config.app.AppConfig
import net.postchain.configurations.GTXTestModule
import net.postchain.configurations.GTX_TEST_OP_NAME
import net.postchain.configurations.table_gtx_test_value
import net.postchain.core.Storage
import net.postchain.core.Transaction
import net.postchain.core.TxDetail
import net.postchain.core.block.BlockHeader
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.crypto.devtools.KeyPairHelper
import net.postchain.crypto.sha256Digest
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvEncoder.encodeGtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtx.GTXTransaction
import net.postchain.gtx.GTXTransactionFactory
import net.postchain.gtx.GtxBuilder
import org.apache.commons.dbutils.handlers.ColumnListHandler
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.FileInputStream
import java.nio.file.Files
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
    fun exportConfigurationsOnly(@TempDir tempDir: Path) {
        val configurationsFile = tempDir.resolve("configurations.gtv")

        StorageBuilder.buildStorage(appConfig, wipeDatabase = true).use { storage ->
            val testBlockChainBuilder = TestBlockChainBuilder(storage, configData0)
            testBlockChainBuilder.buildBlockchainWithTestTransactions(listOf(0L to configData0, 2L to configData2), listOf(
                    listOf("first"),
                    listOf(),
                    listOf("second", "third")
            ))
            val exportResult = ImporterExporter.exportBlockchain(storage, chainId, configurationsFile, null,
                    overwrite = false, logNBlocks = 1)
            assertThat(exportResult).isEqualTo(ExportResult(fromHeight = 0, toHeight = Long.MAX_VALUE, numBlocks = 0))
        }

        FileInputStream(configurationsFile.toFile()).use {
            assertThat(GtvDecoder.decodeGtv(it).asByteArray()).isContentEqualTo(blockchainRid.data)
            assertExportedConfiguration(0, configData0, GtvDecoder.decodeGtv(it))
            assertExportedConfiguration(2, configData2, GtvDecoder.decodeGtv(it))
            assertThat(GtvDecoder.decodeGtv(it).isNull())
            assertThat(it.read()).isEqualTo(-1) // EOF
        }
    }

    @Test
    fun exportAll(@TempDir tempDir: Path) {
        val configurationsFile = tempDir.resolve("configurations.gtv")
        val blocksFile = tempDir.resolve("blocks.gtv")

        val blocks = StorageBuilder.buildStorage(appConfig, wipeDatabase = true).use { storage ->
            val testBlockChainBuilder = TestBlockChainBuilder(storage, configData0)
            val blocks = testBlockChainBuilder.buildBlockchainWithTestTransactions(listOf(0L to configData0, 2L to configData2), listOf(
                    listOf("first"),
                    listOf(),
                    listOf("second", "third")
            ))

            val exportResult = ImporterExporter.exportBlockchain(storage, chainId, configurationsFile, blocksFile,
                    overwrite = false, logNBlocks = 1)
            assertThat(exportResult).isEqualTo(ExportResult(fromHeight = 0, toHeight = 2, numBlocks = 3))
            blocks
        }

        FileInputStream(configurationsFile.toFile()).use {
            assertThat(GtvDecoder.decodeGtv(it).asByteArray()).isContentEqualTo(blockchainRid.data)
            assertExportedConfiguration(0, configData0, GtvDecoder.decodeGtv(it))
            assertExportedConfiguration(2, configData2, GtvDecoder.decodeGtv(it))
            assertThat(GtvDecoder.decodeGtv(it).isNull())
            assertThat(it.read()).isEqualTo(-1) // EOF
        }

        FileInputStream(blocksFile.toFile()).use {
            assertExportedBlock(blocks[0], GtvDecoder.decodeGtv(it))
            assertExportedBlock(blocks[1], GtvDecoder.decodeGtv(it))
            assertExportedBlock(blocks[2], GtvDecoder.decodeGtv(it))
            assertThat(GtvDecoder.decodeGtv(it).isNull())
            assertThat(it.read()).isEqualTo(-1) // EOF
        }
    }

    @Test
    fun exportFromHeight(@TempDir tempDir: Path) {
        val configurationsFile = tempDir.resolve("configurations.gtv")
        val blocksFile = tempDir.resolve("blocks.gtv")

        val blocks = StorageBuilder.buildStorage(appConfig, wipeDatabase = true).use { storage ->
            val testBlockChainBuilder = TestBlockChainBuilder(storage, configData0)
            val blocks = testBlockChainBuilder.buildBlockchainWithTestTransactions(listOf(0L to configData0, 2L to configData2), listOf(
                    listOf("first"),
                    listOf(),
                    listOf("second", "third")
            ))
            val exportResult = ImporterExporter.exportBlockchain(storage, chainId, configurationsFile, blocksFile,
                    overwrite = false, fromHeight = 1, logNBlocks = 1)
            assertThat(exportResult).isEqualTo(ExportResult(fromHeight = 1, toHeight = 2, numBlocks = 2))
            blocks
        }

        FileInputStream(configurationsFile.toFile()).use {
            assertThat(GtvDecoder.decodeGtv(it).asByteArray()).isContentEqualTo(blockchainRid.data)
            assertExportedConfiguration(2, configData2, GtvDecoder.decodeGtv(it))
            assertThat(GtvDecoder.decodeGtv(it).isNull())
            assertThat(it.read()).isEqualTo(-1) // EOF
        }

        FileInputStream(blocksFile.toFile()).use {
            assertExportedBlock(blocks[1], GtvDecoder.decodeGtv(it))
            assertExportedBlock(blocks[2], GtvDecoder.decodeGtv(it))
            assertThat(GtvDecoder.decodeGtv(it).isNull())
            assertThat(it.read()).isEqualTo(-1) // EOF
        }
    }

    @Test
    fun exportToHeight(@TempDir tempDir: Path) {
        val configurationsFile = tempDir.resolve("configurations.gtv")
        val blocksFile = tempDir.resolve("blocks.gtv")

        val blocks = StorageBuilder.buildStorage(appConfig, wipeDatabase = true).use { storage ->
            val testBlockChainBuilder = TestBlockChainBuilder(storage, configData0)
            val blocks = testBlockChainBuilder.buildBlockchainWithTestTransactions(listOf(0L to configData0, 2L to configData2), listOf(
                    listOf("first"),
                    listOf(),
                    listOf("second", "third")
            ))
            val exportResult = ImporterExporter.exportBlockchain(storage, chainId, configurationsFile, blocksFile,
                    overwrite = false, upToHeight = 1, logNBlocks = 1)
            assertThat(exportResult).isEqualTo(ExportResult(fromHeight = 0, toHeight = 1, numBlocks = 2))
            blocks
        }

        FileInputStream(configurationsFile.toFile()).use {
            assertThat(GtvDecoder.decodeGtv(it).asByteArray()).isContentEqualTo(blockchainRid.data)
            assertExportedConfiguration(0, configData0, GtvDecoder.decodeGtv(it))
            assertThat(GtvDecoder.decodeGtv(it).isNull())
            assertThat(it.read()).isEqualTo(-1) // EOF
        }

        FileInputStream(blocksFile.toFile()).use {
            assertExportedBlock(blocks[0], GtvDecoder.decodeGtv(it))
            assertExportedBlock(blocks[1], GtvDecoder.decodeGtv(it))
            assertThat(GtvDecoder.decodeGtv(it).isNull())
            assertThat(it.read()).isEqualTo(-1) // EOF
        }
    }

    @Test
    fun exportNoOverwrite(@TempDir tempDir: Path) {
        val configurationsFile = tempDir.resolve("configurations.gtv")
        val blocksFile = tempDir.resolve("blocks.gtv")

        Files.createFile(configurationsFile)
        Files.createFile(blocksFile)

        StorageBuilder.buildStorage(appConfig, wipeDatabase = true).use { storage ->
            val testBlockChainBuilder = TestBlockChainBuilder(storage, configData0)
            testBlockChainBuilder.buildBlockchainWithTestTransactions(listOf(0L to configData0, 2L to configData2), listOf(
                    listOf("first"),
                    listOf(),
                    listOf("second", "third")
            ))
            assertFailure {
                ImporterExporter.exportBlockchain(storage, chainId, configurationsFile, blocksFile,
                        overwrite = false, logNBlocks = 1)
            }.isInstanceOf(UserMistake::class).messageContains("overwrite")
        }
    }

    @Test
    fun exportOverwrite(@TempDir tempDir: Path) {
        val configurationsFile = tempDir.resolve("configurations.gtv")
        val blocksFile = tempDir.resolve("blocks.gtv")

        Files.createFile(configurationsFile)
        Files.createFile(blocksFile)

        StorageBuilder.buildStorage(appConfig, wipeDatabase = true).use { storage ->
            val testBlockChainBuilder = TestBlockChainBuilder(storage, configData0)
            testBlockChainBuilder.buildBlockchainWithTestTransactions(listOf(0L to configData0, 2L to configData2), listOf(
                    listOf("first"),
                    listOf(),
                    listOf("second", "third")
            ))
            ImporterExporter.exportBlockchain(storage, chainId, configurationsFile, blocksFile,
                    overwrite = true, logNBlocks = 1)
        }
    }

    private fun assertExportedConfiguration(height: Long, configData: Gtv, gtv: Gtv) {
        assertThat(gtv.asArray()[0].asInteger()).isEqualTo(height)
        assertThat(gtv.asArray()[1].asByteArray()).isContentEqualTo(encodeGtv(configData))
    }

    private fun assertExportedBlock(expectedBlock: Pair<BaseBlockHeader, List<Transaction>>, block: Gtv) {
        val (expectedBlockHeader, expectedTransactions) = expectedBlock
        val (blockHeader, blockWitness, transactions) = ImporterExporter.decodeBlockEntry(block)
        assertThat(blockHeader.blockRID).isContentEqualTo(expectedBlockHeader.blockRID)
        assertThat(blockHeader.blockHeaderRec).isEqualTo(expectedBlockHeader.blockHeaderRec)
        val blockWitnessProvider = BaseBlockWitnessProvider(cryptoSystem, cryptoSystem.buildSigMaker(KeyPairHelper.keyPair(0)),
                (0..3).map { KeyPairHelper.pubKey(it) }.toTypedArray())
        blockWitnessProvider.validateWitness(blockWitness, blockWitnessProvider.createWitnessBuilderWithoutOwnSignature(blockHeader))

        assertThat(transactions.size).isEqualTo(expectedTransactions.size)
        for ((expectedTx, tx) in expectedTransactions.zip(transactions)) {
            assertThat(tx).isContentEqualTo(expectedTx.getRawData())
        }
    }

    @Test
    fun importSuccess(@TempDir tempDir: Path) {
        val configurationsFile = tempDir.resolve("configurations.gtv")
        val blocksFile = tempDir.resolve("blocks.gtv")

        val expectedConfigurations = listOf(0L to configData0, 2L to configData2)
        val expectedBlocks = StorageBuilder.buildStorage(appConfig, wipeDatabase = true).use { storage ->
            val testBlockChainBuilder = TestBlockChainBuilder(storage, configData0)
            val expectedBlocks = testBlockChainBuilder.buildBlockchainWithTestTransactions(listOf(0L to configData0, 2L to configData2), listOf(
                    listOf("first"),
                    listOf(),
                    listOf("second", "third")
            ))
            ImporterExporter.exportBlockchain(storage, chainId, configurationsFile, blocksFile, overwrite = false, logNBlocks = 1)
            expectedBlocks
        }

        StorageBuilder.buildStorage(appConfig, wipeDatabase = true).use { storage ->
            val importResult = ImporterExporter.importBlockchain(
                    KeyPairHelper.keyPair(0),
                    cryptoSystem,
                    storage,
                    chainId,
                    configurationsFile,
                    blocksFile,
                    logNBlocks = 1)
            assertThat(importResult).isEqualTo(
                    ImportResult(fromHeight = 0, toHeight = 2, lastSkippedBlock = -1, firstImportedBlock = 0, numBlocks = 3, blockchainRid = blockchainRid)
            )

            withReadConnection(storage, chainId) { ctx ->
                val db = DatabaseAccess.of(ctx) as SQLDatabaseAccess
                assertThat(db.getBlockchainRid(ctx)).isEqualTo(blockchainRid)

                val configurations = db.getAllConfigurations(ctx)
                assertThat(configurations).isEqualTo(expectedConfigurations.map { it.first to encodeGtv(it.second).wrap() })

                val blocks = db.getBlocks(ctx, Long.MAX_VALUE, 1000).sortedBy { it.blockHeight }
                assertThat(blocks.size).isEqualTo(expectedBlocks.size)
                for ((block, expectedBlock) in blocks.zip(expectedBlocks)) {
                    val (expectedBlockHeader, expectedTransactions) = expectedBlock
                    assertImportedBlock(block, expectedBlockHeader)

                    val transactions: List<TxDetail> = db.getBlockTransactions(ctx, block.blockRid, hashesOnly = false)
                    assertThat(transactions.map { it.data!!.wrap() }).isEqualTo(expectedTransactions.map { it.getRawData().wrap() })
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

                assertThat(operations).isEqualTo(expectedOperations)
            }
        }
    }

    @Test
    fun importIncremental(@TempDir tempDir: Path) {
        val (configsFile1, blocksFile1) = tempDir.resolve("configurations1.gtv") to tempDir.resolve("blocks1.gtv")
        val (configsFile2, blocksFile2) = tempDir.resolve("configurations2.gtv") to tempDir.resolve("blocks2.gtv")
        val (configsFile3, blocksFile3) = tempDir.resolve("configurations3.gtv") to tempDir.resolve("blocks3.gtv")

        val expectedConfigurations = listOf(0L to configData0, 2L to configData2)
        val expectedBlocks = StorageBuilder.buildStorage(appConfig, wipeDatabase = true).use { storage ->
            val testBlockChainBuilder = TestBlockChainBuilder(storage, configData0)
            val expectedBlocks = testBlockChainBuilder.buildBlockchainWithTestTransactions(listOf(0L to configData0, 2L to configData2), listOf(
                    listOf("first"),
                    listOf(),
                    listOf("second", "third"),
                    listOf("fourth")
            ))
            ImporterExporter.exportBlockchain(storage, chainId, configsFile1, blocksFile1, overwrite = false, upToHeight = 1, logNBlocks = 1)
            ImporterExporter.exportBlockchain(storage, chainId, configsFile2, blocksFile2, overwrite = false, upToHeight = 2, logNBlocks = 1)
            ImporterExporter.exportBlockchain(storage, chainId, configsFile3, blocksFile3, overwrite = false, fromHeight = 3, logNBlocks = 1)
            expectedBlocks
        }

        StorageBuilder.buildStorage(appConfig, wipeDatabase = true).use { storage ->
            // Import blocks [0, 1]
            val importResult1 = ImporterExporter.importBlockchain(
                    KeyPairHelper.keyPair(0),
                    cryptoSystem,
                    storage,
                    chainId,
                    configsFile1,
                    blocksFile1,
                    incremental = false,
                    logNBlocks = 1)
            assertThat(importResult1).isEqualTo(
                    ImportResult(fromHeight = 0, toHeight = 1, lastSkippedBlock = -1, firstImportedBlock = 0, numBlocks = 2, blockchainRid = blockchainRid)
            )

            // Import blocks [0, 1, 2] -> [0, 1] will be skipped
            val importResult2 = ImporterExporter.importBlockchain(
                    KeyPairHelper.keyPair(0),
                    cryptoSystem,
                    storage,
                    chainId,
                    configsFile2,
                    blocksFile2,
                    incremental = true,
                    logNBlocks = 1)
            assertThat(importResult2).isEqualTo(
                    ImportResult(fromHeight = 0, toHeight = 2, lastSkippedBlock = 1, firstImportedBlock = 2, numBlocks = 3, blockchainRid = blockchainRid)
            )

            // Import blocks [0, 1, 2] again -> [0, 1, 2] will be skipped
            val importResult3 = ImporterExporter.importBlockchain(
                    KeyPairHelper.keyPair(0),
                    cryptoSystem,
                    storage,
                    chainId,
                    configsFile2,
                    blocksFile2,
                    incremental = true,
                    logNBlocks = 1)
            assertThat(importResult3).isEqualTo(
                    ImportResult(fromHeight = 0, toHeight = 2, lastSkippedBlock = 2, firstImportedBlock = -1, numBlocks = 3, blockchainRid = blockchainRid)
            )

            // Import blocks [3]
            val importResult4 = ImporterExporter.importBlockchain(
                    KeyPairHelper.keyPair(0),
                    cryptoSystem,
                    storage,
                    chainId,
                    configsFile3,
                    blocksFile3,
                    incremental = true,
                    logNBlocks = 1)
            assertThat(importResult4).isEqualTo(
                    ImportResult(fromHeight = 3, toHeight = 3, lastSkippedBlock = -1, firstImportedBlock = 3, numBlocks = 1, blockchainRid = blockchainRid)
            )

            assertImportedTxs(storage, expectedConfigurations, expectedBlocks)
        }
    }

    @Test
    fun exportBlocksNoLimit() {

        StorageBuilder.buildStorage(appConfig, wipeDatabase = true).use { storage ->
            buildSimpleBlockchain(storage, 10)
            val exportBlocks = exportBlocks(storage, chainId, 0, Int.MAX_VALUE, Int.MAX_VALUE);
            assertThat(exportBlocks.size).isEqualTo(10)
        }
    }

    @Test
    fun exportBlocksCountLimit1() {

        StorageBuilder.buildStorage(appConfig, wipeDatabase = true).use { storage ->
            buildSimpleBlockchain(storage, 10)
            val blockExports = List(11) { exportBlocks(storage, chainId, it.toLong(), 1, Int.MAX_VALUE) }

            assertBlockExportRangeSize(blockExports, 0..9, 1)
            assertBlockExportRangeSize(blockExports, 10, 0)
        }
    }

    @Test
    fun exportBlocksCountLimit3() {

        StorageBuilder.buildStorage(appConfig, wipeDatabase = true).use { storage ->
            buildSimpleBlockchain(storage, 10)
            val blockExports = List(5) { exportBlocks(storage, chainId, (it * 3).toLong(), 3, Int.MAX_VALUE) }

            assertThat(blockExports.size).isEqualTo(5)
            assertBlockExportRangeSize(blockExports, 0..2, 3)
            assertBlockExportRangeSize(blockExports, 3, 1)
            assertBlockExportRangeSize(blockExports, 4, 0)
        }
    }

    @Test
    fun exportBlocksSizeLimit1k() {

        StorageBuilder.buildStorage(appConfig, wipeDatabase = true).use { storage ->
            buildSimpleBlockchain(storage, 10)
            val blockExports = List(11) { exportBlocks(storage, chainId, it.toLong(), Int.MAX_VALUE, 1000) }

            assertBlockExportRangeSize(blockExports, 0..9, 1)
            assertBlockExportRangeSize(blockExports, 10, 0)
        }
    }

    @Test
    fun exportBlocksSizeLimit3k() {

        StorageBuilder.buildStorage(appConfig, wipeDatabase = true).use { storage ->
            buildSimpleBlockchain(storage, 10)
            val blockExports = List(4) { exportBlocks(storage, chainId, (it * 4).toLong(), Int.MAX_VALUE, 3000) }

            assertThat(blockExports.size).isEqualTo(4)
            assertBlockExportRangeSize(blockExports, 0..1, 4)
            assertBlockExportRangeSize(blockExports, 2, 2)
            assertBlockExportRangeSize(blockExports, 3, 0)
        }
    }

    @Test
    fun exportBlocksAndImportBlocks() {

        val blockExports = StorageBuilder.buildStorage(appConfig, wipeDatabase = true).use { storage ->
            buildSimpleBlockchain(storage, 10)
            val blockExports = List(4) { exportBlocks(storage, chainId, (it * 4).toLong(), Int.MAX_VALUE, 3000) }

            assertThat(blockExports.size).isEqualTo(4)

            blockExports
        }

        StorageBuilder.buildStorage(appConfig, wipeDatabase = true).use { storage ->
            buildSimpleBlockchain(storage, 0)
            blockExports.forEach {
                importBlocks(storage, chainId, it, KeyPairHelper.keyPair(0), cryptoSystem)
            }
        }
    }

    private fun assertImportedTxs(storage: Storage, expectedConfigurations: List<Pair<Long, Gtv>>, expectedBlocks: List<Pair<BaseBlockHeader, List<Transaction>>>) {
        withReadConnection(storage, chainId) { ctx ->
            val db = DatabaseAccess.of(ctx) as SQLDatabaseAccess
            assertThat(db.getBlockchainRid(ctx)).isEqualTo(blockchainRid)

            val configurations = db.getAllConfigurations(ctx)
            assertThat(configurations).isEqualTo(expectedConfigurations.map { it.first to encodeGtv(it.second).wrap() })

            val blocks = db.getBlocks(ctx, Long.MAX_VALUE, 1000).sortedBy { it.blockHeight }
            assertThat(blocks.size).isEqualTo(expectedBlocks.size)
            for ((block, expectedBlock) in blocks.zip(expectedBlocks)) {
                val (expectedBlockHeader, expectedTransactions) = expectedBlock
                assertImportedBlock(block, expectedBlockHeader)

                val transactions: List<TxDetail> = db.getBlockTransactions(ctx, block.blockRid, hashesOnly = false)
                assertThat(transactions.map { it.data!!.wrap() }).isEqualTo(expectedTransactions.map { it.getRawData().wrap() })
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

            assertThat(operations).isEqualTo(expectedOperations)
        }
    }

    private fun assertImportedBlock(blockInfoExt: DatabaseAccess.BlockInfoExt, expectedBlockHeader: BlockHeader) {
        val blockHeader = BaseBlockHeader(blockInfoExt.blockHeader, hashCalculator)
        assertThat(blockHeader.blockRID).isContentEqualTo(expectedBlockHeader.blockRID)
        assertThat(blockInfoExt.blockRid).isContentEqualTo(expectedBlockHeader.blockRID)
        assertThat(blockInfoExt.blockHeight).isEqualTo(blockHeader.blockHeaderRec.getHeight())
        assertThat(blockInfoExt.timestamp).isEqualTo(blockHeader.blockHeaderRec.getTimestamp())

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
            val blockChainBuilder = TestBlockChainBuilder(storage, configData0)
            val expectedBlocks = blockChainBuilder.buildBlockchainWithTransactions(listOf(0L to configData0),
                    listOf(listOf(GTXTransactionFactory(blockchainRid, GTXTestModule(), cryptoSystem)
                            .build(GtxBuilder(blockchainRid, listOf(), cryptoSystem)
                                    .addOperation(GTX_TEST_OP_NAME, gtv("bogus"))
                                    .finish().buildGtx()))))
            ImporterExporter.exportBlockchain(storage, chainId, configurationsFile, blocksFile, overwrite = false, logNBlocks = 1)
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
            val blockChainBuilder = TestBlockChainBuilder(storage, configData0)
            val expectedBlocks = blockChainBuilder.buildBlockchainWithTransactions(listOf(0L to configData0),
                    listOf(listOf(GTXTransactionFactory(blockchainRid, GTXTestModule(), cryptoSystem)
                            .build(GtxBuilder(blockchainRid, listOf(), cryptoSystem)
                                    .addOperation(GTX_TEST_OP_NAME, gtv(1), gtv("rejectMe"))
                                    .finish().buildGtx()))))
            ImporterExporter.exportBlockchain(storage, chainId, configurationsFile, blocksFile, overwrite = false, logNBlocks = 1)
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
            val blockChainBuilder = TestBlockChainBuilder(storage, configData0)
            val expectedBlocks = blockChainBuilder.buildBlockchainWithTransactions(listOf(0L to configData0),
                    listOf(listOf(GTXTransactionFactory(blockchainRid, GTXTestModule(), cryptoSystem)
                            .build(GtxBuilder(blockchainRid, listOf(), cryptoSystem)
                                    .addOperation(GTX_TEST_OP_NAME, gtv(1), gtv("valid"))
                                    .finish().buildGtx()))),
                    (0..1).map { KeyPairHelper.keyPair(it) })
            ImporterExporter.exportBlockchain(storage, chainId, configurationsFile, blocksFile, overwrite = false, logNBlocks = 1)
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

    private fun buildSimpleBlockchain(storage: Storage, blocks: Int) {
        TestBlockChainBuilder(storage,configData0)
                .buildBlockchainWithTestTransactions(
                        listOf(0L to configData0, 2L to configData2),
                        List(blocks) { listOf("transaction-$it") }
        )
    }

    private fun assertBlockExportRangeSize(blockExports: List<List<Gtv>>, range: IntRange, size: Int) {

        range.forEach { assertBlockExportRangeSize(blockExports, it, size) }
    }

    private fun assertBlockExportRangeSize(blockExports: List<List<Gtv>>, index: Int, size: Int) {

        assertThat(blockExports[index].size).isEqualTo(size)
    }
}
