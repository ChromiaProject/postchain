package net.postchain.containers.bpm

enum class ContainerState {
    STARTING, RUNNING, STOPPING
}

interface PostchainContainer {
    val containerName: ContainerName
    var state: ContainerState
    var containerId: String?
    val resourceLimits: ContainerResourceLimits
    val containerPorts: ContainerPorts

    fun shortContainerId(): String?
    fun findProcesses(chainId: Long): ContainerBlockchainProcess?
    fun getAllChains(): Set<Long>
    fun getStoppedChains(): Set<Long>
    fun startProcess(process: ContainerBlockchainProcess): Boolean
    fun removeProcess(chainId: Long): ContainerBlockchainProcess?
    fun terminateProcess(chainId: Long): ContainerBlockchainProcess?
    fun terminateAllProcesses(): Set<Long>
    fun getBlockchainLastHeight(chainId: Long): Long
    fun start()
    fun stop()
    fun isEmpty(): Boolean
    fun isSubnodeHealthy(): Boolean

    /** @return `true` if there are updates */
    fun updateResourceLimits(): Boolean
}
