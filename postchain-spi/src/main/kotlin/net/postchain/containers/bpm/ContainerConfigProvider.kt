package net.postchain.containers.bpm

import net.postchain.config.app.AppConfig

/**
 * Purpose of this interface is a way for extensions to inject node configuration into subnodes
 */
fun interface ContainerConfigProvider {

    /**
     * @return Mapping of {ENVIRONMENT VARIABLE -> CONFIGURATION VALUE}
     */
    fun getConfig(appConfig: AppConfig): Map<String, String>
}
