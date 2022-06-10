package net.postchain.eif

import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.hexStringToByteArray
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Bool
import org.web3j.abi.datatypes.BytesType
import org.web3j.abi.datatypes.Type
import org.web3j.abi.datatypes.Uint
import org.web3j.abi.datatypes.Utf8String

object TypeToGtvMapper {

    fun map(type: Type<*>): Gtv {
        return when (type) {
            is org.web3j.abi.datatypes.Array<*> -> gtv(type.value.map(::map))
            is Address -> gtv(type.value.substring(2).hexStringToByteArray())
            is Bool -> gtv(type.value)
            is BytesType -> gtv(type.value)
            is org.web3j.abi.datatypes.Int -> if (type.bitSize <= 64) gtv(type.value.longValueExact()) else gtv(type.value)
            is Uint -> if (type.bitSize < 64) gtv(type.value.longValueExact()) else gtv(type.value)
            is Utf8String -> gtv(type.value)
            else -> throw ProgrammerMistake("Unexpected type: ${type::class}")
        }
    }

}