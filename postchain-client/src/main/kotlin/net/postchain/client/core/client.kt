// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.client.core

import net.postchain.common.BlockchainRid
import net.postchain.crypto.SigMaker

interface PostchainNodeResolver {
    fun getNodeURL(blockchainRID: BlockchainRid): String
}

class DefaultSigner(val sigMaker: SigMaker, val pubkey: ByteArray)
