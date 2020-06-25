// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.managed

import net.postchain.config.node.PeerInfoDataSource

interface ManagedNodeDataSource : PeerInfoDataSource {
    fun getPeerListVersion(): Long
    fun computeBlockchainList(): List<ByteArray>
    fun getConfiguration(blockchainRidRaw: ByteArray, height: Long): ByteArray?
    fun getConfigurations(blockchainRidRaw: ByteArray): Map<Long, ByteArray>
    fun findNextConfigurationHeight(blockchainRidRaw: ByteArray, height: Long): Long?
}
