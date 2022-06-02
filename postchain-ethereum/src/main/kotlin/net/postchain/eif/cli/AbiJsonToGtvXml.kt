package net.postchain.eif.cli

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.gtvml.GtvMLEncoder
import net.postchain.gtv.make_gtv_gson

object AbiJsonToGtvXml {
    fun abiJsonToXml(json: String, eventNames: List<String>): String {
        val gson = make_gtv_gson()
        val gtv = gson.fromJson(json, Gtv::class.java)
        val events = gtv.asArray()
            .filter { it.asDict()["type"]!!.asString() == "event" && eventNames.contains(it.asDict()["name"]!!.asString()) }

        return GtvMLEncoder.encodeXMLGtv(gtv(events))
    }
}
