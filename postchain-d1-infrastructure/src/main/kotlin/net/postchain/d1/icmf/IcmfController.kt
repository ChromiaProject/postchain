// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1.icmf

import net.postchain.base.Storage
import net.postchain.common.exception.ProgrammerMistake

class IcmfController(storage: Storage) {
    val localDispatcher = IcmfLocalDispatcher(storage)

    fun createReceiver(chainID: Long, route: Route): IcmfReceiver<*, *, *> {
        return when (route) {
            is ClusterAnchorRoute -> {
                val recv = ClusterAnchorIcmfReceiver()
                localDispatcher.connectReceiver(chainID, recv)
                recv
            }
            else -> throw ProgrammerMistake("Unrecognized route")
        }
    }
}