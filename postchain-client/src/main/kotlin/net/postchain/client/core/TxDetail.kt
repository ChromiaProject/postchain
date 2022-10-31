package net.postchain.client.core

import com.google.gson.annotations.JsonAdapter

class TxDetail(
        @JsonAdapter(ByteArrayTypeAdapter::class)
        val rid: ByteArray,
        @JsonAdapter(ByteArrayTypeAdapter::class)
        val hash: ByteArray,
        @JsonAdapter(ByteArrayTypeAdapter::class)
        val data: ByteArray?
)
