// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import mu.KLogging
import net.postchain.cli.util.debugOption
import net.postchain.cli.util.nodeConfigOption
import net.postchain.cli.util.portOption
import net.postchain.config.app.AppConfig
import net.postchain.server.PostchainServer
import org.apache.commons.lang3.builder.ToStringBuilder
import org.apache.commons.lang3.builder.ToStringStyle

class CommandRunServer : CliktCommand(name = "run-server", help = "Start postchain server") {

    companion object : KLogging()

    private val nodeConfigFile by nodeConfigOption()

    private val debug by debugOption()

    private val port by portOption().help("Port for the server").default(50051)


    override fun run() {
        println("Server is started with: " + ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE))

        PostchainServer(AppConfig.fromPropertiesFile(nodeConfigFile), false, debug, port)
            .apply {
                start()
                blockUntilShutdown()
            }
    }
}
