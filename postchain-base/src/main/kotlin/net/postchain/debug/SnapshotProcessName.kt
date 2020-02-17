package net.postchain.debug

import net.postchain.devtools.PeerNameHelper

class SnapshotProcessName(val pubKey: String) {
    override fun toString(): String = "[${PeerNameHelper.peerName(pubKey)}]"
}