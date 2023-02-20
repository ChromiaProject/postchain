package net.postchain.base.data

import net.postchain.StorageBuilder
import net.postchain.base.withReadConnection
import net.postchain.config.app.AppConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContainerIT {

    private val appConfig: AppConfig = testDbConfig("container_test")

    @Test
    fun testContainers() {
        val storage = StorageBuilder.buildStorage(appConfig, wipeDatabase = true)

        storage.withReadConnection { ctx ->
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
}
