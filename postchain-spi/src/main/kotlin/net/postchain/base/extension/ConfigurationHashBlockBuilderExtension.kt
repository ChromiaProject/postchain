package net.postchain.base.extension

import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.base.BaseBlockHeader
import net.postchain.base.data.BaseBlockBuilder
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.core.BlockEContext
import net.postchain.core.block.BlockHeader
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv

const val MERKLE_HASH_VERSION_EXTRA_HEADER = "merkle_hash_version"
const val CONFIG_HASH_EXTRA_HEADER = "config_hash"

class ConfigurationHashBlockBuilderExtension(private val merkleHashVersion: Long, private val configHash: ByteArray)
    : BaseBlockBuilderExtension {

    override fun init(blockEContext: BlockEContext, baseBB: BaseBlockBuilder) {}

    override fun finalize(): Map<String, Gtv> = mutableMapOf<String, Gtv>(
            CONFIG_HASH_EXTRA_HEADER to gtv(configHash)
    ).also {
        if (merkleHashVersion > 1) {
            it[MERKLE_HASH_VERSION_EXTRA_HEADER] = gtv(merkleHashVersion)
        }
    }
}

fun BlockHeader.getMerkleHashVersion(): Long =
        getExtraData()[MERKLE_HASH_VERSION_EXTRA_HEADER]?.asInteger() ?: 1

fun BlockHeader.getConfigHash(): ByteArray? =
        getExtraData()[CONFIG_HASH_EXTRA_HEADER]?.asByteArray()

fun BlockHeader.getFailedConfigHash(): ByteArray? =
        getExtraData()[FAILED_CONFIG_HASH_EXTRA_HEADER]?.asByteArray()

fun BlockHeader.getExtraData(): Map<String, Gtv> =
        ((this as? BaseBlockHeader)?.extraData ?: BlockHeaderData.fromBinary(this.rawData).getExtra())

fun BlockHeaderData.getMerkleHashVersion(): Long = getExtra()[MERKLE_HASH_VERSION_EXTRA_HEADER]?.asInteger() ?: 1
