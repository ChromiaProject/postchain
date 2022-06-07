package net.postchain.eif

import assertk.assert
import assertk.assertions.isEqualTo
import net.postchain.common.exception.UserMistake
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.gtvml.GtvMLParser
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.web3j.abi.datatypes.*
import org.web3j.abi.datatypes.generated.Uint256

class GtvToEventMapperTest {

    @Test
    fun `Map ERC20 standard events`() {
        val abiXml = javaClass.getResource("/net/postchain/eif/cli/erc20_abi_to_gtv.xml").readText()
        val abiGtv = GtvMLParser.parseGtvML(abiXml)

        val events = abiGtv.asArray().map(GtvToEventMapper::map)

        val approvalEvent = events[0]
        assert(approvalEvent.name).isEqualTo("Approval")
        assert(approvalEvent.indexedParameters[0].type).isEqualTo(Address::class.java)
        assert(approvalEvent.indexedParameters[1].type).isEqualTo(Address::class.java)
        assert(approvalEvent.nonIndexedParameters[0].type).isEqualTo(Uint256::class.java)

        val transferEvent = events[1]
        assert(transferEvent.name).isEqualTo("Transfer")
        assert(transferEvent.indexedParameters[0].type).isEqualTo(Address::class.java)
        assert(transferEvent.indexedParameters[1].type).isEqualTo(Address::class.java)
        assert(transferEvent.nonIndexedParameters[0].type).isEqualTo(Uint256::class.java)
    }

    @Test
    fun `All basic types can be matched`() {
        assertTypeNameIsSupported("address", Address::class.java)
        assertTypeNameIsSupported("bool", Bool::class.java)
        assertTypeNameIsSupported("bytes", DynamicBytes::class.java)
        assertTypeNameIsSupported("int", org.web3j.abi.datatypes.Int::class.java)
        assertTypeNameIsSupported("string", Utf8String::class.java)
        assertTypeNameIsSupported("uint", Uint::class.java)
    }

    @Test
    fun `All size suffixes can be matched`() {
        for (i in 1..32) {
            assertTypeNameIsSupported(
                "bytes$i",
                Class.forName("org.web3j.abi.datatypes.generated.Bytes$i").asSubclass(Type::class.java)
            )
            assertTypeNameIsSupported(
                "int${i * 8}",
                Class.forName("org.web3j.abi.datatypes.generated.Int${i * 8}").asSubclass(Type::class.java)
            )
            assertTypeNameIsSupported(
                "uint${i * 8}",
                Class.forName("org.web3j.abi.datatypes.generated.Uint${i * 8}").asSubclass(Type::class.java)
            )
        }
    }

    @Test
    fun `Allow single dimension arrays`() {
        assertTypeNameIsSupported("address[]", DynamicArray::class.java)
        assertTypeNameIsSupported("uint256[]", DynamicArray::class.java)
    }

    @Test
    fun `Prevent multi dimension arrays`() {
        assertTypeNameIsUnsupported("string[][]")
    }

    @Test
    fun `Prevent structs`() {
        assertTypeNameIsUnsupported("tuple")
    }

    @Test
    fun `Prevent illegal size suffixes`() {
        assertTypeNameIsUnsupported("address1")
        assertTypeNameIsUnsupported("bytes33")
        assertTypeNameIsUnsupported("uint33")
    }

    private fun assertTypeNameIsSupported(typeName: String, expectedClass: Class<out Type<*>>) {
        val event = eventWithInputType(typeName)
        val mappedEvent = GtvToEventMapper.map(event)
        assert(mappedEvent.indexedParameters[0].classType).isEqualTo(expectedClass)
    }

    private fun eventWithInputType(typeName: String): Gtv {
        return gtv(
            "inputs" to gtv(
                listOf(
                    gtv(
                        "indexed" to gtv(true),
                        "type" to gtv(typeName)
                    )
                )
            ),
            "type" to gtv("event"),
            "name" to gtv("TestEvent")
        )
    }

    private fun assertTypeNameIsUnsupported(typeName: String) {
        val event = eventWithInputType(typeName)
        assertThrows<UserMistake>("Unsupported type: $typeName") {
            GtvToEventMapper.map(event)
        }
    }
}