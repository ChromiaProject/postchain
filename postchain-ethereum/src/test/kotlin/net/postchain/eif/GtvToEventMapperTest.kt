package net.postchain.eif

import assertk.assert
import assertk.assertions.isEqualTo
import net.postchain.gtv.gtvml.GtvMLParser
import org.junit.jupiter.api.Test
import org.web3j.abi.datatypes.Address
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

}