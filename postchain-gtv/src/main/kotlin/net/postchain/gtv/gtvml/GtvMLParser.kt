// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtv.gtvml

import jakarta.xml.bind.JAXBContext
import jakarta.xml.bind.JAXBElement
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvBigInteger
import net.postchain.gtv.GtvByteArray
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvInteger
import net.postchain.gtv.GtvNull
import net.postchain.gtv.GtvString
import net.postchain.gtv.GtvType
import net.postchain.gtv.GtvType.*
import net.postchain.gtv.gtxml.ArrayType
import net.postchain.gtv.gtxml.DictType
import net.postchain.gtv.gtxml.ObjectFactory
import net.postchain.gtv.gtxml.ParamType
import net.postchain.gtx.gtxml.GtvTypeOf
import net.postchain.gtx.gtxml.component1
import net.postchain.gtx.gtxml.component2
import net.postchain.gtx.gtxml.isParam
import java.io.StringReader
import java.math.BigInteger

object GtvMLParser {

    private val jaxbContext = JAXBContext.newInstance(ObjectFactory::class.java.packageName)

    /**
     * Parses XML represented as string into [Gtv] and resolves params ('<param />') by [params] map
     */
    fun parseGtvML(xml: String, params: Map<String, Gtv> = mapOf()): Gtv {
        return parseJAXBElementToGtvML(
                parseJaxbElement(xml), params)
    }

    fun parseJAXBElementToGtvML(jaxbElement: JAXBElement<*>, params: Map<String, Gtv>): Gtv {
        val (qName, value) = jaxbElement

        return if (isParam(qName)) {
            parseParam(value as ParamType, params)
        } else {
            when (GtvTypeOf(qName)) {
                NULL -> GtvNull
                STRING -> GtvString(value as String)
                INTEGER -> GtvInteger(value as Long)
                BIGINTEGER -> GtvBigInteger(value as BigInteger)
                BYTEARRAY -> GtvByteArray(value as ByteArray)
                ARRAY -> parseArrayGtvML(value as ArrayType, params)
                DICT -> parseDictGtvML(value as DictType, params)
            }
        }
    }

    private fun parseArrayGtvML(array: ArrayType, params: Map<String, Gtv>): GtvArray {
        val elements = array.elements.map { parseJAXBElementToGtvML(it, params) }
        return GtvArray(elements.toTypedArray())
    }

    private fun parseDictGtvML(dict: DictType, params: Map<String, Gtv>): GtvDictionary {
        val parsedDict = dict.entry.map {
            it.key to parseJAXBElementToGtvML(it.value, params)
        }.toMap()

        return GtvDictionary.build(parsedDict)
    }

    private fun parseParam(paramType: ParamType, params: Map<String, Gtv>): Gtv {
        val gtv = params[paramType.key]
                ?: throw IllegalArgumentException("Can't resolve param ${paramType.key}")

        if (paramType.type != null && GtvType.fromString(paramType.type) != gtv.type) {
            throw IllegalArgumentException("Incompatible types of <param> and Gtv: " +
                    "found: '${gtv.type}', required: '${GtvType.fromString(paramType.type)}'")
        }

        return gtv
    }

    private fun parseJaxbElement(xml: String) =
            jaxbContext.createUnmarshaller().unmarshal(StringReader(xml)) as JAXBElement<*>
}