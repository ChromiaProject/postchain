package net.postchain.containers.bpm

import net.postchain.containers.bpm.fs.FileSystem
import java.nio.file.Path

internal interface ContainerInitializer {

    /**
     * Creates container root directory and node-config.properties file in it
     */
    fun initContainerWorkingDir(fs: FileSystem, container: PostchainContainer): Path?
}
