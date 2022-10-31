package net.postchain.client.core

import com.google.gson.annotations.JsonAdapter

class BlockDetail(
        @JsonAdapter(ByteArrayTypeAdapter::class)
        val rid: ByteArray,
        @JsonAdapter(ByteArrayTypeAdapter::class)
        val prevBlockRID: ByteArray,
        @JsonAdapter(ByteArrayTypeAdapter::class)
        val header: ByteArray,
        val height: Long,
        val transactions: List<TxDetail>,
        @JsonAdapter(ByteArrayTypeAdapter::class)
        val witness: ByteArray,
        val timestamp: Long
)
