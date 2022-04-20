package net.postchain.devtools.utils.configuration

import net.postchain.config.app.AppConfig

val AppConfig.activeChainIds: Array<String>
    get() {
        return if (config.containsKey("activechainids"))
            config.getStringArray("activechainids")
        else
            emptyArray()
    }