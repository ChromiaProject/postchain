package net.postchain.debug

import com.google.gson.JsonElement
import net.postchain.common.BlockchainRid

interface NodeDiagnosticContext : MutableMap<DiagnosticProperty, DiagnosticValue> {
    fun format(): JsonElement

    fun hasBlockchainErrors(blockchainRid: BlockchainRid): Boolean
    fun blockchainErrorQueue(blockchainRid: BlockchainRid): DiagnosticQueue
    fun blockchainData(blockchainRid: BlockchainRid): DiagnosticData
    fun blockchainData(): Map<BlockchainRid, DiagnosticData>
    fun removeBlockchainData(blockchainRid: BlockchainRid?): DiagnosticData?
    fun clearBlockchainData()
    fun blockchainBlockStats(blockchainRid: BlockchainRid): DiagnosticQueue
}
