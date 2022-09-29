package net.postchain.d1

import net.postchain.core.EContext
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvInteger
import net.postchain.gtv.GtvNull
import net.postchain.gtx.SimpleGTXModule

class Chain0TestGTXModule : SimpleGTXModule<Unit>(Unit, mapOf(), mapOf(
        "nm_api_version" to ::queryNMApiVersion,
        "nm_get_peer_infos" to ::queryGetPeerInfos,
        "nm_get_peer_list_version" to ::queryGetPeerListVersion,
        "nm_compute_blockchain_list" to ::queryComputeBlockchainList,
        "nm_get_blockchain_configuration" to ::queryGetConfiguration,
        "nm_find_next_configuration_height" to ::queryFindNextConfigurationHeight,
        "nm_get_blockchain_replica_node_map" to ::dummyHandlerArray,
        "nm_get_node_replica_map" to ::dummyHandlerArray
)) {
    override fun initializeDB(ctx: EContext) {}

    companion object {
        fun queryNMApiVersion(unit: Unit, eContext: EContext, args: Gtv): Gtv {
            return GtvInteger(2L)
        }

        fun queryGetPeerInfos(unit: Unit, eContext: EContext, args: Gtv): Gtv {
            return GtvArray(arrayOf())
        }

        fun queryGetPeerListVersion(unit: Unit, eContext: EContext, args: Gtv): Gtv {
            return GtvInteger(1L)
        }

        fun queryComputeBlockchainList(unit: Unit, eContext: EContext, args: Gtv): Gtv {
            return GtvArray(arrayOf())
        }

        fun queryGetConfiguration(unit: Unit, eContext: EContext, args: Gtv): Gtv {
            return GtvNull
        }

        fun queryFindNextConfigurationHeight(unit: Unit, eContext: EContext, args: Gtv): Gtv {
            return GtvNull
        }

        fun dummyHandlerArray(target: Unit, eContext: EContext, args: Gtv): Gtv {
            return GtvArray(emptyArray())
        }
    }
}
