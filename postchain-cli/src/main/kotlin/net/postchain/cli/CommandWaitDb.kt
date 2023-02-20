// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import net.postchain.cli.util.nodeConfigOption
import net.postchain.config.app.AppConfig

class CommandWaitDb : CliktCommand(name = "wait-db", help = "Block until successfully connected to db [ default retry times: 5, interval: 1000 millis ]") {

    private val nodeConfigFile by nodeConfigOption()

    private val retryTimes by option("-rt", "--retry-times", help = "Number of retries").int().default(50)

    private val retryInterval by option("-ri", "--retry-interval", help = "Retry interval (ms)").long().default(1000L)


    override fun run() {
        val appConfig = AppConfig.fromPropertiesFileOrEnvironment(nodeConfigFile)
        CliExecution.waitDb(retryTimes, retryInterval, appConfig)
    }

}
