package net.postchain.containers.bpm

import net.postchain.containers.bpm.fs.FileSystem
import java.nio.file.Path

internal interface ContainerInitializer {

    /**
     * TODO: [POS-129]: Add kdoc
     */
    fun initContainerWorkingDir(fs: FileSystem, container: PostchainContainer): Path?

    /**
     * TODO: [POS-129]: Add kdoc
     */
    fun createContainerNodeConfig(container: PostchainContainer, containerDir: Path)

}
