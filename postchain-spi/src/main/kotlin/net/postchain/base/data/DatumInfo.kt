package net.postchain.base.data

import net.postchain.common.data.Hash

data class DatumInfo (
        val id: Long,
        val hash: Hash,
        val rawValue: ByteArray?
)
