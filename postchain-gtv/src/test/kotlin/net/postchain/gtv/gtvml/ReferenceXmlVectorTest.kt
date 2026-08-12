package net.postchain.gtv.gtvml

import net.postchain.common.hexStringToByteArray
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.VECTORS
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The XML half of the reference vectors, which live in postchain-gtv-min's test jar. GtvML stayed here because
 * its API exposes JAXB types, so this is where the generated expectations can be checked.
 */
class ReferenceXmlVectorTest {

    @Test
    fun matchesReferenceXml() {
        for (vector in VECTORS) {
            val gtv = GtvDecoder.decodeGtv(vector.der.hexStringToByteArray())
            assertEquals(vector.xml, GtvMLEncoder.encodeXMLGtv(gtv), "xml ${vector.name}")
        }
    }

    @Test
    fun reparsesReferenceXml() {
        for (vector in VECTORS.filterNot { it.xml.contains(ILL_FORMED) }) {
            val gtv = GtvDecoder.decodeGtv(vector.der.hexStringToByteArray())
            assertEquals(gtv, GtvMLParser.parseGtvML(vector.xml), "xml round trip ${vector.name}")
        }
    }

    private companion object {
        /**
         * A string holding a character XML forbids is escaped as `&#nn;` rather than rejected, which is
         * documented on [GtvMLEncoder.encodeXMLGtv] and leaves output no XML parser will take back.
         */
        private val ILL_FORMED = Regex("&#(\\d|1[0-9]|2[0-9]|3[01]);")
    }
}
