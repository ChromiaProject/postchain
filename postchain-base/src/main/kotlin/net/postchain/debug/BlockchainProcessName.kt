// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.debug

import net.postchain.base.BlockchainRid
import net.postchain.devtools.NameHelper

class BlockchainProcessName(val pubKey: String, val blockchainRid: BlockchainRid) {

    override fun toString(): String = NameHelper.blockchainProcessName(pubKey, blockchainRid)

}