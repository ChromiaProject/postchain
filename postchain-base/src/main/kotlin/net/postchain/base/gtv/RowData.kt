package net.postchain.base.gtv

import net.postchain.base.SECP256K1CryptoSystem
import net.postchain.base.merkle.Hash
import net.postchain.common.toHex
import net.postchain.gtv.*
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import java.io.Serializable

data class RowData(val id: GtvInteger, val tableName: GtvString, val data: GtvArray): Comparable<RowData>, Serializable {
    @Transient
    private var cryptoSystem = SECP256K1CryptoSystem()

    @Transient
    private var merkleHashCalculator = GtvMerkleHashCalculator(cryptoSystem)

    fun toGtv(): GtvArray {
        return GtvFactory.gtv(id, tableName, data)
    }

    fun toHash(): Hash {
        return toGtv().merkleHash(merkleHashCalculator)
    }

    fun toHex(): String {
        return toGtv().merkleHash(merkleHashCalculator).toHex()
    }

    override fun compareTo(other: RowData): Int {
        return COMPARATOR.compare(this, other)
    }

    companion object {
        fun fromGtv(gtv: GtvArray): RowData {
            return RowData(
                    gtv[0] as GtvInteger,
                    gtv[1] as GtvString,
                    gtv[2] as GtvArray
            )
        }

        private val COMPARATOR =
                Comparator.comparingLong<RowData> { it.id.asInteger() }
    }
}