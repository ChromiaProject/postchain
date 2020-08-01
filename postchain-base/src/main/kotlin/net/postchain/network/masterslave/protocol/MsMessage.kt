package net.postchain.network.masterslave.protocol

import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory

interface MsMessage {
    /**
     * TODO: [POS-129]: Finish it
     */
    val type: Int

    /**
     * TODO: [POS-129]: Finish it
     */
    val source: ByteArray

    /**
     * TODO: [POS-129]: Finish it
     */
    val destination: ByteArray

    /**
     * TODO: [POS-129]: Finish it
     */
    val blockchainRid: ByteArray

    /**
     * TODO: [POS-129]: Finish it
     */
    val messageData: ByteArray
}

class HandshakeMsMessage(
        override val blockchainRid: ByteArray,
        override val messageData: ByteArray
) : MsMessage {
    override val type = 0
    override val source: ByteArray = byteArrayOf()
    override val destination: ByteArray = byteArrayOf()
    val signers: List<ByteArray> = decodeSingers(messageData)

    companion object {

        // TODO: [POS-129]: Use 2nd constructor
        fun build(blockchainRid: ByteArray, signers: List<ByteArray>): HandshakeMsMessage {
            return HandshakeMsMessage(blockchainRid, encodeSingers(signers))
        }

        fun decodeSingers(bytes: ByteArray): List<ByteArray> =
                GtvDecoder.decodeGtv(bytes).asArray().map { it.asByteArray() }

        fun encodeSingers(singers: List<ByteArray>): ByteArray {
            val gtv = singers.map { GtvFactory.gtv(it) }.let { GtvFactory.gtv(it) }
            return GtvEncoder.encodeGtv(gtv)
        }
    }
}

class DataMsMessage(
        override val source: ByteArray,
        override val destination: ByteArray,
        override val blockchainRid: ByteArray,
        override val messageData: ByteArray
) : MsMessage {
    override val type = 1
}