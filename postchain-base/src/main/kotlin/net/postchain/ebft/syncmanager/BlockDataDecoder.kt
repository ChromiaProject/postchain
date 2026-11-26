// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft.syncmanager

import net.postchain.core.BlockchainConfiguration
import net.postchain.core.block.BlockData
import net.postchain.core.block.BlockDataWithWitness
import net.postchain.ebft.message.CompleteBlock
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv

object BlockDataDecoder {

    fun decodeBlockDataWithWitness(block: CompleteBlock, blockchainConfig: BlockchainConfiguration)
            : BlockDataWithWitness {
        val header = blockchainConfig.decodeBlockHeader(block.data.header)
        val witness = blockchainConfig.decodeWitness(block.witness)
        return BlockDataWithWitness(header, block.data.transactions, witness)
    }

    fun decodeBlockData(block: net.postchain.ebft.message.BlockData, blockchainConfig: BlockchainConfiguration)
            : BlockData {
        val header = blockchainConfig.decodeBlockHeader(block.header)
        return BlockData(header, block.transactions)
    }

    fun encodeBlockData(block: BlockData): ByteArray = GtvEncoder.encodeGtv(
            gtv(
                    gtv(block.header.rawData),
                    gtv(block.transactions.map { gtv(it) })
            )
    )

    fun decodeEncodedBlockData(bytes: ByteArray, blockchainConfig: BlockchainConfiguration): BlockData? = try {
        val array = GtvDecoder.decodeGtv(bytes).asArray()
        if (array.size != 2) {
            null
        } else {
            val headerBytes = array[0].asByteArray()
            val txBytes = array[1].asArray().map { it.asByteArray() }
            decodeBlockData(net.postchain.ebft.message.BlockData(headerBytes, txBytes), blockchainConfig)
        }
    } catch (_: Exception) {
        null
    }
}