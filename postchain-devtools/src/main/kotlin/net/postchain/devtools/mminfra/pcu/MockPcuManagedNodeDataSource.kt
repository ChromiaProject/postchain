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
    private val loggedStates = mutableSetOf<String>()
    private val hashCalculator: GtvMerkleHashCalculator = GtvMerkleHashCalculator(Secp256K1CryptoSystem())

    @Synchronized
    fun approveConfig(height: Long) {
        pendingConfigs[height] = true
        logger.info { "Config $height approved" }
    }

    @Synchronized
    override fun getPendingBlockchainConfiguration(blockchainRid: BlockchainRid, height: Long): PendingBlockchainConfiguration? {
        logger.info { "getPendingBlockchainConfiguration(${blockchainRid.toShortHex()}, $height)" }
        val config = bridToConfigs[blockchainRid]!![height]
        return if (config != null) {
            val fullConfig = GtvDecoder.decodeGtv(config.second).asDict().toMutableMap()
            val signers = fullConfig["signers"]!!.asArray().map { PubKey(it.asByteArray()) }
            fullConfig.remove("signers")
            val baseConfig = GtvFactory.gtv(fullConfig)
            val baseConfigHash = baseConfig.merkleHash(hashCalculator)
            pendingConfigs.computeIfAbsent(height) { false }
            logger.info { "Config found" }
            PendingBlockchainConfiguration(baseConfig, baseConfigHash.wrap(), signers)
        } else null

    }

    override fun isPendingBlockchainConfigurationApplied(blockchainRid: BlockchainRid, height: Long, baseConfigHash: ByteArray): Boolean {
        return when {
            !pendingConfigs.containsKey(height) -> {
                logNoDuplicates("No pending config $height")
                true
            }

            pendingConfigs[height] == true -> {
                logNoDuplicates("Config $height got approval")
                true
            }

            else -> {
                logNoDuplicates("Config $height is not approved")
                false
            }
        }
    }

    private fun logNoDuplicates(msg: String) {
        if (!loggedStates.contains(msg)) {
            logger.info(msg)
            loggedStates.add(msg)
        }
    }
}