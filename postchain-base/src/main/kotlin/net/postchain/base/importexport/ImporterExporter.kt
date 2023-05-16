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
import java.nio.file.Path

object ImporterExporter : KLogging() {
    /**
     * @param upToHeight   only export configurations and blocks up to and including this height,
     *                     set to `Long.MAX_VALUE` to export everything
     * @param logNBlocks   log every N block
     */
    fun exportBlockchain(storage: Storage, chainId: Long, configurationsFile: Path, blocksFile: Path,
                         upToHeight: Long = Long.MAX_VALUE, logNBlocks: Int = 100) {
        withReadConnection(storage, chainId) { ctx ->
            val db = DatabaseAccess.of(ctx)

            val blockchainRid = db.getBlockchainRid(ctx)
                    ?: throw UserMistake("Blockchain with id $chainId not found")

            withLoggingContext(
                    CHAIN_IID_TAG to chainId.toString(),
                    BLOCKCHAIN_RID_TAG to blockchainRid.toHex()
            ) {
                logger.info("Exporting blockchain chainId $chainId and bc-rid ${blockchainRid.toHex()}...")

                BufferedOutputStream(FileOutputStream(configurationsFile.toFile())).use { output ->
                    output.write(GtvEncoder.encodeGtv(GtvFactory.gtv(blockchainRid.data)))

                    for ((height, configurationData) in db.getAllConfigurations(ctx)) {
                        if (height > upToHeight) break
                        output.write(GtvEncoder.encodeGtv(GtvFactory.gtv(GtvFactory.gtv(height), GtvFactory.gtv(configurationData))))
                    }

                    output.write(GtvEncoder.encodeGtv(GtvNull))
                }

                var height = 0L
                BufferedOutputStream(FileOutputStream(blocksFile.toFile())).use { output ->
                    db.getAllBlocksWithTransactions(ctx, upToHeight) {
                        if (height % logNBlocks == 0L) logger.info("Export block at height $height")
                        output.write(GtvEncoder.encodeGtv(it.toGtv()))
                        height++
                    }

                    output.write(GtvEncoder.encodeGtv(GtvNull))
                }

                logger.info("Export of $height blocks to ${configurationsFile.toAbsolutePath()} and ${blocksFile.toAbsolutePath()} completed")
            }
        }
    }

    /**
     * @param logNBlocks   log every N block
     */
    fun importBlockchain(nodeKeyPair: KeyPair, cryptoSystem: CryptoSystem, storage: Storage, chainId: Long,
                         configurationsFile: Path, blocksFile: Path, logNBlocks: Int = 100): BlockchainRid {
        val (blockchainRid, configurations) = withReadWriteConnection(storage, chainId) { ctx ->
            val db = DatabaseAccess.of(ctx)

            val existingChain = db.getBlockchainRid(ctx)
            if (existingChain != null) {
                throw UserMistake("Cannot import to already existing chainId $chainId (bc-rid ${existingChain.toHex()}")
            }

            importConfigurations(ctx, db, configurationsFile)
        }

        withLoggingContext(
                CHAIN_IID_TAG to chainId.toString(),
                BLOCKCHAIN_RID_TAG to blockchainRid.toHex()
        ) {
            logger.info("Importing blockchain from ${configurationsFile.toAbsolutePath()} and ${blocksFile.toAbsolutePath()} to chainId $chainId and bc-rid ${blockchainRid.toHex()}...")

            val importedBlocks = importBlocks(blocksFile, configurations, logNBlocks, storage, chainId, blockchainRid, nodeKeyPair, cryptoSystem)

            logger.info("Import of $importedBlocks blocks to chainId $chainId and bc-rid ${blockchainRid.toHex()} completed")
        }
        return blockchainRid
    }

    private fun importConfigurations(ctx: EContext, db: DatabaseAccess, configurationsFile: Path) =
            BufferedInputStream(FileInputStream(configurationsFile.toFile())).use { stream ->
                val blockchainRid = BlockchainRid(GtvDecoder.decodeGtv(stream).asByteArray())
                db.initializeBlockchain(ctx, blockchainRid)
                val configurations = buildList {
                    while (true) {
                        val gtv = GtvDecoder.decodeGtv(stream)
                        if (gtv.isNull()) break
                        val height = gtv.asArray()[0].asInteger()
                        val configurationData = gtv.asArray()[1].asByteArray()
                        db.addConfigurationData(ctx, height, configurationData)
                        add(height to configurationData)
                    }
                }
                blockchainRid to configurations
            }

