// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.cli

import mu.KLogging
import net.postchain.PostchainNode
import net.postchain.StorageBuilder
import net.postchain.api.internal.BlockchainApi
import net.postchain.api.internal.PeerApi
import net.postchain.base.BlockchainRelatedInfo
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.gtv.GtvToBlockchainRidFactory
import net.postchain.base.runStorageCommand
import net.postchain.common.BlockchainRid
import net.postchain.config.app.AppConfig
import net.postchain.config.node.NodeConfigurationProviderFactory
import net.postchain.core.AppContext
import net.postchain.core.BadDataMistake
import net.postchain.core.BadDataType
import net.postchain.core.EContext
import net.postchain.crypto.PubKey
import net.postchain.gtv.Gtv
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
            appConfig: AppConfig,
            chainId: Long,
            blockchainConfigGtv: Gtv,
            mode: AlreadyExistMode = AlreadyExistMode.IGNORE,
            givenDependencies: List<BlockchainRelatedInfo> = listOf()
    ): BlockchainRid {
        return addBlockchainGtv(appConfig, chainId, blockchainConfigGtv, mode, givenDependencies)
    }

    private fun addBlockchainGtv(
            appConfig: AppConfig,
            chainId: Long,
            blockchainConfig: Gtv,
            mode: AlreadyExistMode = AlreadyExistMode.IGNORE,
            givenDependencies: List<BlockchainRelatedInfo> = listOf()
    ): BlockchainRid {
        // If brid is specified in nodeConfigFile, use that instead of calculating it from blockchain configuration.
        val keyString = "brid.chainid.$chainId"
        val brid = if (appConfig.containsKey(keyString)) BlockchainRid.buildFromHex(appConfig.getString(keyString)) else
            GtvToBlockchainRidFactory.calculateBlockchainRid(blockchainConfig, appConfig.cryptoSystem)

        return runStorageCommand(appConfig, chainId) { ctx ->
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
            appConfig: AppConfig,
            blockchainConfig: Gtv,
            chainId: Long,
            height: Long,
            mode: AlreadyExistMode = AlreadyExistMode.IGNORE,
            allowUnknownSigners: Boolean
    ) {
        runStorageCommand(appConfig, chainId) { ctx: EContext ->
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

    fun setMustSyncUntil(appConfig: AppConfig, blockchainRID: BlockchainRid, height: Long): Boolean {
        return runStorageCommand(appConfig) { ctx: AppContext ->
            BlockchainApi.setMustSyncUntil(ctx, blockchainRID, height)
        }
    }

    fun peerinfoAdd(appConfig: AppConfig, host: String, port: Int, pubKey: PubKey, mode: AlreadyExistMode): Boolean {
        return runStorageCommand(appConfig) { ctx: AppContext ->
            // mode tells us how to react upon an error caused if pubkey already exist (throw error or force write).
            when (mode) {
                AlreadyExistMode.ERROR -> {
                    val added = PeerApi.addPeer(ctx, pubKey, host, port, false)
                    if (!added) {
                        throw CliException("Peerinfo with pubkey already exists. Using -f to force update")
                    }
                    true
                }

                AlreadyExistMode.FORCE -> {
                    PeerApi.addPeer(ctx, pubKey, host, port, true)
                }

                AlreadyExistMode.IGNORE -> {
                    PeerApi.addPeer(ctx, pubKey, host, port, false)
                }
            }
        }
    }

    fun runNode(appConfig: AppConfig, chainIds: List<Long>, debug: Boolean) {
        with(PostchainNode(appConfig, wipeDb = false)) {
            chainIds.forEach {
                tryStartBlockchain(it)
            }
        }
    }

    fun findBlockchainRid(appConfig: AppConfig, chainId: Long): BlockchainRid? {
        return runStorageCommand(appConfig, chainId) { ctx: EContext ->
            DatabaseAccess.of(ctx).getBlockchainRid(ctx)
        }
    }

    fun checkBlockchain(appConfig: AppConfig, chainId: Long, blockchainRID: String) {
        runStorageCommand(appConfig, chainId) { ctx: EContext ->
            BlockchainApi.checkBlockchain(ctx, blockchainRID)
        }
    }

    fun getConfiguration(appConfig: AppConfig, chainId: Long, height: Long): ByteArray? {
        return runStorageCommand(appConfig, chainId) { ctx: EContext ->
            BlockchainApi.getConfiguration(ctx, height)
        }
    }

    fun listConfigurations(appConfig: AppConfig, chainId: Long): List<Long> {
        return runStorageCommand(appConfig, chainId) { ctx: EContext ->
            BlockchainApi.listConfigurations(ctx)
        }
    }

    fun waitDb(retryTimes: Int, retryInterval: Long, appConfig: AppConfig) {
        tryCreateBasicDataSource(appConfig)?.let { return } ?: if (retryTimes > 0) {
            Thread.sleep(retryInterval)
            waitDb(retryTimes - 1, retryInterval, appConfig)
        } else throw TimeoutException("Unable to connect to database")
    }

    private fun tryCreateBasicDataSource(appConfig: AppConfig): Connection? {
        return try {
            val storage = StorageBuilder.buildStorage(appConfig)
            NodeConfigurationProviderFactory.createProvider(appConfig) { storage }.getConfiguration()

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
