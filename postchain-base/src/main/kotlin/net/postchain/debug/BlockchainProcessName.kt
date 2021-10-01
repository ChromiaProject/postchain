// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.debug

import net.postchain.core.BlockchainRid
import net.postchain.devtools.NameHelper

data class BlockchainProcessName(val pubKey: String, val blockchainRid: BlockchainRid) {

    override fun toString(): String = NameHelper.blockchainProcessName(pubKey, blockchainRid)

}