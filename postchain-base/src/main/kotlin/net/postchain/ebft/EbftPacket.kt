// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft

import net.postchain.core.BlockchainRid
import net.postchain.base.PeerCommConfiguration
import net.postchain.common.toHex
import net.postchain.core.UserMistake
import net.postchain.ebft.message.Identification
import net.postchain.ebft.message.EbftMessage
import net.postchain.ebft.message.SignedMessage
import net.postchain.core.NodeRid
import net.postchain.network.*

class EbftPacketEncoder(val config: PeerCommConfiguration, val blockchainRID: BlockchainRid) : XPacketEncoder<EbftMessage> {

    override fun makeIdentPacket(forNode: NodeRid): ByteArray {
        val bytes = Identification(forNode.byteArray, blockchainRID, System.currentTimeMillis()).encode()
        val sigMaker = config.sigMaker()
        val signature = sigMaker.signMessage(bytes)
        return SignedMessage(bytes, config.pubKey, signature.data).encode()
    }

    override fun encodePacket(packet: EbftMessage): ByteArray {
        return encodeAndSign(packet, config.sigMaker())
    }
}

class EbftPacketEncoderFactory : XPacketEncoderFactory<EbftMessage> {

    override fun create(config: PeerCommConfiguration, blockchainRID: BlockchainRid): XPacketEncoder<EbftMessage> {
        return EbftPacketEncoder(config, blockchainRID)
    }
}

class EbftPacketDecoder(val config: PeerCommConfiguration) : XPacketDecoder<EbftMessage> {

    override fun parseIdentPacket(bytes: ByteArray): IdentPacketInfo {
        val signedMessage = decodeSignedMessage(bytes)
        val message = decodeAndVerify(bytes, signedMessage.pubKey, config.verifier())

        if (message !is Identification) {
            throw UserMistake("Packet was not an Identification. Got ${message::class}")
        }

        if (!config.pubKey.contentEquals(message.pubKey)) {
            throw UserMistake("'yourPubKey' ${message.pubKey.toHex()} of Identification is not mine")
        }

        return IdentPacketInfo(NodeRid(signedMessage.pubKey), message.blockchainRID, null)
    }

    override fun decodePacket(pubKey: ByteArray, bytes: ByteArray): EbftMessage {
        return decodeAndVerify(bytes, pubKey, config.verifier())
    }

    override fun decodePacket(bytes: ByteArray): EbftMessage? {
        return decodeAndVerify(bytes, config.verifier())
    }

    // TODO: [et]: Improve the design
    override fun isIdentPacket(bytes: ByteArray): Boolean {
        return decodePacket(bytes) is Identification
    }
}

class EbftPacketDecoderFactory : XPacketDecoderFactory<EbftMessage> {

    override fun create(config: PeerCommConfiguration): XPacketDecoder<EbftMessage> {
        return EbftPacketDecoder(config)
    }
}