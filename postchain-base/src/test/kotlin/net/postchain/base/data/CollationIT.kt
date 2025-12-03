package net.postchain.base.data

import net.postchain.PostchainNode
import net.postchain.common.exception.UserMistake
import net.postchain.config.app.AppConfig
import org.apache.commons.configuration2.PropertiesConfiguration
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables

class CollationIT {
    @Test
    @Tag("docker")
    fun testCollationTestPass() {
        PostgreSQLContainer(
                DockerImageName.parse("postgres:16.7-alpine3.21@sha256:97a14a17b1fea5ae1ab33024ca556bb4fedc8709bea5722cb8b7665a9cabb656")
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
                DockerImageName.parse("postgres:16.7@sha256:a35ec42526e3c522eb13b4d82eddaee875d0ac6ca9eb5cc5607e412854478c71")
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

    private fun appConfig(postgres: PostgreSQLContainer) =
            AppConfig(PropertiesConfiguration().apply {
                addProperty("database.url", postgres.jdbcUrl)
                addProperty("database.username", postgres.username)
                addProperty("database.password", postgres.password)
                addProperty("messaging.privkey", "3132333435363738393031323334353637383930313233343536373839303131")
                addProperty("messaging.pubkey", "0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57")
                addProperty("api.port", "-1")
                addProperty("debug.port", "-1")
            })
}
