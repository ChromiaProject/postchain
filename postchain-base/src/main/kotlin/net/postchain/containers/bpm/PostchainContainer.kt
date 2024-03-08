package net.postchain.containers.bpm

import net.postchain.containers.bpm.fs.FileSystem
import net.postchain.crypto.PrivKey
import net.postchain.gtv.Gtv
import java.util.concurrent.atomic.AtomicBoolean

enum class ContainerState {
    STARTING, RUNNING, STOPPING
}

interface PostchainContainer {
    val containerName: ContainerName
    var state: ContainerState
    var containerId: String?
    val resourceLimits: ContainerResourceLimits
    val containerPortMapping: MutableMap<Int, Int>
    val readOnly: AtomicBoolean

    fun shortContainerId(): String?
    fun findProcesses(chainId: Long): ContainerBlockchainProcess?
    fun getAllChains(): Set<Long>
    fun getAllProcesses(): Map<Long, ContainerBlockchainProcess>
    fun getStoppedChains(): Set<Long>
    fun startProcess(process: ContainerBlockchainProcess): Boolean
    fun removeProcess(chainId: Long): ContainerBlockchainProcess?
    fun terminateProcess(chainId: Long): ContainerBlockchainProcess?
    fun terminateAllProcesses(): Set<Long>
    fun start()
    fun reset()
    fun stop()
    fun isIdle(): Boolean
    fun isSubnodeHealthy(): Boolean
    fun initializePostchainNode(privKey: PrivKey): Boolean

    /** @return `true` if there are updates */
    fun updateResourceLimits(): Boolean

    /** @return `false` if a limit is reached and state has changed */
    fun checkResourceLimits(fileSystem: FileSystem): Boolean
    fun getBlockchainLastBlockHeight(chainId: Long): Long
    fun addBlockchainConfiguration(chainId: Long, height: Long, config: ByteArray)
    fun exportBlocks(chainId: Long, fromHeight: Long, blockCountLimit: Int, blocksSizeLimit: Int): List<Gtv>
    fun importBlocks(chainId: Long, blockData: List<Gtv>): Long
}
