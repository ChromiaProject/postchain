// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.cli

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import mu.KLogging
import net.postchain.config.app.AppConfig
import net.postchain.server.rpc.PostchainServer
import org.apache.commons.lang3.builder.ToStringBuilder
import org.apache.commons.lang3.builder.ToStringStyle

@Parameters(commandDescription = "Start postchain server")
class CommandRunServer : Command {

    companion object : KLogging()
    @Parameter(
        names = ["-nc", "--node-config"],
        description = "Configuration file of node (.properties file)")
    private var nodeConfigFile = ""

    @Parameter(
            names = ["--debug"],
            description = "Enables diagnostic info on the /_debug REST endpoint",
    )
    private var debug = false

    @Parameter(
        names = ["--port"],
        description = "Enables diagnostic info on the /_debug REST endpoint",
    )
    private var port = 50051

    private val NODE_CONFIG_FILE = "node-config.properties"
    private val BLOCKCHAIN_DIR = "blockchains"

    override fun key(): String = "run-server"

    private val lastHeights = mutableMapOf<Long, Long>() // { chainId -> height }

    override fun execute(): CliResult {
        println("run-auto-node will be executed with options: " +
                ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE))


        val server = PostchainServer(AppConfig.fromPropertiesFile(nodeConfigFile), false, debug, port)
        server.start()
        server.blockUntilShutdown()
        return Ok()
    }

}