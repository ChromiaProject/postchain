// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.managed

import mu.KLogging
import net.postchain.base.data.DatabaseAccess
import net.postchain.config.blockchain.AbstractBlockchainConfigurationProvider
import net.postchain.config.blockchain.ManualBlockchainConfigurationProvider
import net.postchain.core.EContext

/**
 * A complicated mix of local (=DB) reads and external (=Chain0 API) reads.
 * Currently we allow local configuration to override the external configuration, but this is tricky
 * (see below for more).
 * If you need to use local configuration, the recommended way is to switch to manual mode (instead doing the
 * override in managed mode).
 */
class PcuManagedBlockchainConfigurationProvider : ManagedBlockchainConfigurationProvider() {

   override fun isDataSourceReady(): Boolean {
        return dataSource.pcuIsPendingConfigApproved()
    }
}