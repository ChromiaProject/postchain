package net.postchain.api.rest.model

import net.postchain.core.TransactionInfoExt

class DecodedTransactionInfoExt(
        blockRID: ByteArray,
        blockHeight: Long,
        blockHeader: ByteArray,
        witness: ByteArray,
        timestamp: Long,
        txRID: ByteArray,
        txHash: ByteArray,
        txData: ByteArray?,
) : TransactionInfoExt(blockRID, blockHeight, blockHeader, witness, timestamp, txRID, txHash, txData) {
    companion object {
        @JvmStatic
        fun build(txInfo: TransactionInfoExt): DecodedTransactionInfoExt {
            return DecodedTransactionInfoExt(
                    txInfo.blockRID,
                    txInfo.blockHeight,
                    txInfo.blockHeader,
                    txInfo.witness,
                    txInfo.timestamp,
                    txInfo.txRID,
                    txInfo.txHash,
                    txInfo.txData,
            )
        }
    }
}

