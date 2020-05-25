package net.postchain.base.gtv

import net.postchain.base.SECP256K1CryptoSystem
import net.postchain.base.merkle.Hash
import net.postchain.common.toHex
import net.postchain.gtv.*
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import java.io.Serializable

data class RowData(val id: GtvInteger, val tableName: GtvString, val data: GtvArray, val ignoreIndex: GtvArray = GtvArray(arrayOf())): Comparable<RowData>, Serializable {
    @Transient
    private var cryptoSystem = SECP256K1CryptoSystem()

    @Transient
    private var merkleHashCalculator = GtvMerkleHashCalculator(cryptoSystem)

    fun toGtv(): GtvArray {
        return GtvFactory.gtv(id, tableName, data, ignoreIndex)
    }

    fun toHashGtv(): GtvArray {
        if (ignoreIndex.isNull() || ignoreIndex.getSize() == 0) {
            return toGtv()
        }
        val a = mutableListOf<Gtv>()
        for (i in 0 until data.getSize()) {
            var ignore = false
            for (j in 0 until ignoreIndex.getSize()) {
                if (ignoreIndex[j].asInteger().toInt() == i) {
                    ignore = true
                    break
                }
            }
            if (!ignore) {
                a.add(data[i])
            }
        }
        return GtvFactory.gtv(id, tableName, GtvArray(a.toTypedArray()))
    }

    fun toHash(): Hash {
        return toHashGtv().merkleHash(merkleHashCalculator)
    }

    fun toHex(): String {
        return toHashGtv().merkleHash(merkleHashCalculator).toHex()
    }

    override fun compareTo(other: RowData): Int {
        return COMPARATOR.compare(this, other)
    }

    // To initialize transient variables when reading object from file
    private fun readResolve(): Any {
        this.cryptoSystem = SECP256K1CryptoSystem()
        this.merkleHashCalculator = GtvMerkleHashCalculator(cryptoSystem)
        return this
    }

    companion object {
        fun fromGtv(gtv: GtvArray): RowData {
            return RowData(
                    gtv[0] as GtvInteger,
                    gtv[1] as GtvString,
                    gtv[2] as GtvArray,
                    gtv[3] as GtvArray
            )
        }

        private val COMPARATOR =
                Comparator.comparingLong<RowData> { it.id.asInteger() }
    }
}