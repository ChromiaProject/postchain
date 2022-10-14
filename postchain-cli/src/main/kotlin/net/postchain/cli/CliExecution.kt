// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.cli

import mu.KLogging
import mu.withLoggingContext
import net.postchain.PostchainNode
import net.postchain.StorageBuilder
import net.postchain.base.BaseConfigurationDataStore
import net.postchain.base.BlockchainRelatedInfo
import net.postchain.base.PeerInfo
import net.postchain.base.configuration.KEY_SIGNERS
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.data.DependenciesValidator
import net.postchain.base.gtv.GtvToBlockchainRidFactory
import net.postchain.base.runStorageCommand
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.NotFound
import net.postchain.common.exception.UserMistake
import net.postchain.common.toHex
import net.postchain.config.app.AppConfig
import net.postchain.config.node.NodeConfigurationProviderFactory
import net.postchain.core.BadDataMistake
import net.postchain.core.BadDataType
import net.postchain.core.EContext
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvFileReader
import net.postchain.metrics.CHAIN_IID_TAG
import net.postchain.metrics.NODE_PUBKEY_TAG
import org.apache.commons.configuration2.ex.ConfigurationException
import org.apache.commons.dbcp2.BasicDataSource
import java.sql.Connection
import java.sql.SQLException
import java.util.concurrent.TimeoutException

object CliExecution : KLogging() {

    /**
     * @return blockchain RID
     */
    fun addBlockchain(
            nodeConfigFile: String,
            chainId: Long,
            blockchainConfigFile: String,
            mode: AlreadyExistMode = AlreadyExistMode.IGNORE,
            givenDependencies: List<BlockchainRelatedInfo> = listOf()
    ): BlockchainRid {
        val gtv = GtvFileReader.readFile(blockchainConfigFile)
        return addBlockchainGtv(nodeConfigFile, chainId, gtv, mode, givenDependencies)
    }

    private fun addBlockchainGtv(
            nodeConfigFile: String,
            chainId: Long,
            blockchainConfig: Gtv,
            mode: AlreadyExistMode = AlreadyExistMode.IGNORE,
            givenDependencies: List<BlockchainRelatedInfo> = listOf()
    ): BlockchainRid {

        /**
         * If brid is specified in nodeConfigFile, use that instead of calculating it from blockchain configuration.
         */
        fun getBrid(): BlockchainRid {
            val appConfig = AppConfig.fromPropertiesFile(nodeConfigFile)
            val keyString = "brid.chainid." + chainId.toString()
            val brid = if (appConfig.containsKey(keyString)) BlockchainRid.buildFromHex(appConfig.getString(keyString)) else
                GtvToBlockchainRidFactory.calculateBlockchainRid(blockchainConfig, appConfig.cryptoSystem)
            return brid
        }

        return runStorageCommand(nodeConfigFile, chainId) { ctx ->
            val db = DatabaseAccess.of(ctx)

            fun init(): BlockchainRid {
                val brid = getBrid()
                db.initializeBlockchain(ctx, brid)
                DependenciesValidator.validateBlockchainRids(ctx, givenDependencies)
                BaseConfigurationDataStore.addConfigurationData(ctx, 0, blockchainConfig)
                return brid
            }

            when (mode) {
                AlreadyExistMode.ERROR -> {
                    if (db.getBlockchainRid(ctx) == null) {
                        init()
                    } else {
                        throw CliException(
                            "Blockchain with chainId $chainId already exists. Use -f flag to force addition."
                        )
                    }
                }

                AlreadyExistMode.FORCE -> {
                    init()
                }

                AlreadyExistMode.IGNORE -> {
                    db.getBlockchainRid(ctx) ?: init()
                }
            }
        }
    }

    fun addConfiguration(
            nodeConfigFile: String,
            blockchainConfigFile: String,
            chainId: Long,
            height: Long,
            mode: AlreadyExistMode = AlreadyExistMode.IGNORE,
            allowUnknownSigners: Boolean = false
    ) {
        val gtv = GtvFileReader.readFile(blockchainConfigFile)
        addConfigurationGtv(nodeConfigFile, gtv, chainId, height, mode, allowUnknownSigners)
    }

