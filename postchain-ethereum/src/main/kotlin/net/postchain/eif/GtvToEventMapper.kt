package net.postchain.eif

import net.postchain.common.exception.UserMistake
import net.postchain.gtv.Gtv
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Event

/**
 * Maps gtv representations of JSON ABI specifications to web3j [Event] objects.
 * Does not support structs or multi dimension arrays
 */
object GtvToEventMapper {

    private val supportedTypes = listOf("address", "bool", "bytes", "int", "string", "uint")
    private val supportedTypesExpression = supportedTypes
        .joinToString("|", "^(", ")([0-9]{1,3})?(\\[\\])?\$")
        .toRegex()

    fun map(gtv: Gtv): Event {
        val eventDict = gtv.asDict()
        val eventName = eventDict["name"]!!.asString()

        val eventTypes = eventDict["inputs"]!!.asArray().map {
            val inputDict = it.asDict()
            val typeName = inputDict["type"]!!.asString()
            val matchResult = supportedTypesExpression.matchEntire(typeName)
            if (matchResult == null) {
                throwTypeError(typeName)
            } else {
                val numberMatchGroup = matchResult.groups[2]
                if (numberMatchGroup != null) {
                    val numberMatch = matchResult.groups[2]!!.value.toInt()
                    if (!validRange(matchResult.groups[1]!!.value, numberMatch)) {
                        throwTypeError(typeName)
                    }
                }
            }

            val indexed = inputDict["indexed"]!!.asBoolean()

            TypeReference.makeTypeReference(typeName, indexed, false)
        }

        return Event(eventName, eventTypes)
    }

    private fun validRange(type: String, number: Int): Boolean {
        return when (type) {
            "bytes" -> number in 1..32
            "int", "uint" -> number in 8..256 && number % 8 == 0
            else -> false
        }
    }

    private fun throwTypeError(typeName: String) {
        throw UserMistake("Unsupported type: $typeName")
    }
}