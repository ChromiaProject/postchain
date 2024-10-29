package net.postchain.containers.infra

import com.github.dockerjava.api.model.LogConfig
import net.postchain.common.config.Config
import net.postchain.common.exception.UserMistake

data class DockerLogConfig(val driver: LogConfig.LoggingType? = null, val opts: Map<String, String> = mapOf()) : Config {
    companion object {
        fun fromStrings(driver: String, opts: String): DockerLogConfig? {
            if (driver.isBlank()) return null
            val optsMap = if (opts.isNotBlank()) {
                opts.split(";").associate { opt ->
                    val keyVal = opt.trim().split("=")
                    if (keyVal.size == 2) {
                        val key = keyVal[0].trim()
                        val value = keyVal[1].trim()
                        if (key.isNotBlank() && value.isNotBlank()) {
                            key to value
                        } else {
                            throw UserMistake("Invalid docker log options. Given driver=$driver opts=$opts currentKey=$key currentValue=$value")
                        }
                    } else {
                        throw UserMistake("Invalid docker log options. Given driver=$driver opts=$opts")
                    }
                }
            } else {
                mapOf()
            }

            if (driver.isNotEmpty()) {
                val loggingType = LogConfig.LoggingType.fromValue(driver)
                        ?: throw UserMistake("Invalid docker log option. Incorrect driver type: $driver")
                return DockerLogConfig(loggingType, optsMap)
            }

            return null
        }
    }
}