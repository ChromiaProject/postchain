// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtx.data

import net.postchain.common.BlockchainRid
import net.postchain.gtv.Gtv
import net.postchain.gtx.GtxBody
import net.postchain.gtx.GtxOp
import java.util.*

data class OpData(val opName: String, val args: Array<Gtv>) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as OpData

        if (opName != other.opName) return false
        if (!Arrays.equals(args, other.args)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = opName.hashCode()
        result = 31 * result + Arrays.hashCode(args)
        return result
    }
}

class ExtOpData(val opName: String,
                val opIndex: Int,
                val args: Array<out Gtv>,
                val blockchainRID: BlockchainRid,
                val signers: Array<ByteArray>,
                val operations: Array<OpData> ) {

    companion object {

        fun build(op: GtxOp, opIndex: Int, body: GtxBody): ExtOpData {
            return ExtOpData(op.name, opIndex, op.args, body.blockchainRid, body.signers.toTypedArray(), body.operations.map { it.toOpData() }.toTypedArray())
        }
    }
}
