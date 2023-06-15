package net.postchain.base.importexport

import mu.KLogging
import mu.withLoggingContext
import net.postchain.base.BaseBlockHeader
import net.postchain.base.BaseBlockWitness
import net.postchain.base.BaseBlockchainContext
import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadConnection
import net.postchain.base.withReadWriteConnection
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.exception.UserMistake
import net.postchain.common.reflection.newInstanceOf
import net.postchain.common.toHex
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.BlockchainConfigurationFactory
import net.postchain.core.EContext
import net.postchain.core.NODE_ID_READ_ONLY
import net.postchain.core.Storage
import net.postchain.core.Transaction
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.KeyPair
import net.postchain.crypto.SigMaker
import net.postchain.crypto.sha256Digest
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvNull
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.logging.BLOCKCHAIN_RID_TAG
import net.postchain.logging.CHAIN_IID_TAG
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.toList

object ImporterExporter : KLogging() {
    /**
     * @param storage             storage
     * @param chainId             chain to export
     * @param configurationsFile  file to export blockchain configurations to
     * @param blocksFile          file to export blocks and transactions to
     * @param overwrite           overwrite existing files
     * @param fromHeight          only export configurations and blocks from and including this height,
     *                            set to `0L` to start from first block
     * @param upToHeight          only export configurations and blocks up to and including this height,
     *                            set to `Long.MAX_VALUE` to continue to last block
     * @param logNBlocks          log every N block
     */
    fun exportBlockchain(storage: Storage, chainId: Long, configurationsFile: Path, blocksFile: Path?, overwrite: Boolean,
                         fromHeight: Long = 0L, upToHeight: Long = Long.MAX_VALUE, logNBlocks: Int = 100): ExportResult =
            withReadConnection(storage, chainId) { ctx ->
                val db = DatabaseAccess.of(ctx)

                val blockchainRid = db.getBlockchainRid(ctx)
                        ?: throw UserMistake("Blockchain $chainId not found")

                if (!overwrite) {
                    if (Files.exists(configurationsFile))
                        throw UserMistake("${configurationsFile.toAbsolutePath()} already exists and overwrite was not specified")
                    if (blocksFile != null && Files.exists(blocksFile))
                        throw UserMistake("${blocksFile.toAbsolutePath()} already exists and overwrite was not specified")
                }

                withLoggingContext(
                        CHAIN_IID_TAG to chainId.toString(),
                        BLOCKCHAIN_RID_TAG to blockchainRid.toHex()
                ) {
                    logger.info("Exporting blockchain...")

                    exportConfigurations(configurationsFile, blockchainRid, db, ctx, fromHeight = fromHeight, upToHeight = upToHeight)

                    if (blocksFile != null) {
                        val (firstBlock, lastBlock, numBlocks) = exportBlocks(blocksFile, db, ctx, fromHeight, upToHeight, logNBlocks)
                        val message = if (numBlocks > 0)
                            "Export of $numBlocks blocks $firstBlock..$lastBlock to ${configurationsFile.toAbsolutePath()} and ${blocksFile.toAbsolutePath()} completed"
                        else
                            "No blocks to export to ${configurationsFile.toAbsolutePath()} and ${blocksFile.toAbsolutePath()}"

                        logger.info(message)
                        ExportResult(fromHeight = firstBlock, toHeight = lastBlock, numBlocks = numBlocks)
                    } else {
                        logger.info("Export of configurations to ${configurationsFile.toAbsolutePath()} completed")
                        ExportResult(fromHeight = fromHeight, toHeight = upToHeight, numBlocks = 0)
                    }
                }
            }

    private fun exportConfigurations(configurationsFile: Path, blockchainRid: BlockchainRid, db: DatabaseAccess, ctx: EContext, fromHeight: Long, upToHeight: Long) {
        BufferedOutputStream(FileOutputStream(configurationsFile.toFile())).use { output ->
            output.write(GtvEncoder.encodeGtv(GtvFactory.gtv(blockchainRid.data)))

            for ((height, configurationData) in db.getAllConfigurations(ctx)) {
                if (height > upToHeight) break
                if (height >= fromHeight) {
                    output.write(GtvEncoder.encodeGtv(GtvFactory.gtv(GtvFactory.gtv(height), GtvFactory.gtv(configurationData))))
                }
            }

            output.write(GtvEncoder.encodeGtv(GtvNull))
        }
    }

    private fun exportBlocks(blocksFile: Path, db: DatabaseAccess, ctx: EContext, fromHeight: Long, upToHeight: Long, logNBlocks: Int): ExportResult {
        var firstBlock = -1L
        var lastBlock = -1L
        var numBlocks = 0L
        BufferedOutputStream(FileOutputStream(blocksFile.toFile())).use { output ->
            db.getAllBlocksWithTransactions(ctx, fromHeight = fromHeight, upToHeight = upToHeight) {
                if (firstBlock == -1L) firstBlock = it.blockHeight
                if (numBlocks % logNBlocks == 0L) logger.info("Export block at height ${it.blockHeight}")
                output.write(GtvEncoder.encodeGtv(encodeBlockEntry(it)))
                lastBlock = it.blockHeight
                numBlocks++
            }

            output.write(GtvEncoder.encodeGtv(GtvNull))
        }
        return ExportResult(fromHeight = firstBlock, toHeight = lastBlock, numBlocks = numBlocks)
    }

