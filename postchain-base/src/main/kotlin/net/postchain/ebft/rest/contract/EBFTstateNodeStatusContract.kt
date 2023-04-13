// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft.rest.contract

import net.postchain.api.rest.json.JsonFactory
import net.postchain.common.toHex
import net.postchain.debug.DiagnosticQueue
import net.postchain.ebft.NodeState
import net.postchain.ebft.NodeStatus

class EBFTstateNodeStatusContract(
        val state: NodeState,
        val height: Long,
        val serial: Long,
        val round: Long,
        val blockRid: String?,
        val revolting: Boolean,
        val error: String? = null
)

fun NodeStatus.serialize(errorQueue: DiagnosticQueue<String>? = null): String {
    val gson = JsonFactory.makeJson()
    val contract = EBFTstateNodeStatusContract(
            height = this.height,
            serial = this.serial,
            state = this.state,
            round = this.round,
            blockRid = this.blockRID?.toHex(),
            revolting = this.revolting,
            error = errorQueue?.value.toString()
    )
    return gson.toJson(contract)
}
