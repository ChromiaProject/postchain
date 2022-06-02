package net.postchain.eif

import net.postchain.gtv.Gtv
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Event

object GtvToEventMapper {

    fun map(gtv: Gtv): Event {
        val eventDict = gtv.asDict()
        val eventName = eventDict["name"]!!.asString()

        val eventTypes = eventDict["inputs"]!!.asArray().map {
            val inputDict = it.asDict()
            val typeName = inputDict["type"]!!.asString()
            val indexed = inputDict["indexed"]!!.asBoolean()

            TypeReference.makeTypeReference(typeName, indexed, false)
        }

        return Event(eventName, eventTypes)
    }
}