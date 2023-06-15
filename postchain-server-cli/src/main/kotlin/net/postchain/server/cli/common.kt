package net.postchain.server.cli

import net.postchain.PostchainNode
import net.postchain.StorageBuilder
import net.postchain.config.app.AppConfig
import net.postchain.config.node.NodeConfigurationProviderFactory
import org.apache.commons.configuration2.ex.ConfigurationException
import org.apache.commons.dbcp2.BasicDataSource
import java.io.File
import java.io.IOException
import java.lang.management.ManagementFactory
import java.sql.Connection
import java.sql.SQLException
import java.util.concurrent.TimeoutException

fun runNode(appConfig: AppConfig, chainIds: List<Long>) {
    with(PostchainNode(appConfig, wipeDb = false)) {
        chainIds.forEach {
            tryStartBlockchain(it)
        }
    }
}

fun waitDb(retryTimes: Int, retryInterval: Long, appConfig: AppConfig) {
    tryCreateBasicDataSource(appConfig)?.let { return } ?: if (retryTimes > 0) {
        Thread.sleep(retryInterval)
        waitDb(retryTimes - 1, retryInterval, appConfig)
    } else throw TimeoutException("Unable to connect to database")
}

private fun tryCreateBasicDataSource(appConfig: AppConfig): Connection? {
    return try {
        val storage = StorageBuilder.buildStorage(appConfig)
        NodeConfigurationProviderFactory.createProvider(appConfig, storage).getConfiguration()

        BasicDataSource().apply {
            addConnectionProperty("currentSchema", appConfig.databaseSchema)
            driverClassName = appConfig.databaseDriverclass
            url = appConfig.databaseUrl //?loggerLevel=OFF"
            username = appConfig.databaseUsername
            password = appConfig.databasePassword
            defaultAutoCommit = false
        }.connection
    } catch (e: SQLException) {
        null
    } catch (e: ConfigurationException) {
        throw CliException("Failed to read configuration")
    }
}

fun dumpPid() {
    val processName = ManagementFactory.getRuntimeMXBean().name
    val pid = processName.split("@")[0]
    try {
        File("postchain.pid").writeText(pid)
    } catch (e: IOException) { // might fail due to permission error in containers
        println("Postchain PID: $pid")
    }
}
