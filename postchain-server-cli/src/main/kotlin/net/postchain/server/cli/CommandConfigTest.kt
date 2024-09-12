package net.postchain.server.cli

import com.github.ajalt.clikt.core.PrintMessage
import net.postchain.config.app.AppConfig
import net.postchain.config.app.ConfigTest
import java.util.ServiceLoader


class CommandConfigTest : CommandRunServerBase("config-test", "Test if provided configuration is valid.") {
    private val nodeConfigFile by nodeConfigOption()

    override fun run() {
        val appConfig = AppConfig.fromPropertiesFileOrEnvironment(nodeConfigFile)
        val result = ServiceLoader.load(ConfigTest::class.java)
                .stream()
                .map { it.get() }
                .map { it.test(appConfig) }
                .toList().sum()
        if (result != 0)
            throw PrintMessage("Configuration test failed!", true)
    }
}

