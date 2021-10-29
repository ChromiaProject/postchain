package net.postchain.containers.bpm

import java.nio.file.Path

internal interface ContainerInitializer {

    fun getContainerWorkingDir(containerName: ContainerName): Path

    fun initContainerWorkingDir(container: PostchainContainer)

    /**
     * TODO: [POS-129]: Add kdoc
     */
    fun initContainerChainWorkingDir(chain: Chain): ContainerChainDir

    /**
     * TODO: [POS-129]: Add kdoc
     */
    fun createContainerNodeConfig(container: PostchainContainer, containerDir: Path)

    /**
     * TODO: [POS-129]: Add kdoc
     */
    fun createPeersConfig(container: PostchainContainer, containerDir: Path)

    /**
     * TODO: [POS-129]: Add kdoc
     */
    fun rmChainWorkingDir(chain: Chain): Boolean
}
