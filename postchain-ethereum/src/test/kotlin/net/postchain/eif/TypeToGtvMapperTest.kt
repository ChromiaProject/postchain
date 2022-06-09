package net.postchain.eif

import assertk.assert
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import net.postchain.common.hexStringToByteArray
import net.postchain.gtv.GtvBigInteger
import net.postchain.gtv.GtvInteger
import org.junit.jupiter.api.Test
import org.web3j.abi.datatypes.*
import org.web3j.abi.datatypes.generated.*
import java.math.BigInteger
import kotlin.math.pow

class TypeToGtvMapperTest {

    @Test
    fun `Test all primitive types`() {
        val addressHex = "0xe8907542ee0c73ae2ceed2d407c50d5e5bde33b1"
        val addressToGtv = TypeToGtvMapper.map(Address(addressHex))
        assert(addressToGtv.asByteArray().contentEquals(addressHex.substring(2).hexStringToByteArray())).isTrue()

        val booleanToGtv = TypeToGtvMapper.map(Bool(true))
        assert(booleanToGtv.asBoolean()).isTrue()

        val byteArray = ByteArray(32)
        val bytesToGtv = TypeToGtvMapper.map(Bytes32(byteArray))
        assert(byteArray.contentEquals(bytesToGtv.asByteArray())).isTrue()

        val maxInt = BigInteger.TWO.pow(255) - BigInteger.ONE
        val intToGtv = TypeToGtvMapper.map(Int(maxInt))
        assert(intToGtv.asBigInteger()).isEqualTo(maxInt)

        val maxUint = BigInteger.TWO.pow(256) - BigInteger.ONE
        val uintToGtv = TypeToGtvMapper.map(Uint(maxUint))
        assert(uintToGtv.asBigInteger()).isEqualTo(maxUint)

        val stringToGtv = TypeToGtvMapper.map(Utf8String("TEST"))
        assert(stringToGtv.asString()).isEqualTo("TEST")

        val array = StaticArray2(Uint::class.java, Uint(BigInteger.ONE), Uint(BigInteger.TWO))
        val arrayGtv = TypeToGtvMapper.map(array).asArray()
        assert(arrayGtv[0].asBigInteger()).isEqualTo(BigInteger.ONE)
        assert(arrayGtv[1].asBigInteger()).isEqualTo(BigInteger.TWO)
    }

    @Test
    fun `Test dynamic types`() {
        val array = DynamicArray(Uint::class.java, Uint(BigInteger.ONE), Uint(BigInteger.TWO))
        val arrayGtv = TypeToGtvMapper.map(array).asArray()
        assert(arrayGtv[0].asBigInteger()).isEqualTo(BigInteger.ONE)
        assert(arrayGtv[1].asBigInteger()).isEqualTo(BigInteger.TWO)

        val bytes = ByteArray(64)
        val bytesGtv = TypeToGtvMapper.map(DynamicBytes(bytes))
        assert(bytes.contentEquals(bytesGtv.asByteArray()))
    }

    @Test
    fun `Test integer conversion`() {
        val uint56Max = 2.0.pow(56.0).toLong() - 1
        val uint56Gtv = TypeToGtvMapper.map(Uint56(uint56Max))
        assert(uint56Gtv is GtvInteger).isTrue()
        assert(uint56Gtv.asInteger()).isEqualTo(uint56Max)

        val int64Gtv = TypeToGtvMapper.map(Int64(Long.MAX_VALUE))
        assert(int64Gtv is GtvInteger).isTrue()
        assert(int64Gtv.asInteger()).isEqualTo(Long.MAX_VALUE)

        val uint64Max = BigInteger.TWO.pow(64) - BigInteger.ONE
        val uint64tv = TypeToGtvMapper.map(Uint64(uint64Max))
        assert(uint64tv is GtvBigInteger).isTrue()
        assert(uint64tv.asBigInteger()).isEqualTo(uint64Max)

        val int72Max = BigInteger.TWO.pow(72) - BigInteger.ONE
        val int72Gtv = TypeToGtvMapper.map(Int72(int72Max))
        assert(int72Gtv is GtvBigInteger).isTrue()
        assert(int72Gtv.asBigInteger()).isEqualTo(int72Max)
    }
}