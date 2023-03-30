package net.postchain.base.data

import net.postchain.StorageBuilder
import net.postchain.base.PeerInfo
import net.postchain.base.withReadConnection
import net.postchain.base.withWriteConnection
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.exception.UserMistake
import net.postchain.common.toHex
import net.postchain.config.app.AppConfig
import net.postchain.core.AppContext
import net.postchain.core.EContext
import net.postchain.core.NodeRid
import net.postchain.core.Storage
import net.postchain.crypto.PubKey
import net.postchain.crypto.devtools.KeyPairHelper
import net.postchain.gtv.GtvEncoder.encodeGtv
import net.postchain.gtv.GtvFactory.gtv
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UpgradeDatabaseIT {

    private val appConfig: AppConfig = testDbConfig("upgrade_database_test")

    private val configData1 = gtv(mapOf("any" to gtv("value1")))
    private val configData2 = gtv(mapOf("any" to gtv("value2")))

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

    @Test
    fun testDbVersion4() {
        // Initial launch
        StorageBuilder.buildStorage(appConfig, wipeDatabase = true, expectedDbVersion = 4)
                .use {
                    withWriteConnection(it, 0) { ctx ->
                        val db = DatabaseAccess.of(ctx)
                        db.initializeBlockchain(ctx, BlockchainRid.ZERO_RID)
                        db.addConfigurationData(ctx, 0, encodeGtv(configData1))
                        db.addConfigurationData(ctx, 10, encodeGtv(configData2))
                        true
                    }
                    assertVersion4(it)
                }

        // Reopen
        StorageBuilder.buildStorage(appConfig, wipeDatabase = false, expectedDbVersion = 4)
                .use { assertVersion4(it) }
    }

    @Test
    fun testUpgradeDbVersionFrom3To4() {
        // Initial launch version 3
        StorageBuilder.buildStorage(appConfig, wipeDatabase = true, expectedDbVersion = 3)
                .use {
                    withWriteConnection(it, 0) { ctx ->
                        val db = DatabaseAccess.of(ctx) as SQLDatabaseAccess
                        db.queryRunner.update(ctx.conn, "INSERT INTO ${db.tableBlockchains()} (chain_iid, blockchain_rid) values (?, ?)",
                                ctx.chainID, BlockchainRid.ZERO_RID.data)
                        db.queryRunner.update(ctx.conn, "CREATE TABLE IF NOT EXISTS ${db.tableConfigurations(ctx)} (" +
                                "height BIGINT PRIMARY KEY" +
                                ", configuration_data BYTEA NOT NULL" +
                                ")")
                        db.queryRunner.update(ctx.conn, "INSERT INTO ${db.tableConfigurations(ctx)} (height, configuration_data) values (?, ?)",
                                0L, encodeGtv(configData1))
                        db.queryRunner.update(ctx.conn, "INSERT INTO ${db.tableConfigurations(ctx)} (height, configuration_data) values (?, ?)",
                                10L, encodeGtv(configData2))
                        true
                    }
                }

        // Upgrade to version 4
        StorageBuilder.buildStorage(appConfig, wipeDatabase = false, expectedDbVersion = 4)
                .use { assertVersion4(it) }

        // Reopen
        StorageBuilder.buildStorage(appConfig, wipeDatabase = false, expectedDbVersion = 4)
                .use { assertVersion4(it) }
    }

    @Test
    fun testUpgradeDbVersionFrom3To4WithDuplicateConfig() {
        // Initial launch version 3
        StorageBuilder.buildStorage(appConfig, wipeDatabase = true, expectedDbVersion = 3)
                .use {
                    withWriteConnection(it, 0) { ctx ->
                        val db = DatabaseAccess.of(ctx) as SQLDatabaseAccess
                        db.queryRunner.update(ctx.conn, "INSERT INTO ${db.tableBlockchains()} (chain_iid, blockchain_rid) values (?, ?)",
                                ctx.chainID, BlockchainRid.ZERO_RID.data)
                        db.queryRunner.update(ctx.conn, "CREATE TABLE IF NOT EXISTS ${db.tableConfigurations(ctx)} (" +
                                "height BIGINT PRIMARY KEY" +
                                ", configuration_data BYTEA NOT NULL" +
                                ")")
                        db.queryRunner.update(ctx.conn, "INSERT INTO ${db.tableConfigurations(ctx)} (height, configuration_data) values (?, ?)",
                                0L, encodeGtv(configData1))
                        db.queryRunner.update(ctx.conn, "INSERT INTO ${db.tableConfigurations(ctx)} (height, configuration_data) values (?, ?)",
                                10L, encodeGtv(configData1))
                        true
                    }
                }

        // Upgrade to version 4
        StorageBuilder.buildStorage(appConfig, wipeDatabase = false, expectedDbVersion = 4)
                .use {
                    withReadConnection(it, 0) { ctx ->
                        val db = DatabaseAccess.of(ctx)
                        assertArrayEquals(encodeGtv(configData1), db.getConfigurationData(ctx, 0L))
                        assertArrayEquals(encodeGtv(configData1), db.getConfigurationData(ctx, 10L))
                        assertNull(db.getConfigurationData(ctx, 7L))
                        assertNull(db.getConfigurationData(ctx, configurationHash(configData2)))
                        assertThrows<ProgrammerMistake> {
                            db.getConfigurationData(ctx, configurationHash(configData1))
                        }
                    }
                }
    }

    @Test
    fun testUpgradeFromVersion4to5() {
        val peer = PeerInfo("localhost", 9870, KeyPairHelper.privKey(0), Instant.now().truncatedTo(ChronoUnit.SECONDS))
        val replicaBrid = BlockchainRid.buildRepeat(0)
        StorageBuilder.buildStorage(appConfig, wipeDatabase = true, expectedDbVersion = 4)
                .use {
                    withWriteConnection(it, 0) { ctx ->
                        val db = DatabaseAccess.of(ctx) as SQLDatabaseAccess
                        db.queryRunner.update(ctx.conn, "INSERT INTO peerinfos (host, port, pub_key, timestamp) values (?, ?, ?, ?)",
                                peer.host, peer.port, peer.pubKey.toHex(), SqlUtils.toTimestamp(peer.lastUpdated))
                        db.queryRunner.update(ctx.conn, "INSERT INTO blockchain_replicas (blockchain_rid, node) values (?, ?)",
                                replicaBrid.toHex(), peer.pubKey.toHex())
                        true
                    }
                }

        StorageBuilder.buildStorage(appConfig, wipeDatabase = false, expectedDbVersion = 5)
                .use {
                    withReadConnection(it, 0) { ctx ->
                        val db = DatabaseAccess.of(ctx) as SQLDatabaseAccess

                        verifyBlockchainReplicasInVersion5(db, ctx, replicaBrid, PubKey(peer.pubKey))
                        verifyPeerInfoInVersion5(db, ctx, peer)
                    }
                }
    }

    private fun verifyBlockchainReplicasInVersion5(db: SQLDatabaseAccess, ctx: EContext, replicaBrid: BlockchainRid, replicaKey: PubKey) {
        assertTrue(db.existsBlockchainReplica(ctx, replicaBrid, replicaKey))

        val replicaNodes = db.getBlockchainReplicaCollection(ctx)
        assertEquals(1, replicaNodes.size)
        assertEquals(listOf(NodeRid(replicaKey.data)), replicaNodes[replicaBrid])

        val newReplicaBrid = BlockchainRid.buildRepeat(1)
        db.addBlockchainReplica(ctx, newReplicaBrid, replicaKey)
        assertTrue(db.existsBlockchainReplica(ctx, newReplicaBrid, replicaKey))

        db.removeBlockchainReplica(ctx, newReplicaBrid, replicaKey)
        db.removeBlockchainReplica(ctx, replicaBrid, replicaKey)
        assertTrue(db.getBlockchainReplicaCollection(ctx).isEmpty())
    }

    private fun verifyPeerInfoInVersion5(db: SQLDatabaseAccess, ctx: EContext, peer: PeerInfo) {
        val peers = db.findPeerInfo(ctx, null, null, peer.pubKey.toHex())
        assertArrayEquals(arrayOf(peer), peers)

        // Matching pattern query
        // (bytes 16 - 18, chosen b/c KeyPairHelper returns keys where only byte 16 is non-zero so it's more interesting)
        val matchingPattern = peer.pubKey.toHex().substring(32, 36)
        val peersByPattern = db.findPeerInfo(ctx, null, null, matchingPattern)
        assertArrayEquals(arrayOf(peer), peersByPattern)

        // Non-matching pattern query
        val nonMatchingPattern = matchingPattern.replace('0', '1')
        assertTrue(db.findPeerInfo(ctx, null, null, nonMatchingPattern).isEmpty())

        // Invalid hex pattern
        assertThrows<UserMistake> {
            val invalidHex = peer.pubKey.toHex().substring(5, 10)
            db.findPeerInfo(ctx, null, null, invalidHex)
        }

        // Add/modify/remove peer
        db.updatePeerInfo(ctx, "localhost", 9871, PubKey(peer.pubKey), null)
        val updatedPeers = db.findPeerInfo(ctx, null, null, peer.pubKey.toHex())
        assertEquals(9871, updatedPeers[0].port)

        val newPeer = PeerInfo("localhost", 9872, KeyPairHelper.pubKey(1), Instant.now().truncatedTo(ChronoUnit.SECONDS))
        db.addPeerInfo(ctx, newPeer.host, newPeer.port, newPeer.pubKey.toHex(), newPeer.lastUpdated)
        val peersAfterAdd = db.findPeerInfo(ctx, newPeer.host, newPeer.port, newPeer.pubKey.toHex())
        assertArrayEquals(arrayOf(newPeer), peersAfterAdd)

        assertTrue(db.removePeerInfo(ctx, PubKey(KeyPairHelper.pubKey(2))).isEmpty())
        db.removePeerInfo(ctx, PubKey(newPeer.pubKey))
        db.removePeerInfo(ctx, PubKey(peer.pubKey))
        assertTrue(db.findPeerInfo(ctx, null, null, peer.pubKey.toHex()).isEmpty())
    }

    private fun assertVersion3(storage: Storage) {
        storage.withReadConnection { ctx ->
            val db = DatabaseAccess.of(ctx) as SQLDatabaseAccess
            assertVersion3(db, ctx)
        }
    }

    private fun assertVersion4(storage: Storage) {
        withReadConnection(storage, 0) { ctx ->
            val db = DatabaseAccess.of(ctx) as SQLDatabaseAccess
            assertVersion4(db, ctx)
        }
    }

    private fun assertVersion3(db: SQLDatabaseAccess, ctx: AppContext) {
        assertVersion2(db, ctx)

        assert(db.tableExists(ctx.conn, "containers"))
    }

    private fun assertVersion4(db: SQLDatabaseAccess, ctx: EContext) {
        assertVersion3(db, ctx)

        assertArrayEquals(configurationHash(configData1),
                db.queryRunner.query(ctx.conn, "SELECT configuration_hash FROM ${db.tableConfigurations(ctx)} WHERE height=0",
                        db.byteArrayRes))

        assertArrayEquals(configurationHash(configData2),
                db.queryRunner.query(ctx.conn, "SELECT configuration_hash FROM ${db.tableConfigurations(ctx)} WHERE height=10",
                        db.byteArrayRes))
    }

    @Test
    fun testError_When_DowngradeDbVersionFrom2To1() {
        StorageBuilder.buildStorage(appConfig, wipeDatabase = true, expectedDbVersion = 2)

        Assertions.assertThrows(UserMistake::class.java) {
            StorageBuilder.buildStorage(appConfig, wipeDatabase = false, expectedDbVersion = 1)
        }
    }
}
