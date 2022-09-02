package net.postchain.d1.anchor

import mu.KLogging
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.base.gtv.BlockHeaderDataFactory
import net.postchain.gtx.data.OpData

/**
 * This data class hides the ordering of the [OpData] arguments to the outside world
 * Can do primitive validation of [OpData] too.
 */
data class AnchorOpDataObject(
        val blockRid: ByteArray,
        val headerData: BlockHeaderData,
        val witness: ByteArray
) {

    companion object : KLogging() {

        /**
         * Does first part of validation, and hands over a "decoded" [AnchorOpDataObject] for further validation
         *
         * @param op is what we should validate/decode
         * @return is a simple DTO or null if decode failed
         */
        fun validateAndDecodeOpData(op: OpData): AnchorOpDataObject? {
            if (AnchorSpecialTxExtension.OP_BLOCK_HEADER != op.opName) {
                logger.info("Invalid spcl operation: Expected op name ${AnchorSpecialTxExtension.OP_BLOCK_HEADER} got ${op.opName}.")
                return null
            }

            if (op.args.size != 3) {
                logger.info("Invalid spcl operation: Expected 3 arg but got ${op.args.size}.")
                return null
            }

            return try {
                val gtvBlockRid = op.args[0]
                val gtvHeader = op.args[1]
                val gtvWitness = op.args[2]

                val blockRid = gtvBlockRid.asByteArray()
                val header = BlockHeaderDataFactory.buildFromGtv(gtvHeader)
                val rawWitness = gtvWitness.asByteArray()

                AnchorOpDataObject(blockRid, header, rawWitness)
            } catch (e: RuntimeException) {
                logger.info("Invalid spcl operation: Error: ${e.message}")
                null // We don't really care what's wrong, just log it and return null
            }
        }
    }
}
