package net.postchain.base.data

import net.postchain.config.app.AppConfig
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

fun testDbConfig(dbSchema: String): AppConfig {
    val dbUrl = System.getenv("POSTCHAIN_DB_URL") ?: "jdbc:postgresql://localhost:5432/postchain"

    return mock {
        on { databaseDriverclass } doReturn "org.postgresql.Driver"
        on { databaseUrl } doReturn dbUrl
        on { databaseUsername } doReturn "postchain"
        on { databasePassword } doReturn "postchain"
        on { databaseSchema } doReturn dbSchema
        on { databaseReadConcurrency } doReturn 10
    }
}
