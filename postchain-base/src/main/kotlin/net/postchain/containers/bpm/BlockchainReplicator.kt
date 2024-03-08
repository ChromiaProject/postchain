package net.postchain.containers.bpm

import com.google.common.util.concurrent.ThreadFactoryBuilder
import mu.KLogging
import net.postchain.common.types.WrappedByteArray
import net.postchain.ebft.syncmanager.common.BlockPacker.MAX_PACKAGE_CONTENT_BYTES
import net.postchain.managed.DirectoryDataSource
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class BlockchainReplicator(
        val rid: WrappedByteArray,
        val srcChain: Chain,
        val dstChain: Chain,
        var upToHeight: Long,
        val directoryDataSource: DirectoryDataSource,
        val postchainContainerSupplier: (ContainerName) -> PostchainContainer?
) {

    companion object : KLogging()

    enum class ContainerRole { SOURCE, DESTINATION }

    private val chainId = srcChain.chainId
    private val blockchainRid = srcChain.brid
    private val processName = "blockchain-replicator-chainId-$chainId"
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(
            ThreadFactoryBuilder().setNameFormat(processName).build()
    )
    private val done = AtomicBoolean(false)

    init {
        executor.scheduleWithFixedDelay(::jobHandler, 5000, 2000, TimeUnit.MILLISECONDS)
    }

    private fun jobHandler() {
        try {
            val upToHeight0 = upToHeight

            // require src container is running and healthy
            val srcContainer = ensureContainer(srcChain.containerName, ContainerRole.SOURCE) ?: return

            // require dst container is running and healthy
            val dstContainer = ensureContainer(dstChain.containerName, ContainerRole.DESTINATION) ?: return

            val currentUpToHeight = if (upToHeight0 == -1L) {
                val srcLastBlockHeight = srcContainer.getBlockchainLastBlockHeight(chainId)
                if (srcLastBlockHeight == -1L) {
                    logger.debug { "Source blockchain has no blocks: srcLastBlockHeight = -1L" }
                    return
                } else {
                    logger.debug { "Source chain lastBlockHeight: $srcLastBlockHeight" }
                }
                srcLastBlockHeight
            } else {
                upToHeight0
            }

            val dstLastBlockHeight = dstContainer.getBlockchainLastBlockHeight(chainId)
            logger.debug { "Destination chain lastBlockHeight: $dstLastBlockHeight" }

            if (currentUpToHeight <= dstLastBlockHeight) {
                val log = "Source blockchain has no new blocks: upToHeight = $currentUpToHeight, dstLastBlockHeight = $dstLastBlockHeight"
                if (upToHeight0 != -1L) {
                    logger.info { log }
                    done()
                } else {
                    logger.info { "$log. Waiting for new blocks to replicate." }
                }
                return
            }

            // Importing configs
            replicateConfigurations(currentUpToHeight, dstLastBlockHeight, dstContainer)

            // Importing blocks
            val replicated = replicateBlocks(currentUpToHeight, dstLastBlockHeight, srcContainer, dstContainer)
            if (!replicated && upToHeight0 != -1L) done()
        } catch (e: Exception) {
            logger.error(e) { "$processName has got an error and will be renewed" }
        }
    }

    private fun ensureContainer(name: ContainerName, role: ContainerRole): PostchainContainer? {
        val container = postchainContainerSupplier(name)
        val roleStr = role.name.lowercase().replaceFirstChar(Char::titlecase)

        if (container == null) {
            logger.debug { "$roleStr container is not launched" }
            return null
        }

        if (!container.isSubnodeHealthy()) {
            logger.debug { "$roleStr container is not ready" }
            return null
        }

        return container
    }

    private fun replicateConfigurations(upToHeight: Long, dstLastBlockHeight: Long, dstContainer: PostchainContainer) {
        var cur = dstLastBlockHeight
        while (true) {
            val next = directoryDataSource.findNextConfigurationHeight(blockchainRid.data, cur)
            logger.debug { "Next config found at height $next" }
            if (next == null || next > upToHeight) break
            val config = directoryDataSource.getConfiguration(blockchainRid.data, next)
            if (config == null) {
                logger.debug { "Can't load config at height $next" }
                break
            }
            logger.debug { "Config at height $next fetched from D1" }
            dstContainer.addBlockchainConfiguration(chainId, next, config)
            logger.debug { "Config at height $next added to the destination container/chain" }
            cur = next
        }
    }

    fun replicateBlocks(upToHeight: Long, dstLastBlockHeight: Long, srcContainer: PostchainContainer, dstContainer: PostchainContainer): Boolean {
        val newBlocks = dstLastBlockHeight + 1..upToHeight
        return if (newBlocks.isEmpty()) {
            logger.info { "Source chain has no new blocks" }
            false
        } else {
            logger.info { "Block replication started, $newBlocks blocks will be imported" }

            var currentHeight = newBlocks.first
            while (currentHeight <= newBlocks.last) {

                val blockCountLimit = newBlocks.last.toInt() - currentHeight.toInt() + 1
                logger.info { "Replicate block range $currentHeight - ${newBlocks.last} with max block limit $blockCountLimit and size limit $MAX_PACKAGE_CONTENT_BYTES" }

                val blocks = srcContainer.exportBlocks(chainId, currentHeight, blockCountLimit, MAX_PACKAGE_CONTENT_BYTES)
                val blocksSize = blocks.sumOf { it.nrOfBytes() }
                logger.info { "Exported ${blocks.size} blocks ($currentHeight - ${currentHeight + blocks.size - 1}, $blocksSize bytes) from source container/chain" }

                val importUpToHeight = dstContainer.importBlocks(chainId, blocks)
                logger.info { "Imported ${blocks.size} blocks to height ${importUpToHeight}to destination container/chain" }

                currentHeight = importUpToHeight + 1
            }

            logger.info { "Blockchain replication succeeded, $newBlocks blocks are imported" }
            true
        }
    }

    private fun done() {
        executor.shutdown()
        done.set(true)
        logger.info { "Replication job $processName is done" }
    }

    fun isDone() = upToHeight != -1L && done.get()
}