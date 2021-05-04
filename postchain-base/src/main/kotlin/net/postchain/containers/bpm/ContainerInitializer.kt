package net.postchain.containers.bpm

import java.nio.file.Path

interface ContainerInitializer {

    /**
     * TODO: [POS-129]: Add kdoc
     */
    fun createContainerWorkingDir(chainId: Long, containerName: String): Pair<Path, Path>

    /**
     * TODO: [POS-129]: Add kdoc
     */
    fun createContainerNodeConfig(container: PostchainContainer, containerDir: Path)

}
