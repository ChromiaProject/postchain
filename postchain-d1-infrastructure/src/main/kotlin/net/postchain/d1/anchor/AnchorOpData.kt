package net.postchain.d1.anchor

import mu.KLogging
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.common.BlockchainRid
import net.postchain.gtx.data.OpData

/**
 * This data class hides the ordering of the [OpData] arguments to the outside world
 * Can do primitive validation of [OpData] too.
 */
data class AnchorOpData(
        val blockRid: ByteArray,
        val headerData: BlockHeaderData,
        val witness: ByteArray
) {

    companion object : KLogging() {

        /**
         * Does first part of validation, and hands over a "decoded" [AnchorOpData] for further validation
         *
         * @param op is what we should validate/decode
         * @return is a simple DTO or null if decode failed
         */
        fun validateAndDecodeOpData(op: OpData): AnchorOpData? {
            if (AnchorSpecialTxExtension.OP_BLOCK_HEADER != op.opName) {
                logger.info("Invalid spcl operation: Expected op name ${AnchorSpecialTxExtension.OP_BLOCK_HEADER} got ${op.opName}.")
                return null
            }

            if (op.args.size != 3) {
                logger.info("Invalid spcl operation: Expected 3 arg but got ${op.args.size}.")
                return null
            }

            return try {
                val blockRid = op.args[0].asByteArray()
                val header = BlockHeaderData.fromGtv(op.args[1])
                val rawWitness = op.args[2].asByteArray()

                if (header.getHeight() < 0) { // Ok, pretty stupid check, but why not
                    logger.error(
                            "Someone is trying to anchor a block for blockchain: " +
                                    "${BlockchainRid(header.getBlockchainRid()).toHex()} at height = ${header.getHeight()} (which is impossible!). "
                    )
                    return null
                }

                AnchorOpData(blockRid, header, rawWitness)
            } catch (e: RuntimeException) {
                logger.info("Invalid spcl operation: Error: ${e.message}")
                null // We don't really care what's wrong, just log it and return null
            }
        }
    }
}
