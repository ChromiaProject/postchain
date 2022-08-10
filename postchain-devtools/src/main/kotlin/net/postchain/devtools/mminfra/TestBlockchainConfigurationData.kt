package net.postchain.devtools.mminfra

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDictionary

class TestBlockchainConfigurationData {
    private val m = mutableMapOf<String, Gtv>()
    fun getDict(): GtvDictionary {
        return GtvDictionary.build(m)
    }

    fun setValue(key: String, value: Gtv) {
        m[key] = value
    }
}