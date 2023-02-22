// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtx.gtxml

import jakarta.xml.bind.JAXBElement
import jakarta.xml.bind.annotation.adapters.HexBinaryAdapter
import net.postchain.gtv.gtxml.ObjectFactory
import javax.xml.namespace.QName


/**
 * [component1] function for [JAXBElement] class
 */
operator fun <T> JAXBElement<T>.component1(): QName = name

/**
 * [component2] function for [JAXBElement] class
 */
operator fun <T> JAXBElement<T>.component2(): T = value

/**
 * See comments in GTXMLValueEncodeScalarsTest
 */
fun ObjectFactory.createBytearrayElement(value: ByteArray): JAXBElement<String> {
    val marshaledValue = HexBinaryAdapter().marshal(value)
    // May be declaredType should be a byte[].class
    return JAXBElement(QName("", "bytea"), String::class.java, null, marshaledValue)
}

