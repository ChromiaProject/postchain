package net.postchain.containers.bpm

import java.nio.file.Path

data class ContainerChainDir(
    val containerDir: Path,
    val chainDir: Path
) {

    @Deprecated("Deprecated")
    fun resolveContainerFilename(filename: String): String = containerDir.resolve(filename).toString()

    fun resolveChainFilename(filename: String): String = chainDir.resolve(filename).toString()

}