    /**
     * @param storage             storage
     * @param chainId             chain to import
     * @param configurationsFile  file to import blockchain configurations from
     * @param blocksFile          file to import blocks and transactions from
     * @param incremental         import new configurations and blocks to existing blockchain
     *
     * @return jobId
     */
    fun createImportJob(storage: Storage, chainId: Long, configurationsFile: Path, blocksFile: Path, incremental: Boolean): Int =
        withReadWriteConnection(storage, chainId) { ctx ->
            val db = DatabaseAccess.of(ctx)

            val existingChain = db.getBlockchainRid(ctx)
            if (existingChain != null && !incremental) {
                throw UserMistake("Cannot import to already existing chain $chainId with bc-rid ${existingChain.toHex()} in non-incremental mode")
            }
            if (existingChain == null && incremental) {
                throw UserMistake("Blockchain $chainId not found in incremental mode")
            }

            BufferedInputStream(FileInputStream(configurationsFile.toFile())).use { stream ->
                val blockchainRid = BlockchainRid(GtvDecoder.decodeGtv(stream).asByteArray())
                if (existingChain != null && existingChain != blockchainRid) {
                    throw UserMistake("Blockchain RID mismatch in incremental mode, has ${existingChain.toHex()} but trying to import ${blockchainRid.toHex()}")
                }
                db.initializeBlockchain(ctx, blockchainRid)
            }

            db.createImportJob(ctx, chainId,
                    configurationsFile = configurationsFile.toAbsolutePath().toString(),
                    blocksFile = blocksFile.toAbsolutePath().toString(),
                    ImportJobState.CONFIGURATIONS)
        }

    /**
     * @param nodeKeyPair         KeyPair of the node
     * @param cryptoSystem        CryptoSystem of the node
     * @param storage             storage
     * @param chainId             chain to import
     * @param configurationsFile  file to import blockchain configurations from
     * @param blocksFile          file to import blocks and transactions from
     * @param incremental         import new configurations and blocks to existing blockchain
     * @param logNBlocks          log every N block
     */
    fun importBlockchain(nodeKeyPair: KeyPair, cryptoSystem: CryptoSystem, storage: Storage, chainId: Long,
                         configurationsFile: Path, blocksFile: Path, incremental: Boolean = false, logNBlocks: Int = 100): ImportResult {
        val (blockchainRid, heights) = withReadWriteConnection(storage, chainId) { ctx ->
            val db = DatabaseAccess.of(ctx)

            val existingChain = db.getBlockchainRid(ctx)
            if (existingChain != null && !incremental) {
                throw UserMistake("Cannot import to already existing chain $chainId with bc-rid ${existingChain.toHex()} in non-incremental mode")
            }
            if (existingChain == null && incremental) {
                throw UserMistake("Blockchain $chainId not found in incremental mode")
            }

            importConfigurations(ctx, db, configurationsFile, existingChain)
        }

        return withLoggingContext(
                CHAIN_IID_TAG to chainId.toString(),
                BLOCKCHAIN_RID_TAG to blockchainRid.toHex()
        ) {
            logger.info("Importing blockchain from ${configurationsFile.toAbsolutePath()} and ${blocksFile.toAbsolutePath()}...")
            val importResult = importBlocks(heights.toSet(), blocksFile, logNBlocks, storage, chainId, blockchainRid, nodeKeyPair, cryptoSystem)

            val message = if (importResult.numBlocks > 0)
                "Import of ${importResult.numBlocks} blocks ${importResult.fromHeight}..${importResult.toHeight} to chain $chainId with bc-rid ${blockchainRid.toHex()} completed"
            else
                "No blocks to import to chain $chainId with bc-rid ${blockchainRid.toHex()}"
            logger.info(message)
            importResult
        }
    }

    private fun importConfigurations(ctx: EContext, db: DatabaseAccess, configurationsFile: Path, existingChain: BlockchainRid?) =
            BufferedInputStream(FileInputStream(configurationsFile.toFile())).use { stream ->
                val blockchainRid = BlockchainRid(GtvDecoder.decodeGtv(stream).asByteArray())
                if (existingChain != null && existingChain != blockchainRid) {
                    throw UserMistake("Blockchain RID mismatch in incremental mode, has ${existingChain.toHex()} but trying to import ${blockchainRid.toHex()}")
                }
                db.initializeBlockchain(ctx, blockchainRid)
                val heights = buildList {
                    while (true) {
                        val gtv = GtvDecoder.decodeGtv(stream)
                        if (gtv.isNull()) break
                        val height = gtv.asArray()[0].asInteger()
                        add(height)
                        val configurationData = gtv.asArray()[1].asByteArray()
                        db.addConfigurationData(ctx, height, configurationData)
                    }
                }
                blockchainRid to heights
            }

