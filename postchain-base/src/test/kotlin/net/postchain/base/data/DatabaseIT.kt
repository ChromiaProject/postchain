package net.postchain.base.data

import net.postchain.StorageBuilder
import net.postchain.base.BaseDependencyFactory
import net.postchain.base.BlockchainRelatedInfo
import net.postchain.base.PeerInfo
import net.postchain.base.configuration.KEY_CONFIGURATIONFACTORY
import net.postchain.base.configuration.KEY_DEPENDENCIES
import net.postchain.base.configuration.KEY_SIGNERS
import net.postchain.base.cryptoSystem
import net.postchain.base.gtv.GtvToBlockchainRidFactory
import net.postchain.base.runStorageCommand
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.wrap
import net.postchain.config.app.AppConfig
import net.postchain.core.EContext
import net.postchain.crypto.PubKey
import net.postchain.gtv.GtvEncoder.encodeGtv
import net.postchain.gtv.GtvFactory.gtv
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.ColumnListHandler
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.sql.SQLException

class DatabaseIT {

    private val appConfig: AppConfig = testDbConfig("database_it")

    fun selectAllTablesSql(chainId: Long) = "SELECT tables.table_name FROM information_schema.tables AS tables" +
            " WHERE tables.table_schema = current_schema() AND tables.table_name LIKE 'c$chainId.%'"

    @BeforeEach
    fun beforeEach() {
        StorageBuilder.wipeDatabase(appConfig)
    }

    @Test
    fun collation() {
        runStorageCommand(appConfig) { ctx ->
            DatabaseAccess.of(ctx).checkCollation(ctx.conn, suppressError = false)
        }
    }

    @Test
    fun blockchains() {
        runStorageCommand(appConfig, 1) { ctx ->
            val db = DatabaseAccess.of(ctx)
            db.initializeBlockchain(ctx, BlockchainRid(ByteArray(32) { 1 }))
        }

        runStorageCommand(appConfig, 2) { ctx ->
            val db = DatabaseAccess.of(ctx)
            db.initializeBlockchain(ctx, BlockchainRid(ByteArray(32) { 2 }))
        }

        assertThrows<UserMistake> {
            runStorageCommand(appConfig, 3) { ctx ->
                val db = DatabaseAccess.of(ctx)
                db.initializeBlockchain(ctx, BlockchainRid(ByteArray(32) { 1 }))
            }
        }
    }

