// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft

import mu.KLogging
import net.postchain.base.PeerCommConfiguration
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.toHex
import net.postchain.core.NodeRid
import net.postchain.crypto.Signature
import net.postchain.ebft.message.AppliedConfig
import net.postchain.ebft.message.EbftMessage
import net.postchain.ebft.message.Identification
import net.postchain.ebft.message.MessageTopic
import net.postchain.ebft.message.SignedMessage
import net.postchain.ebft.message.Status
import net.postchain.network.IdentPacketInfo
import net.postchain.network.XPacketDecoder
import net.postchain.network.XPacketDecoderFactory
import net.postchain.network.XPacketEncoder
import net.postchain.network.XPacketEncoderFactory
import org.jetbrains.annotations.TestOnly

class EbftPacketEncoder(val config: PeerCommConfiguration, val blockchainRID: BlockchainRid) : XPacketEncoder<EbftMessage> {

    override fun makeIdentPacket(forNode: NodeRid): ByteArray {
        val idMessage = Identification(forNode.data, blockchainRID, System.currentTimeMillis())
        val sigMaker = config.sigMaker()
        val signature = sigMaker.signMessage(idMessage.encoded)
        return SignedMessage(idMessage, config.pubKey, signature.data).encoded
    }

    override fun encodePacket(packet: EbftMessage): ByteArray {
        val signature = config.sigMaker().signMessage(packet.encoded)
        return SignedMessage(packet, signature.subjectID, signature.data).encoded
    }
}

class EbftPacketEncoderFactory : XPacketEncoderFactory<EbftMessage> {

    override fun create(config: PeerCommConfiguration, blockchainRID: BlockchainRid): XPacketEncoder<EbftMessage> {
        return EbftPacketEncoder(config, blockchainRID)
    }
}

class EbftPacketDecoder(val config: PeerCommConfiguration) : XPacketDecoder<EbftMessage> {

    private val statusCache = EbftPacketCache()
    private val appliedConfigCache = EbftPacketCache()

    override fun parseIdentPacket(rawMessage: ByteArray): IdentPacketInfo {
        val signedMessage = decodeSignedMessage(rawMessage)
        val message = verifySignedMessage(signedMessage, signedMessage.pubKey)

        if (message !is Identification) {
            throw UserMistake("Packet was not an Identification. Got ${message::class}")
        }

        if (!config.pubKey.contentEquals(message.pubKey)) {
            throw UserMistake("'yourPubKey' ${message.pubKey.toHex()} of Identification is not mine")
        }

        return IdentPacketInfo(NodeRid(signedMessage.pubKey), message.blockchainRID, null)
    }

    override fun decodePacket(pubKey: ByteArray, rawMessage: ByteArray): EbftMessage {
        val signedMessage = decodeSignedMessage(rawMessage)
        return when (signedMessage.message) {
            is Status -> statusCache.get(pubKey, rawMessage, signedMessage.topic)
                    ?: statusCache.put(pubKey, rawMessage, verifySignedMessage(signedMessage, pubKey))

            is AppliedConfig -> appliedConfigCache.get(pubKey, rawMessage, signedMessage.topic)
                    ?: appliedConfigCache.put(pubKey, rawMessage, verifySignedMessage(signedMessage, pubKey))

            else -> verifySignedMessage(signedMessage, pubKey)
        }
    }

    override fun isIdentPacket(rawMessage: ByteArray): Boolean {
        return decodeSignedMessage(rawMessage).message is Identification
    }

    @TestOnly
    override fun decodePacket(rawMessage: ByteArray): EbftMessage? {
        return decodeAndVerify(rawMessage)
    }

    private fun decodeSignedMessage(rawMessage: ByteArray): SignedMessage {
        return SignedMessage.decode(rawMessage)
    }

    private fun verifySignedMessage(message: SignedMessage, pubKey: ByteArray): EbftMessage {
        val verified = message.pubKey.contentEquals(pubKey)
                && config.verifier()(message.message.encoded, Signature(message.pubKey, message.signature))
        return if (verified) message.message else throw UserMistake("Verification failed")
    }

    private fun decodeAndVerify(rawMessage: ByteArray): EbftMessage? {
        val message = SignedMessage.decode(rawMessage)
        val verified = config.verifier()(message.message.encoded, Signature(message.pubKey, message.signature))
        return if (verified) message.message else null
    }
}

class EbftPacketCache {

    private val cache = mutableMapOf<ByteArray, Pair<ByteArray, EbftMessage>>()

    companion object : KLogging()

    fun get(pubKey: ByteArray, rawMessage: ByteArray, topic: MessageTopic): EbftMessage? {
        val message = cache[pubKey]?.takeIf { it.first.contentEquals(rawMessage) }?.second
        if (logger.isTraceEnabled) {
            if (message != null) {
                logger.trace { "Cache HIT for pubKey ${pubKey.toHex()} and topic $topic" }
            } else {
                logger.trace { "Cache MISS for pubKey ${pubKey.toHex()} and topic $topic" }
            }
        }
        return message
    }

    fun put(pubKey: ByteArray, rawMessage: ByteArray, message: EbftMessage): EbftMessage {
        cache[pubKey] = rawMessage to message
        return message
    }
}

class EbftPacketDecoderFactory : XPacketDecoderFactory<EbftMessage> {

    override fun create(config: PeerCommConfiguration): XPacketDecoder<EbftMessage> {
        return EbftPacketDecoder(config)
    }
}
