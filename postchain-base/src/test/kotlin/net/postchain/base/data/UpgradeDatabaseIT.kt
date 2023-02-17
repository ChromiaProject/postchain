package net.postchain.base.data

import net.postchain.StorageBuilder
import net.postchain.base.withReadConnection
import net.postchain.common.exception.UserMistake
import net.postchain.config.app.AppConfig
import net.postchain.core.AppContext
import net.postchain.core.Storage
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class UpgradeDatabaseIT {

    private val appConfig: AppConfig = testDbConfig("upgrade_database_test")

    @Test
    fun testDbVersion1() {
        // Initial launch
        StorageBuilder.buildStorage(appConfig, wipeDatabase = true, expectedDbVersion = 1)
                .use { assertVersion1(it) }

        // Reopen
        StorageBuilder.buildStorage(appConfig, wipeDatabase = false, expectedDbVersion = 1)
                .use { assertVersion1(it) }
    }

    private fun assertVersion1(storage: Storage) {
        storage.withReadConnection { ctx ->
            val db = DatabaseAccess.of(ctx) as SQLDatabaseAccess
            assertVersion1(db, ctx)
        }
    }

    private fun assertVersion1(db: SQLDatabaseAccess, ctx: AppContext) {
        assert(db.tableExists(ctx.conn, "meta"))
        assert(db.tableExists(ctx.conn, "peerinfos"))
        assert(db.tableExists(ctx.conn, "blockchains"))
    }

    @Test
    fun testDbVersion2() {
        // Initial launch
        StorageBuilder.buildStorage(appConfig, wipeDatabase = true, expectedDbVersion = 2)
                .use { assertVersion2(it) }

        // Reopen
        StorageBuilder.buildStorage(appConfig, wipeDatabase = false, expectedDbVersion = 2)
                .use { assertVersion2(it) }
    }

    @Test
    fun testUpgradeDbVersionFrom1To2() {
        // Initial launch version 1
        StorageBuilder.buildStorage(appConfig, wipeDatabase = true, expectedDbVersion = 1)

        // Upgrade to version 2
        StorageBuilder.buildStorage(appConfig, wipeDatabase = false, expectedDbVersion = 2)
                .use { assertVersion2(it) }

        // Reopen
        StorageBuilder.buildStorage(appConfig, wipeDatabase = false, expectedDbVersion = 2)
                .use { assertVersion2(it) }
    }

    private fun assertVersion2(storage: Storage) {
        storage.withReadConnection { ctx ->
            val db = DatabaseAccess.of(ctx) as SQLDatabaseAccess
            assertVersion2(db, ctx)
        }
    }

    private fun assertVersion2(db: SQLDatabaseAccess, ctx: AppContext) {
        assertVersion1(db, ctx)

        assert(db.tableExists(ctx.conn, "blockchain_replicas"))
        assert(db.tableExists(ctx.conn, "must_sync_until"))
    }

    @Test
    fun testDbVersion3() {
        // Initial launch
        StorageBuilder.buildStorage(appConfig, wipeDatabase = true, expectedDbVersion = 3)
                .use { assertVersion3(it) }

        // Reopen
        StorageBuilder.buildStorage(appConfig, wipeDatabase = false, expectedDbVersion = 3)
                .use { assertVersion3(it) }
    }

    @Test
    fun testUpgradeDbVersionFrom2To3() {
        // Initial launch version 2
        StorageBuilder.buildStorage(appConfig, wipeDatabase = true, expectedDbVersion = 2)

        // Upgrade to version 3
        StorageBuilder.buildStorage(appConfig, wipeDatabase = false, expectedDbVersion = 3)
                .use { assertVersion3(it) }

        // Reopen
        StorageBuilder.buildStorage(appConfig, wipeDatabase = false, expectedDbVersion = 3)
                .use { assertVersion3(it) }
    }

    @Test
    fun testUpgradeDbVersionFrom1To3() {
        // Initial launch version 1
        StorageBuilder.buildStorage(appConfig, wipeDatabase = true, expectedDbVersion = 1)

        // Upgrade to version 3
        StorageBuilder.buildStorage(appConfig, wipeDatabase = false, expectedDbVersion = 3)
                .use { assertVersion3(it) }

        // Reopen
        StorageBuilder.buildStorage(appConfig, wipeDatabase = false, expectedDbVersion = 3)
                .use { assertVersion3(it) }
    }

    private fun assertVersion3(storage: Storage) {
        storage.withReadConnection { ctx ->
            val db = DatabaseAccess.of(ctx) as SQLDatabaseAccess
            assertVersion3(db, ctx)
        }
    }

    private fun assertVersion3(db: SQLDatabaseAccess, ctx: AppContext) {
        assertVersion2(db, ctx)

        assert(db.tableExists(ctx.conn, "containers"))
    }

    @Test
    fun testError_When_DowngradeDbVersionFrom2To1() {
        StorageBuilder.buildStorage(appConfig, wipeDatabase = true, expectedDbVersion = 2)

        Assertions.assertThrows(UserMistake::class.java) {
            StorageBuilder.buildStorage(appConfig, wipeDatabase = false, expectedDbVersion = 1)
        }
    }
}
