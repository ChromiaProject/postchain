package net.postchain.eif

import assertk.assert
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import net.postchain.common.hexStringToByteArray
import org.junit.jupiter.api.Test
import org.web3j.abi.datatypes.*
import org.web3j.abi.datatypes.generated.Bytes32
import java.math.BigInteger

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
    }

    @Test
    fun `Test dynamic types`() {
        val array = DynamicArray(Uint::class.java, Uint(BigInteger.ONE), Uint(BigInteger.TWO))
        val arrayGtv = TypeToGtvMapper.map(array).asArray()
        assert(arrayGtv[0].asBigInteger()).isEqualTo(BigInteger.ONE)
        assert(arrayGtv[1].asBigInteger()).isEqualTo(BigInteger.TWO)

        val arrayOfArray = DynamicArray(DynamicArray::class.java, array, array)
        val arrayOfArrayGtv = TypeToGtvMapper.map(arrayOfArray)
        for (subArray in arrayOfArrayGtv.asArray()) {
            assert(subArray.asArray()[0].asBigInteger()).isEqualTo(BigInteger.ONE)
            assert(subArray.asArray()[1].asBigInteger()).isEqualTo(BigInteger.TWO)
        }

        val bytes = ByteArray(64)
        val bytesGtv = TypeToGtvMapper.map(DynamicBytes(bytes))
        assert(bytes.contentEquals(bytesGtv.asByteArray()))
    }
}