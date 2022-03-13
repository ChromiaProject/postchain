// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1.icmf

import net.postchain.base.Storage

class IcmfController(storage: Storage) {
    val localDispatcher = IcmfLocalDispatcher(storage)

    fun registerReceiverChain(chainID: Long, rules: Set<RoutingRule>): IcmfReceiver {
        val recv = ConcreteIcmfReceiver(rules)
        if (ClusterAnchorRoutingRule in rules)
            localDispatcher.connectReceiver(chainID, recv)
        return recv
    }
}