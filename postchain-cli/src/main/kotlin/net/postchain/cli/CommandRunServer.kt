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
        description = "Configuration file of node (.properties file)"
    )
    private var nodeConfigFile = ""

    @Parameter(
        names = ["--debug"],
        description = "Enables diagnostic info on the /_debug REST endpoint",
    )
    private var debug = false

    @Parameter(
        names = ["--port"],
        description = "Port for the server",
    )
    private var port = 50051

    override fun key(): String = "run-server"

    override fun execute(): CliResult {
        println("Server is started with: " + ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE))

        PostchainServer(AppConfig.fromPropertiesFile(nodeConfigFile), false, debug, port).apply {
            start()
            blockUntilShutdown()
        }
        return Ok()
    }

}