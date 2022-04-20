package net.postchain.devtools.utils.configuration

import net.postchain.config.app.AppConfig

val AppConfig.activeChainIds: Array<String>
    get() {
        return if (containsKey("activechainids"))
            getStringArray("activechainids")
        else
            emptyArray()
    }