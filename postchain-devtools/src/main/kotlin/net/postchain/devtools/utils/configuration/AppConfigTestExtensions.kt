package net.postchain.devtools.utils.configuration

import net.postchain.config.app.AppConfig

// Note: This is only needed for tests
val AppConfig.activeChainIds: Array<String>
    get() {
        return if (containsKey("activechainids"))
            getStringArray("activechainids")
        else
            emptyArray()
    }