package net.postchain.devtools.mminfra.pcu

import mu.KLogging
import net.postchain.common.BlockchainRid
import net.postchain.common.wrap
import net.postchain.crypto.PubKey
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.devtools.mminfra.MockManagedNodeDataSource
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash
import net.postchain.managed.PendingBlockchainConfiguration

class MockPcuManagedNodeDataSource : MockManagedNodeDataSource() {

    companion object : KLogging()

    private val pendingConfigs = mutableMapOf<Long, Boolean>()
    private val hashCalculator: GtvMerkleHashCalculator = GtvMerkleHashCalculator(Secp256K1CryptoSystem())

    @Synchronized
    fun approveConfig(height: Long) {
        pendingConfigs[height] = true
        logger.info { "Config $height approved" }
    }

    @Synchronized
    override fun getPendingBlockchainConfiguration(blockchainRid: BlockchainRid, height: Long): List<PendingBlockchainConfiguration> {
        logger.info { "getPendingBlockchainConfiguration(${blockchainRid.toShortHex()}, $height)" }
        val config = bridToConfigs[blockchainRid]!![height]
        return if (config != null) {
            val fullConfig = GtvDecoder.decodeGtv(config.second).asDict().toMutableMap()
            val signers = fullConfig["signers"]!!.asArray().map { PubKey(it.asByteArray()) }
            fullConfig.remove("signers")
            val baseConfig = GtvFactory.gtv(fullConfig)
            val configHash = GtvFactory.gtv(fullConfig).merkleHash(hashCalculator)
            pendingConfigs.computeIfAbsent(height) { false }
            logger.info { "Config found" }
            listOf(PendingBlockchainConfiguration(baseConfig, configHash.wrap(), signers, height))
        } else listOf()
    }
}
