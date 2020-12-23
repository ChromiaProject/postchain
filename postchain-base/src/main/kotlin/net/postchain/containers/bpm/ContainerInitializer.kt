package net.postchain.containers.bpm

import net.postchain.managed.ManagedNodeDataSource
import java.nio.file.Path

interface ContainerInitializer {

    /**
     *
     */
    fun createContainerWorkingDir(process: ContainerBlockchainProcess): Pair<Path, Path>

    /**
     *
     */
    fun createContainerNodeConfig(process: ContainerBlockchainProcess, containerCwd: Path)

    /**
     *
     */
    fun createContainerChainConfigs(dataSource: ManagedNodeDataSource, process: ContainerBlockchainProcess, chainDir: Path)

}
