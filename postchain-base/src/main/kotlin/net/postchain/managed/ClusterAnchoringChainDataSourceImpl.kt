package net.postchain.managed

import net.postchain.gtv.GtvFactory
import net.postchain.managed.query.QueryRunner

class ClusterAnchoringChainDataSourceImpl(val queryRunner: QueryRunner) : ClusterAnchoringChainDataSource, QueryRunner by queryRunner{

    override val nmApiVersion by lazy {
        query("nm_api_version", GtvFactory.gtv(mapOf())).asInteger().toInt()
    }


    override fun isBlockAnchored(blockchainRid: ByteArray, blockRid: ByteArray): Boolean {
        if (nmApiVersion < 44) return true

        val res = query(
                "is_block_anchored",
                GtvFactory.gtv(
                        "blockchain_rid" to GtvFactory.gtv(blockchainRid),
                        "block_rid" to GtvFactory.gtv(blockRid)
                )
        )
        if (res.isNull()) return true

        return res.asBoolean()
    }
}