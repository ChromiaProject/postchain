package net.postchain.integrationtest

import net.postchain.core.EContext
import net.postchain.gtx.SimpleGTXModule

class ShutdownTestModule : SimpleGTXModule<Unit>(Unit, mapOf(), mapOf()) {
    var hasShutdown = false

    override fun initializeDB(ctx: EContext) { }

    override fun shutdown() {
        hasShutdown = true
    }
}
