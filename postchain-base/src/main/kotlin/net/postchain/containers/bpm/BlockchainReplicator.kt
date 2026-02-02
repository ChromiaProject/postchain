package net.postchain.containers.bpm

import com.google.common.util.concurrent.ThreadFactoryBuilder
import mu.KLogging
import mu.withLoggingContext
import net.postchain.common.exception.UserMistake
import net.postchain.common.types.WrappedByteArray
import net.postchain.ebft.syncmanager.common.BlockPacker.MAX_BLOCKS_IN_PACKAGE
import net.postchain.ebft.syncmanager.common.BlockPacker.MAX_PACKAGE_CONTENT_BYTES
import net.postchain.logging.BLOCKCHAIN_RID_TAG
import net.postchain.logging.CHAIN_IID_TAG
import net.postchain.managed.DirectoryDataSource
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

class BlockchainReplicator(
        val migrationRid: WrappedByteArray,
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
    private val cancelled = AtomicBoolean(false)
    private val loggingContext = mapOf(
            CHAIN_IID_TAG to chainId.toString(),
            BLOCKCHAIN_RID_TAG to blockchainRid.toHex()
    )

    init {
        executor.scheduleWithFixedDelay(::jobHandler, 5000, 2000, TimeUnit.MILLISECONDS)
    }

    private fun jobHandler() {
        withLoggingContext(loggingContext) {
            logger.info { "Blockchain replication handler started: from $srcChain to $dstChain" }

            if (cancelled.get()) {
                logger.info { "Blockchain replication has been cancelled" }
                return
            }

            try {
                val upToHeight0 = upToHeight

                // require src container is running and healthy, and chain is running
                val srcContainer = ensureContainer(srcChain.containerName, ContainerRole.SOURCE) ?: return

                // require dst container is running and healthy, and chain is running
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
                logger.debug { "currentUpToHeight: $currentUpToHeight" }

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
            logger.info { "Blockchain replication handler finished: from $srcChain to $dstChain" }
        }
    }

    private fun ensureContainer(name: ContainerName, role: ContainerRole): PostchainContainer? {
        val container = postchainContainerSupplier(name)
        val roleStr = role.name.lowercase().replaceFirstChar(Char::titlecase)

        withLoggingContext(loggingContext) {

            if (container == null) {
                logger.debug { "$roleStr container is not launched" }
                return null
            }

            if (!container.isSubnodeHealthy()) {
                logger.debug { "$roleStr container is not ready" }
                return null
            }

            if (!container.isBlockchainRunning(chainId)) {
                logger.debug { "Chain $chainId in the $roleStr container is not running" }
                return null
            }
        }

        return container
    }

    private fun replicateConfigurations(upToHeight: Long, dstLastBlockHeight: Long, dstContainer: PostchainContainer) {
        withLoggingContext(loggingContext) {
            var cur = dstLastBlockHeight
            logger.info { "Configuration replication started from height $cur on the destination container" }
            while (true) {
                if (cancelled.get()) {
                    logger.info { "Blockchain replication has been cancelled" }
                    break
                }

                val next = directoryDataSource.findNextConfigurationHeight(blockchainRid.data, cur)
                when {
                    next == null -> {
                        logger.info { "No next config found for height $cur" }
                        break
                    }

                    next > upToHeight -> {
                        logger.info { "Next config is at height $next, but we are limited by height $upToHeight" }
                        break
                    }

                    else -> {
                        logger.info { "Next config found at height $next" }
                    }
                }

                val config = directoryDataSource.getConfiguration(blockchainRid.data, next)
                if (config == null) {
                    logger.warn { "Can't load config at height $next" }
                    break
                }
                logger.debug { "Config at height $next fetched from managed data source" }

                dstContainer.addBlockchainConfiguration(chainId, next, config)
                logger.info { "Config at height $next added to the destination container" }

                cur = next
            }
        }
    }

    fun replicateBlocks(upToHeight: Long, dstLastBlockHeight: Long, srcContainer: PostchainContainer, dstContainer: PostchainContainer): Boolean {
        withLoggingContext(loggingContext) {
            val newBlocks = dstLastBlockHeight + 1..upToHeight
            if (newBlocks.isEmpty()) {
                logger.info { "Source chain has no new blocks" }
                return false
            } else {
                logger.info { "Block replication started, $newBlocks blocks will be imported" }

                var currentHeight = newBlocks.first
                while (currentHeight <= newBlocks.last) {
                    if (cancelled.get()) {
                        logger.info { "Blockchain replication has been cancelled" }
                        return false
                    }

                    val blockCountLimit = min(newBlocks.last.toInt() - currentHeight.toInt() + 1, MAX_BLOCKS_IN_PACKAGE)
                    logger.info { "Replicate block range $currentHeight..${newBlocks.last} with max block limit $blockCountLimit and size limit $MAX_PACKAGE_CONTENT_BYTES" }

                    val blocks = srcContainer.exportBlocks(chainId, currentHeight, blockCountLimit, MAX_PACKAGE_CONTENT_BYTES)
                    val blocksSize = blocks.sumOf { it.nrOfBytes() }
                    logger.info { "Exported ${blocks.size} blocks ($currentHeight..${currentHeight + blocks.size - 1}, $blocksSize bytes) from source container/chain" }

                    var triesLeft = 3
                    while (triesLeft > 0) {
                        try {
                            logger.info { "Importing ${blocks.size} blocks at $currentHeight to destination container/chain..." }
                            val importUpToHeight = dstContainer.importBlocks(chainId, blocks)
                            logger.info { "Imported ${importUpToHeight - currentHeight + 1} blocks ($currentHeight..$importUpToHeight) to destination container/chain" }
                            currentHeight = importUpToHeight + 1
                            break
                        } catch (e: UserMistake) {
                            triesLeft--
                            logger.warn { "Import of ${blocks.size} blocks at $currentHeight failed: ${e.message}, ${if (triesLeft > 0) "retrying..." else "giving up"}" }
                        }
                    }
                }

                logger.info { "Blockchain replication succeeded, $newBlocks blocks are imported" }
                return true
            }
        }
    }

    private fun done() {
        withLoggingContext(loggingContext) {
            done.set(true)
            shutdown()
            logger.info { "Replication job $processName is done" }
        }
    }

    fun isDone() = upToHeight != -1L && done.get()

    fun cancel() {
        withLoggingContext(loggingContext) {
            cancelled.set(true)
            shutdown()
            logger.info { "Replication job $processName has been cancelled" }
        }
    }

    fun isCancelled() = cancelled.get()

    fun shutdown() {
        executor.shutdownNow()
        executor.awaitTermination(2000, TimeUnit.MILLISECONDS)
    }
}