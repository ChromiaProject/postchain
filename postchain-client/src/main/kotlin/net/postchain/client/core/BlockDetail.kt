package net.postchain.client.core

import net.postchain.common.types.WrappedByteArray
import net.postchain.gtv.mapper.Name

data class BlockDetail(
        @Name("rid") val rid: WrappedByteArray,
        @Name("prevBlockRID") val prevBlockRID: WrappedByteArray,
        @Name("header") val header: WrappedByteArray,
        @Name("height") val height: Long,
        @Name("transactions") val transactions: List<TxDetail>,
        @Name("witness") val witness: WrappedByteArray,
        @Name("timestamp") val timestamp: Long
)
