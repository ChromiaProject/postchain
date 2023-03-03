package net.postchain.devtools.mminfra.pcu

import mu.KLogging
import net.postchain.common.BlockchainRid
import net.postchain.common.wrap
import net.postchain.devtools.mminfra.MockManagedNodeDataSource
import net.postchain.gtv.GtvNull
import net.postchain.managed.PendingBlockchainConfiguration

class MockPcuManagedNodeDataSource : MockManagedNodeDataSource() {

    companion object : KLogging()

    private val pendingConfigs = mutableMapOf<Long, Boolean>()
    private val loggedStates = mutableSetOf<String>()

    @Synchronized
    fun approveConfig(height: Long) {
        pendingConfigs[height] = true
        logger.info { "Config $height approved" }
    }

    @Synchronized
    override fun getPendingBlockchainConfiguration(blockchainRid: BlockchainRid, height: Long): PendingBlockchainConfiguration? {
        logger.info { "getPendingBlockchainConfiguration(${blockchainRid.toShortHex()}, $height)" }
        val config = bridToConfigs[blockchainRid]!![height]?.second
        return if (config != null) {
            pendingConfigs[height] = false
            logger.info { "Config found" }
            PendingBlockchainConfiguration(config.wrap(), GtvNull)
        } else null

    }

    override fun isPendingBlockchainConfigurationApplied(blockchainRid: BlockchainRid, height: Long, configHash: ByteArray): Boolean {
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