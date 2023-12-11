package net.postchain.containers.bpm

import mu.KLogging
import net.postchain.containers.bpm.docker.DockerTools
import net.postchain.containers.bpm.fs.FileSystem
import net.postchain.containers.bpm.rpc.SubnodeAdminClient
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.crypto.PrivKey
import net.postchain.gtv.Gtv
import net.postchain.managed.DirectoryDataSource
import java.time.Clock
import java.util.concurrent.atomic.AtomicBoolean

class DefaultPostchainContainer(
        val containerNodeConfig: ContainerNodeConfig,
        val dataSource: DirectoryDataSource,
        override val containerName: ContainerName,
        override var containerPortMapping: MutableMap<Int, Int>,
        override var state: ContainerState,
        private val subnodeAdminClient: SubnodeAdminClient,
        override var containerId: String? = null,
        val clock: Clock = Clock.systemUTC()
) : PostchainContainer {

    companion object : KLogging()

    private val processes = mutableMapOf<Long, ContainerBlockchainProcess>()
    private var initialized = false

    // NB: Resources are per directoryContainerName, not nodeContainerName
    @Volatile
    override var resourceLimits = dataSource.getResourceLimitForContainer(containerName.directoryContainer)

    override val readOnly = AtomicBoolean(false)
    private var lastUpdated = clock.millis()

    override fun shortContainerId(): String? {
        return DockerTools.shortContainerId(containerId)
    }

    override fun findProcesses(chainId: Long): ContainerBlockchainProcess? {
        return processes[chainId]
    }

    override fun getAllChains(): Set<Long> = processes.keys

    override fun getAllProcesses(): Map<Long, ContainerBlockchainProcess> = processes

    override fun getStoppedChains(): Set<Long> {
        return processes.keys
                .filter { !subnodeAdminClient.isBlockchainRunning(it) }
                .toSet()
    }

    override fun startProcess(process: ContainerBlockchainProcess): Boolean {
        subnodeAdminClient.startBlockchain(process.chainId, process.blockchainRid).also {
            if (it) processes[process.chainId] = process
        }
        setLastUpdated()
        return true
    }

    override fun removeProcess(chainId: Long): ContainerBlockchainProcess? = processes.remove(chainId).also {
        setLastUpdated()
    }

    override fun terminateProcess(chainId: Long): ContainerBlockchainProcess? {
        return processes.remove(chainId)?.also {
            subnodeAdminClient.stopBlockchain(chainId)
            setLastUpdated()
        }
    }

    override fun terminateAllProcesses(): Set<Long> {
        return getAllChains().toSet().onEach(::terminateProcess)
    }

    override fun getBlockchainLastBlockHeight(chainId: Long): Long {
        return subnodeAdminClient.getBlockchainLastBlockHeight(chainId)
    }

    override fun addBlockchainConfiguration(chainId: Long, height: Long, config: ByteArray) {
        if (height == 0L) {
            subnodeAdminClient.initializeBlockchain(chainId, config)
        } else {
            subnodeAdminClient.addBlockchainConfiguration(chainId, height, config)
        }
        setLastUpdated()
    }

    override fun exportBlock(chainId: Long, height: Long): Gtv {
        return subnodeAdminClient.exportBlock(chainId, height)
    }

    override fun importBlock(chainId: Long, blockData: Gtv) {
        subnodeAdminClient.importBlock(chainId, blockData)
        setLastUpdated()
    }

    override fun start() {
        state = ContainerState.RUNNING
        subnodeAdminClient.connect()
        setLastUpdated()
    }

    override fun reset() {
        subnodeAdminClient.disconnect()
        state = ContainerState.STARTING
        initialized = false
        setLastUpdated()
    }

    override fun stop() {
        state = ContainerState.STOPPING
        subnodeAdminClient.shutdown()
        setLastUpdated()
    }

    override fun isIdle(): Boolean {
        return processes.isEmpty() && (System.currentTimeMillis() - lastUpdated > 5 * 60 * 1000)
    }

    override fun isSubnodeHealthy() = subnodeAdminClient.isSubnodeHealthy()

    override fun initializePostchainNode(privKey: PrivKey): Boolean = if (!initialized) {
        val success = subnodeAdminClient.initializePostchainNode(privKey)
        if (success) initialized = true
        setLastUpdated()
        success
    } else true

    override fun updateResourceLimits(): Boolean {
        val oldResourceLimits = resourceLimits
        val newResourceLimits = dataSource.getResourceLimitForContainer(containerName.directoryContainer)
        return if (newResourceLimits != oldResourceLimits) {
            resourceLimits = newResourceLimits
            setLastUpdated()
            true
        } else {
            false
        }
    }

    override fun checkResourceLimits(fileSystem: FileSystem): Boolean {
        val readOnlyBeforeCheck = readOnly.get()
        fileSystem.getCurrentLimitsInfo(containerName, resourceLimits)?.let {
            readOnly.set(it.spaceUsedMB + containerNodeConfig.minSpaceQuotaBufferMB >= it.spaceHardLimitMB)
            if (readOnlyBeforeCheck != readOnly.get()) {
                if (readOnly.get())
                    logger.warn("Space used is too close to hard limit. Switching to read only mode. (used space: ${it.spaceUsedMB}MB, space buffer: ${containerNodeConfig.minSpaceQuotaBufferMB}MB, hard space limit: ${it.spaceHardLimitMB}MB)")
                else
                    logger.info("Space used is no longer too close to hard limit. (used space: ${it.spaceUsedMB}MB, space buffer: ${containerNodeConfig.minSpaceQuotaBufferMB}MB, hard space limit: ${it.spaceHardLimitMB}MB)")
                setLastUpdated()
            }
        }
        return readOnlyBeforeCheck == readOnly.get()
    }

    private fun setLastUpdated() {
        lastUpdated = clock.millis()
    }
}
