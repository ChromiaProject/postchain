package net.postchain.containers.bpm

import mu.KLogging
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.managed.DirectoryDataSource
import java.lang.Thread.sleep
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class BlockchainReplicator(
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
    private val process: Thread = thread(name = processName, start = false) { main() }
    private val started = AtomicBoolean(false)
    private val running = AtomicBoolean(false)
    private val done = AtomicBoolean(false)

    fun start() {
        if (done.get()) throw ProgrammerMistake("BlockchainReplicator process is already done")

        if (!started.getAndSet(true)) {
            running.set(true)
            process.start()
        } else throw ProgrammerMistake("BlockchainReplicator process is already started")
    }

    private fun main() {
        try {
            while (running.get()) {
                sleep(1000)
                val upToHeight0 = upToHeight

                // require src container is running and healthy
                val srcContainer = ensureContainer(srcChain.containerName, ContainerRole.SOURCE) ?: continue

                // require dst container is running and healthy
                val dstContainer = ensureContainer(dstChain.containerName, ContainerRole.DESTINATION) ?: continue

                val currentUpToHeight = if (upToHeight0 == -1L) {
                    val srcLastBlockHeight = srcContainer.getBlockchainLastBlockHeight(chainId)
                    if (srcLastBlockHeight == -1L) {
                        logger.error { "Source blockchain has no blocks: srcLastBlockHeight = -1L" }
                        continue
                    } else {
                        logger.error { "Source chain lastBlockHeight: $srcLastBlockHeight" }
                    }
                    srcLastBlockHeight
                } else {
                    upToHeight0
                }

                val dstLastBlockHeight = dstContainer.getBlockchainLastBlockHeight(chainId)
                logger.error { "Destination chain lastBlockHeight: $dstLastBlockHeight" }

                if (currentUpToHeight <= dstLastBlockHeight) {
                    logger.error { "Source blockchain has no blocks: upToHeight = $currentUpToHeight, dstLastBlockHeight = $dstLastBlockHeight" }
                    if (upToHeight0 != -1L) break else continue
                }

                // Importing configs
                replicateConfigurations(currentUpToHeight, dstLastBlockHeight, dstContainer)

                // Importing blocks
                val replicated = replicateBlocks(currentUpToHeight, dstLastBlockHeight, srcContainer, dstContainer)
                if (!replicated && upToHeight0 != -1L) break
            }
        } catch (e: Exception) {
            logger.error(e) { "BlockchainReplicator process $processName stopped unexpectedly" }
            running.set(false)
        } finally {
            done.set(true)
        }
    }

    private fun ensureContainer(name: ContainerName, role: ContainerRole): PostchainContainer? {
        val container = postchainContainerSupplier(name)
        val roleStr = role.name.lowercase().replaceFirstChar(Char::titlecase)

        if (container == null) {
            logger.error { "$roleStr container is not launched" }
            return null
        }

        if (!container.isSubnodeHealthy()) {
            logger.error { "$roleStr container is not ready" }
            return null
        }

        return container
    }

    private fun replicateConfigurations(upToHeight: Long, dstLastBlockHeight: Long, dstContainer: PostchainContainer) {
        var cur = dstLastBlockHeight
        while (true) {
            val next = directoryDataSource.findNextConfigurationHeight(blockchainRid.data, cur)
            logger.error { "Next config found at height $next" }
            if (next == null || next > upToHeight) break
            val config = directoryDataSource.getConfiguration(blockchainRid.data, next)
            if (config == null) {
                logger.error { "Can't load config at height $next" }
                break
            }
            logger.error { "Config at height $next fetched from D1" }
            dstContainer.addBlockchainConfiguration(chainId, next, config)
            logger.error { "Config at height $next added to the destination container/chain" }
            cur = next
        }
    }

    private fun replicateBlocks(upToHeight: Long, dstLastBlockHeight: Long, srcContainer: PostchainContainer, dstContainer: PostchainContainer): Boolean {
        val newBlocks = dstLastBlockHeight + 1..upToHeight
        return if (newBlocks.isEmpty()) {
            logger.error { "Source chain has no new blocks" }
            false
        } else {
            newBlocks.forEach { height ->
                val block = srcContainer.exportBlock(chainId, height)
                logger.error { "Block at height $height exported from source container/chain" }
                dstContainer.importBlock(chainId, block)
                logger.error { "Block at height $height imported to destination container/chain" }
            }
            logger.error { "Blockchain replication succeeded, $newBlocks blocks are imported" }
            true
        }
    }

    fun isDone() = upToHeight != 1L && done.get()

    fun shutdown() {
        running.set(false)
        if (!process.isAlive) return
        logger.debug { "Shutting down process $processName" }
        process.join()
    }
}