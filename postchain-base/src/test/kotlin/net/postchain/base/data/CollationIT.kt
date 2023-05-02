package net.postchain.base.data

import net.postchain.common.exception.UserMistake
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.DriverManager
import kotlin.test.assertFailsWith

class CollationIT {
    @Test
    fun testCollationTestPass() {
        PostgreSQLContainer(DockerImageName.parse("postgres:14.7-alpine3.17")).apply { start() }.use { postgres ->
            DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
                PostgreSQLDatabaseAccess().checkCollation(connection, suppressError = false)
            }
        }
    }

    @Test fun testCollationTestFail() {
        PostgreSQLContainer(DockerImageName.parse("postgres:14.7")).apply { start() }.use { postgres ->
            DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
                assertFailsWith<UserMistake> {
                    PostgreSQLDatabaseAccess().checkCollation(connection, suppressError = false)
                }
            }
        }
    }
}
