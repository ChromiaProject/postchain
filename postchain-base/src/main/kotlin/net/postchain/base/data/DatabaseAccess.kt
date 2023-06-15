// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base.data

import net.postchain.base.PeerInfo
import net.postchain.base.configuration.FaultyConfiguration
import net.postchain.base.importexport.ImportJob
import net.postchain.base.importexport.ImportJobState
import net.postchain.base.snapshot.Page
import net.postchain.common.BlockchainRid
import net.postchain.common.data.Hash
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.types.WrappedByteArray
import net.postchain.core.AppContext
import net.postchain.core.BlockEContext
import net.postchain.core.EContext
import net.postchain.core.NodeRid
import net.postchain.core.Transaction
import net.postchain.core.TransactionInfoExt
import net.postchain.core.TxDetail
import net.postchain.core.TxEContext
import net.postchain.core.block.BlockHeader
import net.postchain.core.block.BlockWitness
import net.postchain.crypto.PubKey
import java.sql.Connection
import java.time.Instant

interface DatabaseAccess {

    class BlockInfo(
            val blockIid: Long,
            val blockHeader: ByteArray,
            val witness: ByteArray)

    class BlockInfoExt(
            val blockRid: ByteArray,
            val blockHeight: Long,
            val blockHeader: ByteArray,
            val witness: ByteArray,
            val timestamp: Long)

    class EventInfo(
            val pos: Long,
            val blockHeight: Long,
            val hash: Hash,
            val data: ByteArray)

    class AccountState(
            val blockHeight: Long,
            val stateN: Long,
            val data: ByteArray)

    class BlockWithTransactions(
            val blockHeight: Long,
            val blockHeader: ByteArray,
            val witness: ByteArray,
            val transactions: List<ByteArray>)

    fun tableName(ctx: EContext, table: String): String

    fun checkCollation(connection: Connection, suppressError: Boolean)

    fun isSavepointSupported(): Boolean
    fun isSchemaExists(connection: Connection, schema: String): Boolean
    fun createSchema(connection: Connection, schema: String)
    fun setCurrentSchema(connection: Connection, schema: String)
    fun dropSchemaCascade(connection: Connection, schema: String)
    fun dropTable(connection: Connection, tableName: String)

    /**
     * @return iid of the newly created container
     */
    fun createContainer(ctx: AppContext, name: String): Int
    fun getContainerIid(ctx: AppContext, name: String): Int?

    fun initializeApp(connection: Connection, expectedDbVersion: Int, allowUpgrade: Boolean)
    fun initializeBlockchain(ctx: EContext, blockchainRid: BlockchainRid)
    fun removeBlockchain(ctx: EContext): Boolean
    fun removeAllBlockchainSpecificTables(ctx: EContext)
    fun removeBlockchainFromMustSyncUntil(ctx: EContext): Boolean
    fun getChainId(ctx: EContext, blockchainRid: BlockchainRid): Long?
    fun getMaxChainId(ctx: EContext): Long?
    fun getMaxSystemChainId(ctx: EContext): Long?

    fun getBlockchainRid(ctx: EContext): BlockchainRid?
    fun insertBlock(ctx: EContext, height: Long): Long
    fun insertTransaction(ctx: BlockEContext, tx: Transaction, transactionNumber: Long): Long
    fun finalizeBlock(ctx: BlockEContext, header: BlockHeader)

    fun commitBlock(ctx: BlockEContext, w: BlockWitness)
    fun getBlockHeight(ctx: EContext, blockRID: ByteArray, chainId: Long): Long?
    fun getBlockRID(ctx: EContext, height: Long): ByteArray?
    fun getBlockHeader(ctx: EContext, blockRID: ByteArray): ByteArray
    fun getBlockTransactions(ctx: EContext, blockRID: ByteArray, hashesOnly: Boolean): List<TxDetail>
    fun getWitnessData(ctx: EContext, blockRID: ByteArray): ByteArray
    fun getLastBlockHeight(ctx: EContext): Long
    fun getLastBlockRid(ctx: EContext, chainId: Long): ByteArray?
    fun getLastBlockTimestamp(ctx: EContext): Long
    fun getBlockHeightInfo(ctx: EContext, bcRid: BlockchainRid): Pair<Long, ByteArray>?
    fun getTxRIDsAtHeight(ctx: EContext, height: Long): Array<ByteArray>
    fun getBlockInfo(ctx: EContext, txRID: ByteArray): BlockInfo?
    fun getTxHash(ctx: EContext, txRID: ByteArray): ByteArray
    fun getBlockTxRIDs(ctx: EContext, blockIid: Long): List<ByteArray>
    fun getBlockTxHashes(ctx: EContext, blockIid: Long): List<ByteArray>
    fun getTxBytes(ctx: EContext, txRID: ByteArray): ByteArray?
    fun isTransactionConfirmed(ctx: EContext, txRID: ByteArray): Boolean
    fun getBlock(ctx: EContext, blockRID: ByteArray): BlockInfoExt?
    fun getBlocks(ctx: EContext, blockTime: Long, limit: Int): List<BlockInfoExt>
    fun getBlocksBeforeHeight(ctx: EContext, blockHeight: Long, limit: Int): List<BlockInfoExt>
    fun getTransactionInfo(ctx: EContext, txRID: ByteArray): TransactionInfoExt?
    fun getTransactionsInfo(ctx: EContext, beforeTime: Long, limit: Int): List<TransactionInfoExt>
    fun getLastTransactionNumber(ctx: EContext): Long