    @Test
    fun containers() {
        runStorageCommand(appConfig) { ctx ->
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
        val chainId = 0L
        val configData1 = gtv(mapOf("any" to gtv("value1")))
        val configData2 = gtv(mapOf("any" to gtv("value2")))
        val configData3 = gtv(mapOf("any" to gtv("value3")))

        runStorageCommand(appConfig, chainId) { ctx ->
            val db = DatabaseAccess.of(ctx)
            db.initializeBlockchain(ctx, BlockchainRid.ZERO_RID)

            db.addConfigurationData(ctx, 0, encodeGtv(configData1))
            db.addConfigurationData(ctx, 5, encodeGtv(configData2))

            val hash1 = GtvToBlockchainRidFactory.calculateBlockchainRid(configData1, cryptoSystem)
            val hash2 = GtvToBlockchainRidFactory.calculateBlockchainRid(configData2, cryptoSystem)
            val hash3 = GtvToBlockchainRidFactory.calculateBlockchainRid(configData3, cryptoSystem)

            assertEquals(listOf(0L, 5L), db.listConfigurations(ctx))
            assertEquals(listOf(hash1.wData, hash2.wData), db.listConfigurationHashes(ctx).map { it.wrap() })
            assertTrue(db.configurationHashExists(ctx, hash1.data))
            assertFalse(db.configurationHashExists(ctx, hash3.data))
            assertArrayEquals(encodeGtv(configData1), db.getConfigurationData(ctx, 0L))
            assertArrayEquals(encodeGtv(configData2), db.getConfigurationData(ctx, 5L))
            assertNull(db.getConfigurationData(ctx, 7L))
            assertArrayEquals(encodeGtv(configData2), db.getConfigurationDataForHeight(ctx, 7L))
            assertArrayEquals(encodeGtv(configData1), db.getConfigurationData(ctx, configurationHash(configData1)))
            assertArrayEquals(encodeGtv(configData2), db.getConfigurationData(ctx, configurationHash(configData2)))
            assertNull(db.getConfigurationData(ctx, configurationHash(configData3)))

            db.addConfigurationData(ctx, 0, encodeGtv(configData1))
            assertThrows<SQLException> {
                db.addConfigurationData(ctx, 5, encodeGtv(configData1))
            }
        }
    }

    @Test
    fun dropTable() {
        val chainId = 0L

        runStorageCommand(appConfig, chainId) { ctx ->
            val db = DatabaseAccess.of(ctx)
            db.initializeBlockchain(ctx, BlockchainRid.ZERO_RID)
            assertTrue(tableExists(ctx, "transactions"))

            db.dropTable(ctx.conn, db.tableName(ctx, "transactions"))

            assertFalse(tableExists(ctx, "transactions"))
        }
    }

    private fun tableExists(ctx: EContext, tableName: String): Boolean {
        val db = DatabaseAccess.of(ctx)
        val realTableName = db.tableName(ctx, tableName).replace("\"", "")
        val queryRunner = QueryRunner()
        val sql = "SELECT tables.table_name FROM information_schema.tables AS tables" +
                " WHERE tables.table_schema = current_schema() AND tables.table_name = '${realTableName}'"
        return queryRunner.query(ctx.conn, sql, ColumnListHandler<String>()).isNotEmpty()
    }

    @Test
    fun removeBlockchain() {
        val chainId = 0L

        runStorageCommand(appConfig, chainId) { ctx ->
            val db = DatabaseAccess.of(ctx)
            db.initializeBlockchain(ctx, BlockchainRid.ZERO_RID)
            assertNotNull(db.getBlockchainRid(ctx))

            db.removeBlockchain(ctx)
            assertNull(db.getBlockchainRid(ctx))
        }
    }

    @Test
    fun removeAllBlockchainTables() {
        val chainId = 0L
        val queryRunner = QueryRunner()

        runStorageCommand(appConfig, chainId) { ctx ->
            val db = DatabaseAccess.of(ctx)
            db.initializeBlockchain(ctx, BlockchainRid.ZERO_RID)

            val allTables = queryRunner.query(ctx.conn, selectAllTablesSql(chainId), ColumnListHandler<String>())
            assertTrue(allTables.isNotEmpty())

            db.removeAllBlockchainSpecificTables(ctx)

            val allTables2 = queryRunner.query(ctx.conn, selectAllTablesSql(chainId), ColumnListHandler<String>())
            assertTrue(allTables2.isEmpty())
        }
    }

    @Test
    fun removeAllBlockchainTablesWithExclusion() {
        val chainId = 0L
        val excludeTables = listOf("configurations", "blocks", "transactions")
        val queryRunner = QueryRunner()

        runStorageCommand(appConfig, chainId) { ctx ->
            val db = DatabaseAccess.of(ctx)
            db.initializeBlockchain(ctx, BlockchainRid.ZERO_RID)

            val tables = queryRunner.query(ctx.conn, selectAllTablesSql(chainId), ColumnListHandler<String>())
                    .filter { it.substringAfter(".") !in excludeTables }
            assertTrue(tables.isNotEmpty())

            db.removeAllBlockchainSpecificTables(ctx, excludeTables)

            val tables2 = queryRunner.query(ctx.conn, selectAllTablesSql(chainId), ColumnListHandler<String>()).toSet()
            assertEquals(excludeTables.map { "c0.$it" }.toSet(), tables2)
        }
    }

    @Test
    fun removeAllBlockchainSpecificFunctions() {
        val chainId = 10L
        val queryRunner = QueryRunner()

        // init blockchain
        val rowIdTable = "c$chainId.rowid_gen"
        val funcName = "c$chainId.make_rowid"
        runStorageCommand(appConfig, chainId) { ctx ->
            val db = DatabaseAccess.of(ctx)
            db.initializeBlockchain(ctx, BlockchainRid.ZERO_RID)
            // GtxModule adds a function
            queryRunner.execute(ctx.conn, """CREATE TABLE "$rowIdTable"( last_value bigint not null);""")
            queryRunner.execute(ctx.conn, """INSERT INTO "$rowIdTable"(last_value) VALUES (0);""")
            queryRunner.execute(ctx.conn, """
            CREATE FUNCTION "$funcName"() RETURNS BIGINT AS
            'UPDATE "$rowIdTable" SET last_value = last_value + 1 RETURNING last_value'
            LANGUAGE SQL;
        """.trimIndent())
            queryRunner.execute(ctx.conn, """
            CREATE FUNCTION "$funcName.0"() RETURNS BIGINT AS 'SELECT 0' LANGUAGE SQL;
        """.trimIndent())

            val allFunctionsQuery = """
            SELECT routine_name FROM information_schema.routines
            WHERE routine_schema = current_schema() AND routine_name LIKE 'c${chainId}.%'
        """.trimIndent()

        // before
            val allFunctions = queryRunner.query(ctx.conn, allFunctionsQuery, ColumnListHandler<String>())
            assertEquals(allFunctions, listOf("c10.make_rowid", "c10.make_rowid.0"))

        // action
            db.removeAllBlockchainSpecificFunctions(ctx)
            db.removeAllBlockchainSpecificFunctions(ctx) // deleting IF NOT EXISTS

        // after
            val allFunctions2 = queryRunner.query(ctx.conn, allFunctionsQuery, ColumnListHandler<String>())
            assertTrue(allFunctions2.isEmpty())
        }
    }

    @Test
    fun removeMustSyncUntil() {
        val chainId = 0L

        runStorageCommand(appConfig, chainId) { ctx ->
            val db = DatabaseAccess.of(ctx)
            db.initializeBlockchain(ctx, BlockchainRid.ZERO_RID)
            db.setMustSyncUntil(ctx, BlockchainRid.ZERO_RID, 10)

            assertEquals(10, db.getMustSyncUntil(ctx)[chainId])

            db.removeBlockchainFromMustSyncUntil(ctx)

            assertTrue(db.getMustSyncUntil(ctx).isEmpty())
        }
    }

    @Test
    fun removeBlockchainReplica() {
        val chainId = 0L
        val node = PubKey(ByteArray(32))

        runStorageCommand(appConfig, chainId) { ctx ->
            val db = DatabaseAccess.of(ctx)
            db.initializeBlockchain(ctx, BlockchainRid.ZERO_RID)
            db.addPeerInfo(ctx, PeerInfo("test", 0, node.data))
            db.addBlockchainReplica(ctx, BlockchainRid.ZERO_RID, node)

            assertEquals(listOf(node.data.wrap()), db.getBlockchainReplicaCollection(ctx)[BlockchainRid.ZERO_RID])

            db.removeAllBlockchainReplicas(ctx)

            assertTrue(db.getBlockchainReplicaCollection(ctx).isEmpty())
        }
    }

    @Test
    fun getAllConfigurations() {
        val chainId = 0L

        runStorageCommand(appConfig, chainId) { ctx ->
            val db = DatabaseAccess.of(ctx)
            db.initializeBlockchain(ctx, BlockchainRid.ZERO_RID)

            assertTrue(db.getAllConfigurations(ctx.conn, chainId).isEmpty())

            db.addConfigurationData(ctx, 0, encodeGtv(gtv(0)))
            db.addConfigurationData(ctx, 10, encodeGtv(gtv(1)))
            db.addConfigurationData(ctx, 2, encodeGtv(gtv(2)))

            assertEquals(3, db.getAllConfigurations(ctx.conn, chainId).size)
        }
    }

    @Test
    fun getDependenciesOnBlockchain() {
        val chainId = 0L
        val dependentChainId = 1L

        runStorageCommand(appConfig, chainId) { ctx ->
            val db = DatabaseAccess.of(ctx)
            db.initializeBlockchain(ctx, BlockchainRid.ZERO_RID)
        }

        runStorageCommand(appConfig, dependentChainId) { ctx ->
            val db = DatabaseAccess.of(ctx)
            db.initializeBlockchain(ctx, BlockchainRid.buildRepeat(1))
            db.addConfigurationData(ctx, 0, encodeGtv(gtv(mapOf(
                    KEY_SIGNERS to gtv(listOf()),
                    KEY_CONFIGURATIONFACTORY to gtv("test"),
                    KEY_DEPENDENCIES to BaseDependencyFactory.buildGtv(listOf(
                            BlockchainRelatedInfo(BlockchainRid.ZERO_RID, "dependency", chainId)
                    ))!!
            ))))
        }

        runStorageCommand(appConfig, chainId) { ctx ->
            val db = DatabaseAccess.of(ctx)
            val deps = db.getDependenciesOnBlockchain(ctx)
            assertEquals(1, deps.size)
            assertEquals(BlockchainRid.buildRepeat(1), deps[0])
        }
    }
}