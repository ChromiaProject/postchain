// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.cli

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.containers.infra.ContainerNodeConfig
import java.io.File

class CommandGenerateContainerZfsInitScript : CliktCommand(name = "generate-container-zfs-init-script", help = "Generates container-zfs-init-script.sh file") {

    override fun run() {
        val body = """
            #!/bin/sh
            
            # create fs
            zfs create -u ${'$'}1
            
            # set disk quota and reservation
            if [ ${'$'}2 -gt 0 ]
            then
               zfs set quota=${'$'}2m ${'$'}1
               zfs set reservation=50m ${'$'}1
            fi
            
        """.trimIndent()
        File(ContainerNodeConfig.DEFAULT_CONTAINER_ZFS_INIT_SCRIPT).writeText(body)
    }
}
