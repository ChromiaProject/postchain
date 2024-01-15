// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.cli

import net.postchain.api.internal.BlockchainApi
import net.postchain.api.internal.PeerApi
import net.postchain.base.BlockchainRelatedInfo
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.gtv.GtvToBlockchainRidFactory
import net.postchain.base.runStorageCommand
import net.postchain.common.BlockchainRid
import net.postchain.config.app.AppConfig
import net.postchain.core.AppContext
import net.postchain.core.BadDataException
import net.postchain.core.EContext
import net.postchain.core.MissingPeerInfoException
import net.postchain.crypto.PubKey
import net.postchain.gtv.Gtv

object CliExecution {

    /**
     * @return blockchain RID
     */
    fun addBlockchain(
            appConfig: AppConfig,
            chainId: Long,
            blockchainConfig: Gtv,
            mode: AlreadyExistMode = AlreadyExistMode.IGNORE,
            givenDependencies: List<BlockchainRelatedInfo> = listOf(),
            validate: Boolean = true
    ): BlockchainRid {
        // If brid is specified in nodeConfigFile, use that instead of calculating it from blockchain configuration.
        val keyString = "brid.chainid.$chainId"
        val brid = if (appConfig.containsKey(keyString)) BlockchainRid.buildFromHex(appConfig.getString(keyString)) else
            GtvToBlockchainRidFactory.calculateBlockchainRid(blockchainConfig, appConfig.cryptoSystem)

        return runStorageCommand(appConfig, chainId) { ctx ->
            when (mode) {
                AlreadyExistMode.ERROR -> {
                    if (BlockchainApi.initializeBlockchain(ctx, brid, false, blockchainConfig, givenDependencies, validate)) {
                        brid
                    } else {
                        throw CliException(
                                "Blockchain with chainId $chainId already exists. Use -f flag to force addition."
                        )
                    }
                }

                AlreadyExistMode.FORCE -> {
                    BlockchainApi.initializeBlockchain(ctx, brid, true, blockchainConfig, givenDependencies, validate)
                    brid
                }

                AlreadyExistMode.IGNORE -> {
                    BlockchainApi.initializeBlockchain(ctx, brid, false, blockchainConfig, givenDependencies, validate)
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
            mode: AlreadyExistMode,
            allowUnknownSigners: Boolean,
            validate: Boolean
    ) {
        runStorageCommand(appConfig, chainId) { ctx: EContext ->
            try {
                when (mode) {
                    AlreadyExistMode.ERROR -> {
                        if (!BlockchainApi.addConfiguration(appConfig.pubKey, ctx, height, false, blockchainConfig, allowUnknownSigners, validate)) {
                            throw CliException(
                                    "Blockchain configuration of chainId $chainId at " +
                                            "height $height already exists. Use -f flag to force addition."
                            )
                        }
                    }

                    AlreadyExistMode.FORCE -> {
                        BlockchainApi.addConfiguration(appConfig.pubKey, ctx, height, true, blockchainConfig, allowUnknownSigners, validate)
                    }

                    AlreadyExistMode.IGNORE -> {
                        if (!BlockchainApi.addConfiguration(appConfig.pubKey, ctx, height, false, blockchainConfig, allowUnknownSigners, validate))
                            println("Blockchain configuration of chainId $chainId at height $height already exists")
                    }
                }
            } catch (e: MissingPeerInfoException) {
                throw CliException(e.message + " Please add node with command peerinfo-add or set flag --allow-unknown-signers.")
            } catch (e: BadDataException) {
                throw CliException("Bad configuration format.")
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
                        throw CliException("Peer info with pubkey $pubKey already exists. Use -f to force update")
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
}
