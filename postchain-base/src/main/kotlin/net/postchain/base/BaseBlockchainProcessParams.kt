package net.postchain.base

import net.postchain.core.BlockchainProcessParams

data class BaseBlockchainProcessParams(
        override val syncEnabled: Boolean
) : BlockchainProcessParams
