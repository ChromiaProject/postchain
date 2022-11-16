package net.postchain.config.node

import net.postchain.common.BlockchainRid
import net.postchain.config.app.AppConfig

open class ManagedNodeConfig(appConfig: AppConfig) : NodeConfig(appConfig) {
    open val locallyConfiguredBlockchainsToReplicate: Set<BlockchainRid> = setOf()
}