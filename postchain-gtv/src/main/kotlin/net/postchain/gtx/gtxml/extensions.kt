// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtx.gtxml

import net.postchain.gtv.GtvType
import javax.xml.namespace.QName

/**
 * Returns [GtvType] object correspondent to [QName]
 */
fun GtvTypeOf(qname: QName): GtvType = GtvType.fromString(qname.localPart)

/**
 * Returns `true` if [QName] corresponds `<param />` tag and `false` otherwise
 */
fun isParam(qname: QName): Boolean =
        "param".equals(qname.localPart, true)