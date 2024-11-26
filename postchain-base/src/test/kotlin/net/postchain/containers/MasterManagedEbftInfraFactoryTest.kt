package net.postchain.containers

import assertk.assertThat
import assertk.assertions.isEqualTo
import net.postchain.common.exception.UserMistake
import net.postchain.containers.infra.MasterManagedEbftInfraFactory
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MasterManagedEbftInfraFactoryTest {

    @Test
    fun `test subnode-user validation`() {

        val invalidFormats = mapOf(
                null to "POSTCHAIN_SUBNODE_USER must be specified",
                "0" to "POSTCHAIN_SUBNODE_USER can't be set to root",
                "0:" to "POSTCHAIN_SUBNODE_USER requires format <uid> or <uid>:<gid>",
                "0:0" to "POSTCHAIN_SUBNODE_USER can't be set to root",
                "0:1" to "POSTCHAIN_SUBNODE_USER can't be set to root",
                "1:0" to "POSTCHAIN_SUBNODE_USER can't be set to root",
                "abc" to "POSTCHAIN_SUBNODE_USER requires format <uid> or <uid>:<gid>",
                "1:abc" to "POSTCHAIN_SUBNODE_USER requires format <uid> or <uid>:<gid>",
        )

        invalidFormats.forEach {
            println("Testing ${it.key}")
            var exception = assertThrows<UserMistake> { MasterManagedEbftInfraFactory.validateSubnodeUser(it.key) }
            assertThat(exception.message).isEqualTo(it.value)
        }

        val validFormats = listOf(
                "1",
                "1:1",
        )

        validFormats.forEach {
            println("Testing it")
            MasterManagedEbftInfraFactory.validateSubnodeUser(it)
        }
    }
}