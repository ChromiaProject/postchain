// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.debug

import net.postchain.common.BlockchainRid

data class BlockchainProcessName(val pubKey: String, val blockchainRid: BlockchainRid) {

    override fun toString(): String {
        // In tests, last part of brid is used to identify chains, so include that in the shortened name.
        return "[${pubKey.take(8)}/${blockchainRid.toHex().take(2)}:${blockchainRid.toHex().takeLast(4)}]"
    }

}