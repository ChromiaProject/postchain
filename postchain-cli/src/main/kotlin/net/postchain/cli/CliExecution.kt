// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.cli

import mu.KLogging
import mu.withLoggingContext
import net.postchain.PostchainNode
import net.postchain.StorageBuilder
import net.postchain.api.internal.BlockchainApi
import net.postchain.api.internal.PeerApi
import net.postchain.base.BlockchainRelatedInfo
import net.postchain.base.gtv.GtvToBlockchainRidFactory
import net.postchain.base.runStorageCommand
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.NotFound
import net.postchain.common.exception.UserMistake
import net.postchain.common.hexStringToByteArray
import net.postchain.config.app.AppConfig
import net.postchain.config.node.NodeConfigurationProviderFactory
import net.postchain.core.BadDataMistake
import net.postchain.core.BadDataType
import net.postchain.crypto.PubKey
import net.postchain.gtv.Gtv
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
        // If brid is specified in nodeConfigFile, use that instead of calculating it from blockchain configuration.
        val appConfig = AppConfig.fromPropertiesFile(nodeConfigFile)
        val keyString = "brid.chainid." + chainId.toString()
        val brid = if (appConfig.containsKey(keyString)) BlockchainRid.buildFromHex(appConfig.getString(keyString)) else
            GtvToBlockchainRidFactory.calculateBlockchainRid(blockchainConfig, appConfig.cryptoSystem)

        return runStorageCommand(nodeConfigFile, chainId) { ctx ->
            when (mode) {
                AlreadyExistMode.ERROR -> {
                    if (BlockchainApi.initializeBlockchain(ctx, brid, false, blockchainConfig, givenDependencies)) {
                        brid
                    } else {
                        throw CliException(
                                "Blockchain with chainId $chainId already exists. Use -f flag to force addition."
                        )
                    }
                }

                AlreadyExistMode.FORCE -> {
                    BlockchainApi.initializeBlockchain(ctx, brid, true, blockchainConfig, givenDependencies)
                    brid
                }

                AlreadyExistMode.IGNORE -> {
                    BlockchainApi.initializeBlockchain(ctx, brid, false, blockchainConfig, givenDependencies)
                    brid
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
        runStorageCommand(nodeConfigFile, chainId) { ctx ->
            try {
                when (mode) {
                    AlreadyExistMode.ERROR -> {
                        if (!BlockchainApi.addConfiguration(ctx, height, false, blockchainConfig, allowUnknownSigners)) {
                            throw CliException(
                                    "Blockchain configuration of chainId $chainId at " +
                                            "height $height already exists. Use -f flag to force addition."
                            )
                        }
                    }

                    AlreadyExistMode.FORCE -> {
                        BlockchainApi.addConfiguration(ctx, height, true, blockchainConfig, allowUnknownSigners)
                    }

                    AlreadyExistMode.IGNORE -> {
                        if (!BlockchainApi.addConfiguration(ctx, height, false, blockchainConfig, allowUnknownSigners))
                            println("Blockchain configuration of chainId $chainId at height $height already exists")
                    }
                }
            } catch (e: BadDataMistake) {
                if (e.type == BadDataType.MISSING_PEERINFO) {
                    throw CliException(e.message + " Please add node with command peerinfo-add or set flag --allow-unknown-signers.")
                } else {
                    throw CliException("Bad configuration format.")
                }
            }
            Unit
        }
    }

    fun setMustSyncUntil(nodeConfigFile: String, blockchainRID: BlockchainRid, height: Long): Boolean =
            runStorageCommand(nodeConfigFile) { ctx ->
                BlockchainApi.setMustSyncUntil(ctx, blockchainRID, height)
            }

    fun getMustSyncUntilHeight(nodeConfigFile: String): Map<Long, Long> = runStorageCommand(nodeConfigFile) { ctx ->
        BlockchainApi.getMustSyncUntilHeight(ctx)
    }

    fun peerinfoAdd(nodeConfigFile: String, host: String, port: Int, pubKey: String, mode: AlreadyExistMode): Boolean =
            runStorageCommand(nodeConfigFile) { ctx ->
                // mode tells us how to react upon an error caused if pubkey already exist (throw error or force write).
                when (mode) {
                    AlreadyExistMode.ERROR -> {
                        val added = PeerApi.addPeer(ctx, PubKey(pubKey.hexStringToByteArray()), host, port, false)
                        if (!added) {
                            throw CliException("Peerinfo with pubkey already exists. Using -f to force update")
                        }
                        true
                    }

                    AlreadyExistMode.FORCE -> {
                        PeerApi.addPeer(ctx, PubKey(pubKey.hexStringToByteArray()), host, port, true)
                    }

                    AlreadyExistMode.IGNORE -> {
                        PeerApi.addPeer(ctx, PubKey(pubKey.hexStringToByteArray()), host, port, false)
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
            BlockchainApi.checkBlockchain(ctx, blockchainRID)
        }
    }

    fun getConfiguration(nodeConfigFile: String, chainId: Long, height: Long): ByteArray? =
            runStorageCommand(nodeConfigFile, chainId) { ctx ->
                BlockchainApi.getConfiguration(ctx, height)
            }

    fun listConfigurations(nodeConfigFile: String, chainId: Long) =
            runStorageCommand(nodeConfigFile, chainId) { ctx ->
                BlockchainApi.listConfigurations(ctx)
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