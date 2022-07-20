package net.postchain.containers.bpm

import java.nio.file.Path

internal interface ContainerInitializer {

    /**
     * TODO: [POS-129]: Add kdoc
     */
    fun initContainerWorkingDir(fs: FileSystem, container: PostchainContainer): Path?

    /**
     * TODO: [POS-129]: Add kdoc
     */
    fun initContainerChainWorkingDir(fs: FileSystem, chain: Chain): Path?

    /**
     * TODO: [POS-129]: Add kdoc
     */
    fun createContainerNodeConfig(container: PostchainContainer, containerDir: Path)

    /**
     * TODO: [POS-129]: Add kdoc
     */
    fun removeContainerChainDir(fs: FileSystem, chain: Chain): Boolean
}