    private fun addConfigurationGtv(
            nodeConfigFile: String,
            blockchainConfig: Gtv,
            chainId: Long,
            height: Long,
            mode: AlreadyExistMode = AlreadyExistMode.IGNORE,
            allowUnknownSigners: Boolean
    ) {

        val configStore = BaseConfigurationDataStore

        runStorageCommand(nodeConfigFile, chainId) { ctx ->
            val db = DatabaseAccess.of(ctx)
            val lastBlockHeight = db.getLastBlockHeight(ctx)
            if (lastBlockHeight >= height) {
                throw UserMistake("Cannot add configuration at $height, since last block is already at $lastBlockHeight")
            }

            fun init() = try {
                configStore.addConfigurationData(ctx, height, blockchainConfig)
                addFutureSignersAsReplicas(ctx, height, blockchainConfig, allowUnknownSigners)
            } catch (e: BadDataMistake) {
                if (e.type == BadDataType.MISSING_PEERINFO) {
                    throw CliException(e.message + " Please add node with command peerinfo-add or set flag --allow-unknown-signers.")
                } else {
                    throw CliException("Bad configuration format.")
                }
            }

            when (mode) {
                AlreadyExistMode.ERROR -> {
                    if (configStore.getConfigurationData(ctx, height) == null) {
                        init()
                    } else {
                        throw CliException(
                            "Blockchain configuration of chainId $chainId at " +
                                    "height $height already exists. Use -f flag to force addition."
                        )
                    }
                }

                AlreadyExistMode.FORCE -> {
                    init()
                }

                AlreadyExistMode.IGNORE -> {
                    if (configStore.getConfigurationData(ctx, height) == null) {
                        init()
                    } else {
                        println("Blockchain configuration of chainId $chainId at height $height already exists")
                    }
                }
            }
        }
    }

    /** When a new (height > 0) configuration is added, we automatically add signers in that config to table
     * blockchainReplicaNodes (for current blockchain). Useful for synchronization.
     */
    private fun addFutureSignersAsReplicas(eContext: EContext, height: Long, gtvData: Gtv, allowUnknownSigners: Boolean) {
        if (height > 0) {
            val db = DatabaseAccess.of(eContext)
            val brid = db.getBlockchainRid(eContext)!!
            val confGtvDict = gtvData as GtvDictionary
            val signers = confGtvDict[KEY_SIGNERS]!!.asArray().map { it.asByteArray() }
            for (sig in signers) {
                val nodePubkey = sig.toHex()
                // Node must be in PeerInfo, or else it cannot be a blockchain replica.
                val foundInPeerInfo = db.findPeerInfo(eContext, null, null, nodePubkey)
                if (foundInPeerInfo.isNotEmpty()) {
                    db.addBlockchainReplica(eContext, brid.toHex(), nodePubkey)
                    // If the node is not in the peerinfo table and we do not allow unknown signers in a configuration,
                    // throw error
                } else if (!allowUnknownSigners) {
                    throw BadDataMistake(BadDataType.MISSING_PEERINFO,
                            "Signer $nodePubkey does not exist in peerinfos.")
                }
            }
        }
    }


    fun setMustSyncUntil(nodeConfigFile: String, blockchainRID: BlockchainRid, height: Long): Boolean {
        return runStorageCommand(nodeConfigFile) { ctx ->
            val db = DatabaseAccess.of(ctx)
            db.setMustSyncUntil(ctx, blockchainRID, height)
        }
    }

    fun getMustSyncUntilHeight(nodeConfigFile: String): Map<Long, Long>? {
        return runStorageCommand(nodeConfigFile) { ctx ->
            DatabaseAccess.of(ctx).getMustSyncUntil(ctx)
        }
    }

