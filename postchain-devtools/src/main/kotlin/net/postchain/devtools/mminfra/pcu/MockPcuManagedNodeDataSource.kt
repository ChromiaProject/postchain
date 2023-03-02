package net.postchain.devtools.mminfra.pcu

import mu.KLogging
import net.postchain.common.BlockchainRid
import net.postchain.devtools.mminfra.MockManagedNodeDataSource

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
    override fun getPendingBlockchainConfiguration(blockchainRid: BlockchainRid, height: Long): ByteArray? {
        logger.info { "getPendingBlockchainConfiguration(${blockchainRid.toShortHex()}, $height)" }
        return bridToConfigs[blockchainRid]!![height]?.second
                ?.also {
                    pendingConfigs[height] = false
                    logger.info { "Config found" }
                }
    }

    override fun isPendingBlockchainConfigurationApplied(blockchainRid: BlockchainRid, height: Long, configHash: ByteArray): Boolean {
        // TODO use configHash
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