    private fun importBlocks(configurationHeights: Set<Long>, blocksFile: Path, logNBlocks: Int, storage: Storage, chainId: Long, blockchainRid: BlockchainRid,
                             nodeKeyPair: KeyPair, cryptoSystem: CryptoSystem): ImportResult {
        val partialContext = BaseBlockchainContext(chainId, blockchainRid, NODE_ID_READ_ONLY, nodeKeyPair.pubKey.data)
        val blockSigMaker = cryptoSystem.buildSigMaker(nodeKeyPair)

        var firstBlock = -1L
        var lastBlock = -1L
        var numBlocks = 0L
        BufferedInputStream(FileInputStream(blocksFile.toFile())).use { stream ->
            var currentConfiguration: BlockchainConfiguration? = null
            while (true) {
                val gtv = GtvDecoder.decodeGtv(stream)
                if (gtv.isNull()) break
                val (blockHeader, blockWitness, transactions) = decodeBlockEntry(gtv)
                val blockHeight = blockHeader.blockHeaderRec.getHeight()
                if (firstBlock == -1L) firstBlock = blockHeight

                withReadWriteConnection(storage, chainId) { ctx ->
                    if (blockHeight in configurationHeights) {
                        val rawConfigurationData = DatabaseAccess.of(ctx).getConfigurationData(ctx, blockHeight)
                                ?: throw ProgrammerMistake("Cannot load configuration for height $blockHeight even though we just added it")
                        currentConfiguration = makeBlockchainConfiguration(rawConfigurationData, partialContext, blockSigMaker, ctx, cryptoSystem)
                    }
                    val thisConfiguration = currentConfiguration ?: throw UserMistake("No initial configuration")
                    if (blockHeight % logNBlocks == 0L) logger.info("Import block ${blockHeader.blockRID.toHex()} at height $blockHeight")
                    importBlock(ctx, thisConfiguration, blockHeader, transactions, blockWitness)
                }
                lastBlock = blockHeight
                numBlocks++
            }
        }
        return ImportResult(fromHeight = firstBlock, toHeight = lastBlock, numBlocks = numBlocks, blockchainRid = blockchainRid)
    }

    private fun makeBlockchainConfiguration(rawConfigurationData: ByteArray, partialContext: BaseBlockchainContext,
                                            blockSigMaker: SigMaker, ctx: EContext, cryptoSystem: CryptoSystem): BlockchainConfiguration {
        val blockConfData = BlockchainConfigurationData.fromRaw(rawConfigurationData)
        val factory = newInstanceOf<BlockchainConfigurationFactory>(blockConfData.configurationFactory)
        return factory.makeBlockchainConfiguration(blockConfData, partialContext, blockSigMaker, ctx, cryptoSystem)
    }

    private fun importBlock(ctx: EContext, blockchainConfiguration: BlockchainConfiguration, blockHeader: BaseBlockHeader,
                            rawTransactions: List<ByteArray>, blockWitness: BaseBlockWitness) {
        val blockBuilder = blockchainConfiguration.makeBlockBuilder(ctx)
        blockBuilder.begin(blockHeader)
        val transactions = rawTransactions.parallelStream().map { rawTransaction ->
            decodeTransaction(blockchainConfiguration, rawTransaction)
        }.toList()
        for (transaction in transactions) {
            blockBuilder.appendTransaction(transaction)
        }
        blockBuilder.finalizeAndValidate(blockHeader)
        blockBuilder.commit(blockWitness)
    }

    private fun decodeTransaction(blockchainConfiguration: BlockchainConfiguration, txData: ByteArray): Transaction {
        val tx = blockchainConfiguration.getTransactionFactory().decodeTransaction(txData)
        tx.checkCorrectness()
        return tx
    }

    private fun encodeBlockEntry(block: DatabaseAccess.BlockWithTransactions): Gtv = GtvFactory.gtv(
            GtvFactory.gtv(block.blockHeader),
            GtvFactory.gtv(block.witness),
            GtvFactory.gtv(block.transactions.map { GtvFactory.gtv(it) })
    )

    internal fun decodeBlockEntry(gtv: Gtv): Triple<BaseBlockHeader, BaseBlockWitness, List<ByteArray>> = Triple(
            BaseBlockHeader(gtv.asArray()[0].asByteArray(), GtvMerkleHashCalculator(::sha256Digest)),
            BaseBlockWitness.fromBytes(gtv.asArray()[1].asByteArray()),
            gtv.asArray()[2].asArray().map { it.asByteArray() }
    )
}
