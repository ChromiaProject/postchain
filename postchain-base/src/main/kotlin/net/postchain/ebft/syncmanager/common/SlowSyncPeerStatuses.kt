package net.postchain.ebft.syncmanager.common

import net.postchain.core.NodeRid

class SlowSyncPeerStatuses(val params: SyncParameters) : AbstractPeerStatuses<KnownState>() {

    override fun stateOf(peerId: NodeRid): KnownState {
        return statuses.computeIfAbsent(peerId) { KnownState(params) }
    }
}