    private fun importBlocks(blocksFile: Path, configurations: List<Pair<Long, ByteArray>>, logNBlocks: Int,
                             storage: Storage, chainId: Long, blockchainRid: BlockchainRid, nodeKeyPair: KeyPair, cryptoSystem: CryptoSystem): Long {
        var blocks = 0L
        BufferedInputStream(FileInputStream(blocksFile.toFile())).use { stream ->
            while (true) {
                val gtv = GtvDecoder.decodeGtv(stream)
                if (gtv.isNull()) break
                val blockWithTransactions = blockWithTransactionsFromGtv(gtv)
                val blockHeader = BaseBlockHeader(blockWithTransactions.blockHeader, GtvMerkleHashCalculator(::sha256Digest))
                val blockWitness = BaseBlockWitness.fromBytes(blockWithTransactions.witness)
                val transactions = blockWithTransactions.transactions

                val height = blockHeader.blockHeaderRec.getHeight()
                val rawConfigurationData = configurations.filter { it.first <= height }.maxByOrNull { it.first }?.second
                        ?: throw UserMistake("No initial configuration")
                if (height % logNBlocks == 0L) logger.info("Import block ${blockHeader.blockRID.toHex()} at height $height")

                withReadWriteConnection(storage, chainId) { ctx ->
                    importBlock(rawConfigurationData, chainId, blockchainRid, nodeKeyPair, cryptoSystem, ctx, blockHeader, transactions, blockWitness)
                }
                blocks++
            }
        }
        return blocks
    }

    private fun importBlock(rawConfigurationData: ByteArray, chainId: Long, blockchainRid: BlockchainRid, nodeKeyPair: KeyPair, cryptoSystem: CryptoSystem, ctx: EContext, blockHeader: BaseBlockHeader, transactions: List<ByteArray>, blockWitness: BaseBlockWitness) {
        val blockConfData = BlockchainConfigurationData.fromRaw(rawConfigurationData)
        val factory = newInstanceOf<BlockchainConfigurationFactory>(blockConfData.configurationFactory)
        val partialContext = BaseBlockchainContext(chainId, blockchainRid, NODE_ID_READ_ONLY, nodeKeyPair.pubKey.data)
        val blockSigMaker = cryptoSystem.buildSigMaker(nodeKeyPair)
        val blockchainConfiguration = factory.makeBlockchainConfiguration(blockConfData, partialContext, blockSigMaker, ctx, cryptoSystem)
        val blockBuilder = blockchainConfiguration.makeBlockBuilder(ctx)

        blockBuilder.begin(blockHeader)
        for (transaction in transactions) { // TODO POS-705 parallelize
            blockBuilder.appendTransaction(decodeTransaction(blockchainConfiguration, transaction))
        }
        blockBuilder.finalizeAndValidate(blockHeader)
        blockBuilder.commit(blockWitness)
    }

    private fun decodeTransaction(blockchainConfiguration: BlockchainConfiguration, txData: ByteArray): Transaction {
        val tx = blockchainConfiguration.getTransactionFactory().decodeTransaction(txData)
        tx.checkCorrectness()
        return tx
    }

    private fun DatabaseAccess.BlockWithTransactions.toGtv(): Gtv =
            GtvFactory.gtv(GtvFactory.gtv(blockHeader), GtvFactory.gtv(witness), GtvFactory.gtv(transactions.map { GtvFactory.gtv(it) }))

    internal fun blockWithTransactionsFromGtv(gtv: Gtv): DatabaseAccess.BlockWithTransactions =
            DatabaseAccess.BlockWithTransactions(
                    gtv.asArray()[0].asByteArray(),
                    gtv.asArray()[1].asByteArray(),
                    gtv.asArray()[2].asArray().map { it.asByteArray() })

}