    /**
     * @param fromHeight   only fetch blocks from and including this height,
     *                     set to `0L` to start from first block
     * @param upToHeight   only fetch blocks up to and including this height,
     *                     set to `Long.MAX_VALUE` to continue to last block
     */
    fun getAllBlocksWithTransactions(ctx: EContext, fromHeight: Long = 0L, upToHeight: Long = Long.MAX_VALUE,
                                     blockHandler: (BlockWithTransactions) -> Unit)

    // Blockchain configurations
    fun findConfigurationHeightForBlock(ctx: EContext, height: Long): Long?
    fun findNextConfigurationHeight(ctx: EContext, height: Long): Long?
    fun listConfigurations(ctx: EContext): List<Long>
    fun listConfigurationHashes(ctx: EContext): List<ByteArray>
    fun configurationHashExists(ctx: EContext, hash: ByteArray): Boolean
    fun removeConfiguration(ctx: EContext, height: Long): Int
    fun getAllConfigurations(ctx: EContext): List<Pair<Long, WrappedByteArray>>
    fun getAllConfigurations(connection: Connection, chainId: Long): List<Pair<Long, WrappedByteArray>>
    fun getDependenciesOnBlockchain(ctx: EContext): List<BlockchainRid>

    /** Get configuration data at exactly given height */
    fun getConfigurationData(ctx: EContext, height: Long): ByteArray?

    /** Get configuration data at <= given height */
    fun getConfigurationDataForHeight(ctx: EContext, height: Long): ByteArray?

    fun getConfigurationData(ctx: EContext, hash: ByteArray): ByteArray?
    fun addConfigurationData(ctx: EContext, height: Long, data: ByteArray)

    fun getFaultyConfiguration(ctx: EContext): FaultyConfiguration?
    fun addFaultyConfiguration(ctx: EContext, faultyConfiguration: FaultyConfiguration)
    fun updateFaultyConfigurationReportHeight(ctx: EContext, height: Long)

    // Event and State
    fun insertEvent(ctx: TxEContext, prefix: String, height: Long, position: Long, hash: Hash, data: ByteArray)
    fun getEvent(ctx: EContext, prefix: String, eventHash: ByteArray): EventInfo?
    fun getEventsOfHeight(ctx: EContext, prefix: String, blockHeight: Long): List<EventInfo>
    fun getEventsAboveHeight(ctx: EContext, prefix: String, blockHeight: Long): List<EventInfo>
    fun pruneEvents(ctx: EContext, prefix: String, heightMustBeHigherThan: Long)
    fun insertState(ctx: EContext, prefix: String, height: Long, state_n: Long, data: ByteArray)
    fun getAccountState(ctx: EContext, prefix: String, height: Long, state_n: Long): AccountState?
    fun pruneAccountStates(ctx: EContext, prefix: String, left: Long, right: Long, heightMustBeHigherThan: Long)
    fun insertPage(ctx: EContext, name: String, page: Page)
    fun getPage(ctx: EContext, name: String, height: Long, level: Int, left: Long): Page?
    fun getHighestLevelPage(ctx: EContext, name: String, height: Long): Int

    // Peers
    fun getPeerInfoCollection(ctx: AppContext): Array<PeerInfo>
    fun findPeerInfo(ctx: AppContext, host: String?, port: Int?, pubKeyPattern: String?): Array<PeerInfo>
    fun addPeerInfo(ctx: AppContext, peerInfo: PeerInfo): Boolean
    fun addPeerInfo(ctx: AppContext, host: String, port: Int, pubKey: String, timestamp: Instant? = null): Boolean
    fun updatePeerInfo(ctx: AppContext, host: String, port: Int, pubKey: PubKey, timestamp: Instant? = null): Boolean
    fun removePeerInfo(ctx: AppContext, pubKey: PubKey): Array<PeerInfo>

    // Extra nodes to sync from
    fun getBlockchainReplicaCollection(ctx: AppContext): Map<BlockchainRid, List<NodeRid>>
    fun existsBlockchainReplica(ctx: AppContext, brid: BlockchainRid, pubkey: PubKey): Boolean
    fun addBlockchainReplica(ctx: AppContext, brid: BlockchainRid, pubKey: PubKey): Boolean
    fun removeBlockchainReplica(ctx: AppContext, brid: BlockchainRid?, pubKey: PubKey): Set<BlockchainRid>
    fun removeAllBlockchainReplicas(ctx: EContext): Boolean
    fun getBlockchainsToReplicate(ctx: AppContext, pubkey: String): Set<BlockchainRid>

    //Avoid potential chain split
    fun setMustSyncUntil(ctx: AppContext, blockchainRID: BlockchainRid, height: Long): Boolean
    fun getMustSyncUntil(ctx: AppContext): Map<Long, Long>
    fun getChainIds(ctx: AppContext): Map<BlockchainRid, Long>

    // To be able to create tables not automatically created by the system
    // (most mandatory tables are not "creatable")
    fun createEventLeafTable(ctx: EContext, prefix: String) // Note: Not used by anchoring
    fun createPageTable(ctx: EContext, prefix: String)
    fun createStateLeafTable(ctx: EContext, prefix: String)
    fun createStateLeafTableIndex(ctx: EContext, prefix: String, index: Int)

    fun getImportJobs(ctx: AppContext): List<ImportJob>
    fun createImportJob(ctx: AppContext, chainId: Long, configurationsFile: String, blocksFile: String, state: ImportJobState): Int
    fun updateImportJob(ctx: AppContext, jobId: Int, state: ImportJobState)
    fun deleteImportJob(ctx: AppContext, jobId: Int)

    companion object {
        fun of(ctx: AppContext): DatabaseAccess {
            return ctx.getInterface(DatabaseAccess::class.java)
                    ?: throw ProgrammerMistake("DatabaseAccess not accessible through EContext")
        }
    }
}
