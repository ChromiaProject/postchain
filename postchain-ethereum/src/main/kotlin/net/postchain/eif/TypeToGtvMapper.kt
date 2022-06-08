package net.postchain.eif

import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.hexStringToByteArray
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Type
import org.web3j.abi.datatypes.Uint
import java.math.BigInteger

object TypeToGtvMapper {

    fun map(type: Type<*>): Gtv {
        return when (val rawValue = type.value) {
            is BigInteger -> {
                if (type is Uint && type.bitSize < 64) {
                    gtv(rawValue.longValueExact())
                } else if (type is org.web3j.abi.datatypes.Int && type.bitSize <= 64) {
                    gtv(rawValue.longValueExact())
                } else {
                    gtv(rawValue)
                }
            }
            is Boolean -> gtv(rawValue)
            is ByteArray -> gtv(rawValue)
            is String -> {
                if (type is Address) {
                    gtv(rawValue.substring(2).hexStringToByteArray())
                } else {
                    gtv(rawValue)
                }
            }
            is List<*> -> gtv(rawValue.map {
                if (it is Type<*>) {
                    map(it)
                } else {
                    throw ProgrammerMistake("Invalid class")
                }
            })
            else -> throw ProgrammerMistake("Type has unsupported raw type: ${rawValue::class}")
        }
    }

}