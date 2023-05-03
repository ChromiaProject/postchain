package net.postchain.base.data

import net.postchain.PostchainNode
import net.postchain.common.exception.UserMistake
import net.postchain.config.app.AppConfig
import org.apache.commons.configuration2.PropertiesConfiguration
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CollationIT {
    @Test
    fun testCollationTestPass() {
        PostgreSQLContainer(DockerImageName.parse("postgres:14.7-alpine3.17")).apply {
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
    fun testCollationTestFail() {
        PostgreSQLContainer(DockerImageName.parse("postgres:14.7")).apply {
            withUsername("postchain")
            withPassword("postchain")
            start()
        }.use { postgres ->
            EnvironmentVariables("POSTCHAIN_DB_URL", postgres.jdbcUrl).execute {
                val exception = assertFailsWith<UserMistake> {
                    PostchainNode(appConfig(postgres))
                }
                assertTrue(exception.message?.contains("Database collation check failed") ?: false)
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
