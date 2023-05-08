// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft.rest.contract

import net.postchain.common.toHex
import net.postchain.debug.DiagnosticQueue
import net.postchain.debug.DpNodeType
import net.postchain.ebft.NodeStatus

class StateNodeStatus(
        val pubKey: String,
        val type: String,
        val state: String,
        val height: Long? = null,
        val serial: Long? = null,
        val round: Long? = null,
        val blockRid: String? = null,
        val revolting: Boolean? = null,
        val error: String? = null
)

fun NodeStatus.toStateNodeStatus(pubKey: String, errorQueue: DiagnosticQueue<String>? = null): StateNodeStatus =
        StateNodeStatus(
                pubKey = pubKey,
                type = DpNodeType.NODE_TYPE_VALIDATOR.name,
                height = this.height,
                serial = this.serial,
                state = this.state.name,
                round = this.round,
                blockRid = this.blockRID?.toHex(),
                revolting = this.revolting,
                error = errorQueue?.value.toString()
        )

