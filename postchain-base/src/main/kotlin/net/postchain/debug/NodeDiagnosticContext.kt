package net.postchain.debug

import net.postchain.common.BlockchainRid

interface NodeDiagnosticContext : MutableMap<DiagnosticProperty, DiagnosticValue> {
    fun format(): String
    val blockchainDiagnosticData: MutableMap<BlockchainRid, DiagnosticData>

    fun blockchainErrorQueue(blockchainRid: BlockchainRid): DiagnosticQueue<String>
    fun blockchainData(blockchainRid: BlockchainRid): DiagnosticData
}
