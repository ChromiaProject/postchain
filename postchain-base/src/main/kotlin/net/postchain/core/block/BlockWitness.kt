package net.postchain.core.block

import net.postchain.gtv.GtvByteArray
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.mapper.ToGtv

/**
 * Witness is a generalization over signatures.
 * Block-level witness is something which proves that block is valid and properly authorized.
 */
interface BlockWitness : ToGtv {
    //    val blockRID: ByteArray
    fun getRawData(): ByteArray

    override fun toGtv(): GtvByteArray = gtv(getRawData())
}
