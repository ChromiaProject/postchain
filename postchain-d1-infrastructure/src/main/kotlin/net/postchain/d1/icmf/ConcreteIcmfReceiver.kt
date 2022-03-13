// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1.icmf

class ConcreteIcmfReceiver(
        val routes: Set<RoutingRule>
): IcmfReceiver {

    val localPipes = mutableMapOf<Long, LocalIcmfPipe>()

    override fun getRelevantPipes(): List<IcmfPipe> {
        return localPipes.values.toList()
    }
}