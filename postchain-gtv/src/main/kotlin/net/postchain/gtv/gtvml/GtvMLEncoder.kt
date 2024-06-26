// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtv.gtvml

import jakarta.xml.bind.JAXB
import jakarta.xml.bind.JAXBElement
import net.postchain.gtv.*
import net.postchain.gtv.gtxml.ArrayType
import net.postchain.gtv.gtxml.DictType
import net.postchain.gtv.gtxml.ObjectFactory
import java.io.StringWriter


object GtvMLEncoder {

    private val objectFactory = ObjectFactory()

    /**
     * Encodes [Gtv] into XML format
     */
    fun encodeXMLGtv(gtv: Gtv): String {
        return with(StringWriter()) {
            JAXB.marshal(encodeGTXMLValueToJAXBElement(gtv), this)
            toString()
        }
    }

    fun encodeGTXMLValueToJAXBElement(gtv: Gtv): JAXBElement<*> {
        return when (gtv) {
            /**
             * Note: null element will be equal to:
             *      `<null xsi:nil="true" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"/>`
             */
            is GtvNull -> objectFactory.createNull(null)
            is GtvString -> objectFactory.createString(gtv.string)
            is GtvInteger -> objectFactory.createInt(gtv.asInteger())
            is GtvBigInteger -> objectFactory.createBigint(gtv.asBigInteger())
            is GtvByteArray -> objectFactory.createBytea(gtv.bytearray) // See comments in GTXMLValueEncodeScalarsTest
            is GtvArray -> createArrayElement(gtv)
            is GtvDictionary -> createDictElement(gtv)
            else -> throw IllegalArgumentException("Unknown GTV type: {${gtv.type}")
        }
    }

    private fun createArrayElement(gtv: GtvArray): JAXBElement<ArrayType> {
        return with(objectFactory.createArrayType()) {
            gtv.array
                    .map(GtvMLEncoder::encodeGTXMLValueToJAXBElement)
                    .toCollection(this.elements)

            objectFactory.createArray(this)
        }
    }

    private fun createDictElement(gtv: GtvDictionary): JAXBElement<DictType> {
        return with(objectFactory.createDictType()) {
            gtv.dict.map { entry ->
                val entryType = objectFactory.createEntryType()
                entryType.key = entry.key
                entryType.value = encodeGTXMLValueToJAXBElement(entry.value)
                entryType
            }.toCollection(this.entry)

            objectFactory.createDict(this)
        }
    }
}
