// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.cli

import com.beust.jcommander.Parameters
import net.postchain.containers.bpm.CONTAINER_ZFS_INIT_SCRIPT
import java.io.File

@Parameters(commandDescription = "Generates container-zfs-init-script.sh file")
class CommandGenerateContainerZfsInitScript : Command {

    override fun key(): String = "generate-container-zfs-init-script"

    override fun execute(): CliResult {
        val body = """
            #!/bin/sh
            
            # create fs
            zfs create ${'$'}1
            
            # set disk quota and reservation
            if [ ${'$'}2 -gt 0 ]
            then
               zfs set quota=${'$'}2m ${'$'}1
               zfs set reservation=50m ${'$'}1
            fi
            
        """.trimIndent()
        File(CONTAINER_ZFS_INIT_SCRIPT).writeText(body)
        return Ok()
    }
}
