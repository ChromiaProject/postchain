package net.postchain.base.configuration

import net.postchain.common.types.WrappedByteArray

data class FaultyConfiguration(
        val configHash: WrappedByteArray,
        val reportAtHeight: Long
)
