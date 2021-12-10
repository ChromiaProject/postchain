// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.config.app

import assertk.assertions.isEmpty
import assertk.assert
import assertk.assertions.isEqualTo
import org.junit.Test

class AppConfigTest {

    @Test
    fun testEmptyNodeConfig() {
        val appConfig = AppConfig.fromPropertiesFile(
                javaClass.getResource("/net/postchain/config/empty-node-config.properties").file)

        assert(appConfig.nodeConfigProvider).isEmpty()
        assert(appConfig.databaseDriverclass).isEmpty()
        assert(appConfig.databaseUrl).apply {
            if ( System.getenv()["POSTCHAIN_DB_URL"] == null ) isEmpty()
            else isEqualTo(System.getenv()["POSTCHAIN_DB_URL"])
        }
        assert(appConfig.databaseSchema).isEqualTo("public")
        assert(appConfig.databaseUsername).isEmpty()
        assert(appConfig.databasePassword).isEmpty()
    }
}