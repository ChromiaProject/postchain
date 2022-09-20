package net.postchain.managed

import net.postchain.common.BlockchainRid
import net.postchain.config.app.AppConfig
import net.postchain.containers.bpm.ContainerResourceLimits
import net.postchain.containers.bpm.ContainerResourceLimits.ResourceLimit
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.core.block.BlockQueries
import net.postchain.crypto.PubKey
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.crypto.secp256k1_derivePubKey
import net.postchain.gtv.GtvFactory
import net.postchain.managed.directory1.D1ClusterInfo
import net.postchain.managed.directory1.D1PeerInfo

class BaseDirectoryDataSource(
        queries: BlockQueries,
        appConfig: AppConfig,
        private val containerNodeConfig: ContainerNodeConfig?
) : BaseManagedNodeDataSource(queries, appConfig),
        DirectoryDataSource {

    override fun getContainersToRun(): List<String>? {
        val res = queries.query(
                "nm_get_containers",
                buildArgs("pubkey" to GtvFactory.gtv(appConfig.pubKeyByteArray))
        ).get()

        return res.asArray().map { it.asString() }
    }

    override fun getBlockchainsForContainer(containerId: String): List<BlockchainRid>? {
        val res = queries.query(
                "nm_get_blockchains_for_container",
                buildArgs("container_id" to GtvFactory.gtv(containerId))
        ).get()

        return res.asArray().map { BlockchainRid(it.asByteArray()) }
    }

    override fun getContainerForBlockchain(brid: BlockchainRid): String {
        return if (containerNodeConfig?.testmode == true) {
            val short = brid.toHex().uppercase().take(8)
            containerNodeConfig.testmodeDappsContainers[short] ?: "cont0"
        } else {
            if (nmApiVersion >= 3) {
                queries.query(
                        "nm_get_container_for_blockchain",
                        buildArgs("blockchain_rid" to GtvFactory.gtv(brid.data))
                ).get().asString()
            } else {
                throw Exception("Directory1 v.{$nmApiVersion} doesn't support 'nm_get_container_for_blockchain' query")
            }
        }
    }

    // TODO: [POS-344]: directory vs containerId?
    override fun getResourceLimitForContainer(containerId: String): ContainerResourceLimits {
        return if (containerNodeConfig?.testmode == true) {
            ContainerResourceLimits.fromValues(
                    containerNodeConfig.testmodeResourceLimitsRAM,
                    containerNodeConfig.testmodeResourceLimitsCPU,
                    containerNodeConfig.testmodeResourceLimitsSTORAGE
            )
        } else {
            val resourceLimits = queries.query(
                    "nm_get_container_limits",
                    buildArgs("name" to GtvFactory.gtv(containerId))
            ).get().asDict()
                    .map { ResourceLimit.valueOf(it.key.uppercase()) to it.value.asInteger() }
                    .toMap()

            ContainerResourceLimits(resourceLimits)
        }
    }

    override fun setLimitsForContainer(containerId: String, ramLimit: Long, cpuQuota: Long) {
        TODO("Will not be used")
    }

    override fun getAllClusters(): List<D1ClusterInfo> {
        return if (containerNodeConfig?.testmode == true) {
            val cs = Secp256K1CryptoSystem()

            val peer0 = D1PeerInfo("http://127.0.0.1:7740/", PubKey(secp256k1_derivePubKey(cs.getRandomBytes(32))))
            val peer1 = D1PeerInfo("http://127.0.0.1:7741/", PubKey(secp256k1_derivePubKey(cs.getRandomBytes(32))))
            val peer2 = D1PeerInfo("http://127.0.0.1:7742/", PubKey(secp256k1_derivePubKey(cs.getRandomBytes(32))))

            listOf(
                    D1ClusterInfo("s1", BlockchainRid.buildRepeat(1), setOf(peer0, peer1)),
                    D1ClusterInfo("s2", BlockchainRid.buildRepeat(2), setOf(peer0, peer2)),
            )

        } else {
            // TODO: [POS-344]: Check NP_API or D1_API version here. We return an EMPTY for now.
            // if (nmApiVersion >= 3) { ... }
            emptyList()
        }
    }
}