    fun peerinfoAdd(nodeConfigFile: String, host: String, port: Int, pubKey: String, mode: AlreadyExistMode): Boolean {
        return runStorageCommand(nodeConfigFile) { ctx ->
            val db = DatabaseAccess.of(ctx)

            val found: Array<PeerInfo> = db.findPeerInfo(ctx, host, port, null)
            if (found.isNotEmpty()) {
                throw CliException("Peerinfo with port, host already exists.")
            }

            val found2 = db.findPeerInfo(ctx, null, null, pubKey)
            if (found2.isNotEmpty()) {
                when (mode) {
                    // mode tells us how to react upon an error caused if pubkey already exist (throw error or force write).
                    AlreadyExistMode.ERROR -> {
                        throw CliException("Peerinfo with pubkey already exists. Using -f to force update")
                    }
                    AlreadyExistMode.FORCE -> {
                        db.updatePeerInfo(ctx, host, port, pubKey)
                    }
                    else -> false
                }
            } else {
                when (mode) {
                    // In this branch, the pubkey do not already exist, thus we whant to add it, regarless of mode.
                    AlreadyExistMode.ERROR, AlreadyExistMode.FORCE -> {
                        db.addPeerInfo(ctx, host, port, pubKey)
                    }
                    else -> false
                }
            }
        }
    }

    fun runNode(nodeConfigFile: String, chainIds: List<Long>, debug: Boolean) {
        val appConfig = AppConfig.fromPropertiesFile(nodeConfigFile)

        with(PostchainNode(appConfig, wipeDb = false, debug = debug)) {
            chainIds.forEach {
                withLoggingContext(
                    NODE_PUBKEY_TAG to appConfig.pubKey,
                    CHAIN_IID_TAG to it.toString()
                ) {
                    try {
                        startBlockchain(it)
                    } catch (e: NotFound) {
                        logger.error(e.message)
                    } catch (e: UserMistake) {
                        logger.error(e.message)
                    } catch (e: Exception) {
                        logger.error(e) { e.message }
                    }
                }
            }
        }
    }

    fun checkBlockchain(nodeConfigFile: String, chainId: Long, blockchainRID: String) {
        runStorageCommand(nodeConfigFile, chainId) { ctx ->
            val currentBrid = DatabaseAccess.of(ctx).getBlockchainRid(ctx)
            when {
                currentBrid == null -> {
                    throw CliException("Unknown chain-id: $chainId")
                }
                !blockchainRID.equals(currentBrid.toHex(), true) -> {
                    throw CliException(
                        """
                        BlockchainRids are not equal:
                            expected: $blockchainRID
                            actual: ${currentBrid.toHex()}
                    """.trimIndent()
                    )
                }
                BaseConfigurationDataStore.findConfigurationHeightForBlock(ctx, 0) == null -> {
                    throw CliException("No configuration found")
                }
                else -> {
                }
            }
        }
    }

    fun getConfiguration(nodeConfigFile: String, chainId: Long, height: Long): ByteArray? {
        return runStorageCommand(nodeConfigFile, chainId) { ctx ->
            val db = DatabaseAccess.of(ctx)
            db.getConfigurationData(ctx, height)
        }
    }

    fun listConfigurations(nodeConfigFile: String, chainId: Long) =
        runStorageCommand(nodeConfigFile, chainId) { ctx ->
            DatabaseAccess.of(ctx).listConfigurations(ctx)
        }

    fun waitDb(retryTimes: Int, retryInterval: Long, nodeConfigFile: String) {
        tryCreateBasicDataSource(nodeConfigFile)?.let { return } ?: if (retryTimes > 0) {
            Thread.sleep(retryInterval)
            waitDb(retryTimes - 1, retryInterval, nodeConfigFile)
        } else throw TimeoutException("Unable to connect to database")
    }

    private fun tryCreateBasicDataSource(nodeConfigFile: String): Connection? {
        return try {
            val appConfig = AppConfig.fromPropertiesFile(nodeConfigFile)
            val storage = StorageBuilder.buildStorage(appConfig)
            val nodeConfig = NodeConfigurationProviderFactory.createProvider(
                    appConfig
            ) { storage }.getConfiguration()

            BasicDataSource().apply {
                addConnectionProperty("currentSchema", appConfig.databaseSchema)
                driverClassName = appConfig.databaseDriverclass
                url = appConfig.databaseUrl //?loggerLevel=OFF"
                username = appConfig.databaseUsername
                password = appConfig.databasePassword
                defaultAutoCommit = false
            }.connection
        } catch (e: SQLException) {
            null
        } catch (e: ConfigurationException) {
            throw CliException("Failed to read configuration")
        }
    }

}