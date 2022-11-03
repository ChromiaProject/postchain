package net.postchain.client.core

import net.postchain.gtv.mapper.Name

class BlockDetail(
        @Name("rid") val rid: ByteArray,
        @Name("prevBlockRID") val prevBlockRID: ByteArray,
        @Name("header") val header: ByteArray,
        @Name("height") val height: Long,
        @Name("transactions") val transactions: List<TxDetail>,
        @Name("witness") val witness: ByteArray,
        @Name("timestamp") val timestamp: Long
)
