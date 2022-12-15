package net.postchain.client.core

import net.postchain.gtv.mapper.Name
import net.postchain.gtv.mapper.Nullable

class TxDetail(
        @Name("rid") val rid: ByteArray,
        @Name("hash") val hash: ByteArray,
        @Name("data") @Nullable val data: ByteArray?
)
