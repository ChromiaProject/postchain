// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtv.gtvml

import jakarta.xml.bind.JAXBContext
import jakarta.xml.bind.JAXBElement
import jakarta.xml.bind.Marshaller
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvBigInteger
import net.postchain.gtv.GtvByteArray
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvInteger
import net.postchain.gtv.GtvNull
import net.postchain.gtv.GtvString
import net.postchain.gtv.gtxml.ArrayType
import net.postchain.gtv.gtxml.DictType
import net.postchain.gtv.gtxml.ObjectFactory
import java.io.StringWriter
import java.io.Writer

object GtvMLEncoder {

    private val objectFactory = ObjectFactory()
    private val jaxbContext = JAXBContext.newInstance(ObjectFactory::class.java.packageName)

    /**
     * Encodes [Gtv] into XML format.
     *
     * If any [GtvString] contains [character not allowed in XML](https://www.w3.org/TR/xml/#NT-Char),
     * it will be escaped with `&#nn;` but produce ill-formed XML output.
     */
    fun encodeXMLGtv(gtv: Gtv): String = with(StringWriter()) {
        encodeXML(gtv, this, strict = false)
        toString()
    }

    /**
     * Encodes [Gtv] into XML format.
     *
     * @throws IllegalArgumentException if any [GtvString] contains
     * [character not allowed in XML](https://www.w3.org/TR/xml/#NT-Char)
     */
    fun encodeXMLGtvStrict(gtv: Gtv): String = with(StringWriter()) {
        encodeXML(gtv, this, strict = true)
        toString()
    }

    /**
     * Encodes [Gtv] into XML format.
     *
     * If any [GtvString] contains [character not allowed in XML](https://www.w3.org/TR/xml/#NT-Char)
     * it will be escaped with `&#nn;` but produce ill-formed XML output if [strict] is false,
     * or throw [IllegalArgumentException] if [strict] is true.
     *
     * @param gtv  GTV to encode
     * @param out  where to write XML output
     * @param strict how to handle [GtvString]s containing [character not allowed in XML](https://www.w3.org/TR/xml/#NT-Char)
     *
     * @throws IllegalArgumentException if any [GtvString] contains
     * [character not allowed in XML](https://www.w3.org/TR/xml/#NT-Char) and [strict] is true
     */
    fun encodeXML(gtv: Gtv, out: Writer, strict: Boolean = false) {
        val marshaller = jaxbContext.createMarshaller()
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)
        marshaller.setProperty("org.glassfish.jaxb.marshaller.CharacterEscapeHandler", GtvMLEscapeHandler(strict))
        marshaller.marshal(encodeGTXMLValueToJAXBElement(gtv), out)
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
