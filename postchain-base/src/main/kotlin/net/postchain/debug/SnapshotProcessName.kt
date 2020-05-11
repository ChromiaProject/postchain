package net.postchain.debug

import net.postchain.base.BlockchainRid
import net.postchain.devtools.PeerNameHelper

class SnapshotProcessName(val pubKey: String, val blockchainRID: BlockchainRid) {
    override fun toString(): String = "[${PeerNameHelper.peerName(pubKey)}]/[${blockchainRID.toShortHex()}]"
}