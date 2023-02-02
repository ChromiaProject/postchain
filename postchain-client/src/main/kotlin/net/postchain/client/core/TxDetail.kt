package net.postchain.client.core

import net.postchain.common.types.WrappedByteArray
import net.postchain.gtv.mapper.Name
import net.postchain.gtv.mapper.Nullable

data class TxDetail(
        @Name("rid") val rid: WrappedByteArray,
        @Name("hash") val hash: WrappedByteArray,
        @Name("data") @Nullable val data: WrappedByteArray?
)
