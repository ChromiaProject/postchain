package net.postchain.base.data

import net.postchain.PostchainNode
import net.postchain.common.exception.UserMistake
import net.postchain.config.app.AppConfig
import org.apache.commons.configuration2.PropertiesConfiguration
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables

class CollationIT {
    @Test
    @Tag("docker")
    fun testCollationTestPass() {
        PostgreSQLContainer(
                DockerImageName.parse("postgres:16.6-alpine3.21@sha256:aba1fab94626cf8b0f4549055214239a37e0a690f03f142b7bca05b9ed36c6db")
                        .asCompatibleSubstituteFor("postgres")).apply {
            withUsername("postchain")
            withPassword("postchain")
            start()
        }.use { postgres ->
            EnvironmentVariables("POSTCHAIN_DB_URL", postgres.jdbcUrl).execute {
                PostchainNode(appConfig(postgres))
            }
        }
    }

    @Test
    @Tag("docker")
    fun testCollationTestFail() {
        PostgreSQLContainer(
                DockerImageName.parse("postgres:16.6:c7afedc5c15994625b5be4cb4736c030271b55be0360b78a99c90ec2fbe658b6")
                        .asCompatibleSubstituteFor("postgres")).apply {
            withUsername("postchain")
            withPassword("postchain")
            start()
        }.use { postgres ->
            EnvironmentVariables("POSTCHAIN_DB_URL", postgres.jdbcUrl).execute {
                val exception = assertThrows<UserMistake> {
                    PostchainNode(appConfig(postgres))
                }
                assertTrue(exception.message?.contains("Database collation check failed") == true)
            }
        }
    }

    private fun appConfig(postgres: PostgreSQLContainer<out PostgreSQLContainer<*>>) =
            AppConfig(PropertiesConfiguration().apply {
                addProperty("database.url", postgres.jdbcUrl)
                addProperty("database.username", postgres.username)
                addProperty("database.password", postgres.password)
                addProperty("messaging.privkey", "3132333435363738393031323334353637383930313233343536373839303131")
                addProperty("messaging.pubkey", "0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57")
                addProperty("api.port", "-1")
            })
}
