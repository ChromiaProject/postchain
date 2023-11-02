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
import net.postchain.ebft.message.EBFT_VERSION
import net.postchain.ebft.message.EbftMessage
import net.postchain.ebft.message.EbftVersion
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
        val signature = sigMaker.signMessage(idMessage.encoded(1).value)
        return SignedMessage(idMessage, config.pubKey, signature.data).encoded(1).value
    }

    override fun makeVersionPacket(): ByteArray {
        val versionMessage = EbftVersion(EBFT_VERSION)
        val sigMaker = config.sigMaker()
        val signature = sigMaker.signMessage(versionMessage.encoded(1).value)
        return SignedMessage(versionMessage, config.pubKey, signature.data).encoded(1).value
    }

    override fun encodePacket(packet: EbftMessage, packetVersion: Long): ByteArray {
        val signature = config.sigMaker().signMessage(packet.encoded(packetVersion).value)
        return SignedMessage(packet, signature.subjectID, signature.data).encoded(packetVersion).value
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
        val signedMessage = decodeSignedMessage(rawMessage, 1)
        val message = verifySignedMessage(signedMessage, signedMessage.pubKey, 1)

        if (message !is Identification) {
            throw UserMistake("Packet was not an Identification. Got ${message::class}")
        }

        if (!config.pubKey.contentEquals(message.pubKey)) {
            throw UserMistake("'yourPubKey' ${message.pubKey.toHex()} of Identification is not mine")
        }

        return IdentPacketInfo(NodeRid(signedMessage.pubKey), message.blockchainRID, null)
    }

    override fun parseVersionPacket(rawMessage: ByteArray): Long {
        val signedMessage = decodeSignedMessage(rawMessage, 1)
        val message = verifySignedMessage(signedMessage, signedMessage.pubKey, 1)
        if (message !is EbftVersion) {
            throw UserMistake("Packet was not an EbftVersion. Got ${message::class}")
        }
        return message.version
    }

    override fun decodePacket(pubKey: ByteArray, rawMessage: ByteArray, packetVersion: Long): EbftMessage {
        val signedMessage = decodeSignedMessage(rawMessage, packetVersion)
        return when (signedMessage.message) {
            is Status -> statusCache.get(pubKey, rawMessage, signedMessage.topic)
                    ?: statusCache.put(pubKey, rawMessage, verifySignedMessage(signedMessage, pubKey, packetVersion))

            is AppliedConfig -> appliedConfigCache.get(pubKey, rawMessage, signedMessage.topic)
                    ?: appliedConfigCache.put(pubKey, rawMessage, verifySignedMessage(signedMessage, pubKey, packetVersion))

            else -> verifySignedMessage(signedMessage, pubKey, packetVersion)
        }
    }

    override fun isIdentPacket(rawMessage: ByteArray): Boolean =
            decodeSignedMessage(rawMessage, 1).message is Identification

    override fun isVersionPacket(rawMessage: ByteArray): Boolean =
            decodeSignedMessage(rawMessage, 1).message is EbftVersion

    @TestOnly
    override fun decodePacket(rawMessage: ByteArray, packetVersion: Long): EbftMessage? {
        return decodeAndVerify(rawMessage, packetVersion)
    }

    private fun decodeSignedMessage(rawMessage: ByteArray, ebftVersion: Long): SignedMessage {
        return SignedMessage.decode(rawMessage, ebftVersion)
    }

    private fun verifySignedMessage(message: SignedMessage, pubKey: ByteArray, ebftVersion: Long): EbftMessage {
        val verified = message.pubKey.contentEquals(pubKey)
                && config.verifier()(message.message.encoded(ebftVersion).value, Signature(message.pubKey, message.signature))
        return if (verified) message.message else throw UserMistake("Verification failed")
    }

    private fun decodeAndVerify(rawMessage: ByteArray, ebftVersion: Long): EbftMessage? {
        val message = SignedMessage.decode(rawMessage, ebftVersion)
        val verified = config.verifier()(message.message.encoded(ebftVersion).value, Signature(message.pubKey, message.signature))
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
