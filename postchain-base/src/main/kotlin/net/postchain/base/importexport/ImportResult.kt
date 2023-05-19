package net.postchain.base.importexport

import net.postchain.common.BlockchainRid

data class ImportResult(val fromHeight: Long, val toHeight: Long, val numBlocks: Long, val blockchainRid: BlockchainRid)
