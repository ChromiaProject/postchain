package net.postchain.containers.bpm

import net.postchain.containers.bpm.fs.FileSystem
import net.postchain.crypto.PrivKey
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
    fun getBlockchainLastHeight(chainId: Long): Long
    fun start()
    fun reset()
    fun stop()
    fun isEmpty(): Boolean
    fun isSubnodeHealthy(): Boolean
    fun initializePostchainNode(privKey: PrivKey): Boolean
    /** @return `true` if there are updates */
    fun updateResourceLimits(): Boolean
    /** @return `false` if a limit is reached and state has changed */
    fun checkResourceLimits(fileSystem: FileSystem): Boolean
}
