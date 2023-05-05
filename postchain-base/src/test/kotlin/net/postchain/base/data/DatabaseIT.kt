package net.postchain.base.data

import net.postchain.StorageBuilder
import net.postchain.base.withReadConnection
import net.postchain.base.withWriteConnection
import net.postchain.common.BlockchainRid
import net.postchain.config.app.AppConfig
import net.postchain.gtv.GtvEncoder.encodeGtv
import net.postchain.gtv.GtvFactory.gtv
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.sql.SQLException

class DatabaseIT {

    private val appConfig: AppConfig = testDbConfig("database_it")

    @Test
    fun collation() {
        val storage = StorageBuilder.buildStorage(appConfig, wipeDatabase = true)
        storage.withReadConnection { ctx ->
            DatabaseAccess.of(ctx).checkCollation(ctx.conn, suppressError = false)
        }
    }

    @Test
    fun containers() {
        val storage = StorageBuilder.buildStorage(appConfig, wipeDatabase = true)

        storage.withWriteConnection { ctx ->
            val db = DatabaseAccess.of(ctx)
            assertNull(db.getContainerIid(ctx, "test"))
            val iid1 = db.createContainer(ctx, "test1")
            val iid2 = db.createContainer(ctx, "test2")
            assertNotEquals(iid1, iid2)
            assertTrue(iid1 > 0)
            assertTrue(iid2 > 0)
            assertEquals(iid1, db.getContainerIid(ctx, "test1"))
            assertEquals(iid2, db.getContainerIid(ctx, "test2"))
        }
    }

    @Test
    fun configurations() {
        val storage = StorageBuilder.buildStorage(appConfig, wipeDatabase = true)

        val chainId = 0L
        val configData1 = gtv(mapOf("any" to gtv("value1")))
        val configData2 = gtv(mapOf("any" to gtv("value2")))
        val configData3 = gtv(mapOf("any" to gtv("value3")))

        withWriteConnection(storage, chainId) { ctx ->
            val db = DatabaseAccess.of(ctx)
            db.initializeBlockchain(ctx, BlockchainRid.ZERO_RID)
            true
        }

        withWriteConnection(storage, chainId) { ctx ->
            val db = DatabaseAccess.of(ctx)
            db.addConfigurationData(ctx, 0, encodeGtv(configData1))
            db.addConfigurationData(ctx, 5, encodeGtv(configData2))
            true
        }

        withReadConnection(storage, chainId) { ctx ->
            val db = DatabaseAccess.of(ctx)
            assertEquals(listOf(0L, 5L), db.listConfigurations(ctx))
            assertArrayEquals(encodeGtv(configData1), db.getConfigurationData(ctx, 0L))
            assertArrayEquals(encodeGtv(configData2), db.getConfigurationData(ctx, 5L))
            assertNull(db.getConfigurationData(ctx, 7L))
            assertArrayEquals(encodeGtv(configData2), db.getConfigurationDataForHeight(ctx, 7L))
            assertArrayEquals(encodeGtv(configData1), db.getConfigurationDataFromHeight(ctx, 0L, configurationHash(configData1)))
            assertArrayEquals(encodeGtv(configData2), db.getConfigurationDataFromHeight(ctx, 0L, configurationHash(configData2)))
            assertNull(db.getConfigurationDataFromHeight(ctx, 0L, configurationHash(configData3)))
        }

        withWriteConnection(storage, chainId) { ctx ->
            val db = DatabaseAccess.of(ctx)
            db.addConfigurationData(ctx, 0, encodeGtv(configData1))
            assertThrows<SQLException> {
                db.addConfigurationData(ctx, 5, encodeGtv(configData1))
            }
            true
        }
    }
}
