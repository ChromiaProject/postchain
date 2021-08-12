package net.postchain.containers.bpm

interface ContainerInitializer {

    /**
     * TODO: [POS-129]: Add kdoc
     */
    fun createContainerChainWorkingDir(chainId: Long, containerName: ContainerName): ContainerChainDir

    /**
     * TODO: [POS-129]: Add kdoc
     */
    fun createContainerNodeConfig(container: PostchainContainer, containerChainDir: ContainerChainDir)

    /**
     * TODO: [POS-129]: Add kdoc
     */
    fun createPeersConfig(container: PostchainContainer, containerChainDir: ContainerChainDir)

    /**
     * TODO: [POS-129]: Add kdoc
     */
    fun killContainerChainWorkingDir(chainId: Long, containerName: ContainerName)
}
