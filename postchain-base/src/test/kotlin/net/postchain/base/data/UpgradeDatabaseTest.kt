package net.postchain.base.data

import net.postchain.StorageBuilder
import net.postchain.base.Storage
import net.postchain.base.withReadConnection
import net.postchain.config.app.AppConfig
import net.postchain.core.UserMistake
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import kotlin.test.assertFalse

class UpgradeDatabaseTest {

    private val appConfig: AppConfig = mock {
        on { databaseDriverclass } doReturn "org.postgresql.Driver"
        on { databaseUrl } doReturn "jdbc:postgresql://localhost:5432/postchain"
        on { databaseUsername } doReturn "postchain"
        on { databasePassword } doReturn "postchain"
        on { databaseSchema } doReturn "upgrade_database_test"
    }

    @Test
    fun testDbVersion1() {

        fun assertAll(storage: Storage) {
            storage.withReadConnection { ctx ->
                val db = DatabaseAccess.of(ctx) as SQLDatabaseAccess

                // version 1:
                assert(db.tableExists(ctx.conn, "meta"))
                assert(db.tableExists(ctx.conn, "peerinfos"))
                assert(db.tableExists(ctx.conn, "blockchains"))
                // version 2:
                assertFalse(db.tableExists(ctx.conn, "blockchain_replicas"))
                assertFalse(db.tableExists(ctx.conn, "must_sync_until"))
            }
        }

        // Initial launch
        StorageBuilder.buildStorage(appConfig, wipeDatabase = true, expectedDbVersion = 1)
                .use { assertAll(it) }

        // Reopen
        StorageBuilder.buildStorage(appConfig, wipeDatabase = false, expectedDbVersion = 1)
                .use { assertAll(it) }
    }

    @Test
    fun testDbVersion2() {

        fun assertAll(storage: Storage) {
            storage.withReadConnection { ctx ->
                val db = DatabaseAccess.of(ctx) as SQLDatabaseAccess

                // version 1:
                assert(db.tableExists(ctx.conn, "meta"))
                assert(db.tableExists(ctx.conn, "peerinfos"))
                assert(db.tableExists(ctx.conn, "blockchains"))
                // version 2:
                assert(db.tableExists(ctx.conn, "blockchain_replicas"))
                assert(db.tableExists(ctx.conn, "must_sync_until"))
            }
        }

        // Initial launch
        StorageBuilder.buildStorage(appConfig, wipeDatabase = true, expectedDbVersion = 2)
                .use { assertAll(it) }

        // Reopen
        StorageBuilder.buildStorage(appConfig, wipeDatabase = false, expectedDbVersion = 2)
                .use { assertAll(it) }
    }

    @Test
    fun testUpgradeDbVersionFrom1To2() {

        fun assertAll(storage: Storage) {
            storage.withReadConnection { ctx ->
                val db = DatabaseAccess.of(ctx) as SQLDatabaseAccess

                // version 1:
                assert(db.tableExists(ctx.conn, "meta"))
                assert(db.tableExists(ctx.conn, "peerinfos"))
                assert(db.tableExists(ctx.conn, "blockchains"))
                // version 2:
                assert(db.tableExists(ctx.conn, "blockchain_replicas"))
                assert(db.tableExists(ctx.conn, "must_sync_until"))
            }
        }

        // Initial launch version 1
        StorageBuilder.buildStorage(appConfig, wipeDatabase = true, expectedDbVersion = 1)
        // Initial launch version 2
        StorageBuilder.buildStorage(appConfig, wipeDatabase = false, expectedDbVersion = 2)
                .use { assertAll(it) }
        // Reopen
        StorageBuilder.buildStorage(appConfig, wipeDatabase = false, expectedDbVersion = 2)
                .use { assertAll(it) }
    }

    @Test
    fun testError_When_DowngradeDbVersionFrom2To1() {
        StorageBuilder.buildStorage(appConfig, wipeDatabase = true, expectedDbVersion = 2)

        Assertions.assertThrows(UserMistake::class.java) {
            StorageBuilder.buildStorage(appConfig, wipeDatabase = false, expectedDbVersion = 1)
        }
    }

}