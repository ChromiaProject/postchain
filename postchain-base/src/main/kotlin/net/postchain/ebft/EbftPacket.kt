// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft

import mu.KLogging
import net.postchain.base.PeerCommConfiguration
import net.postchain.common.BlockchainRid
import net.postchain.common.toHex
import net.postchain.common.types.WrappedByteArray
import net.postchain.core.BadMessageException
import net.postchain.core.NodeRid
import net.postchain.crypto.Signature
import net.postchain.ebft.message.AppliedConfig
import net.postchain.ebft.message.EbftMessage
import net.postchain.ebft.message.EbftVersion
import net.postchain.ebft.message.Identification
import net.postchain.ebft.message.MessageTopic
import net.postchain.ebft.message.SignedMessage
import net.postchain.ebft.message.Status
import net.postchain.network.IdentPacketInfo
import net.postchain.network.XPacketCodec
import net.postchain.network.XPacketCodecFactory
import org.jetbrains.annotations.TestOnly

// Whenever an Ebft message is changed, in a non-backward compatible way,
// bump the version and handle old versions accordingly
const val EBFT_VERSION: Long = 3

class EbftPacketCodec(
        val config: PeerCommConfiguration,
        val blockchainRID: BlockchainRid,
        private val clock: () -> Long = System::currentTimeMillis
) : XPacketCodec<EbftMessage> {

    companion object {
        /**
         * How far the timestamp of a received Identification may be from our own clock, in either direction.
         * Limits how long a captured Identification packet can be replayed to open a connection as that peer.
         */
        const val IDENT_PACKET_MAX_CLOCK_SKEW_MS: Long = 10 * 60 * 1000L
    }

    private val statusCache = EbftPacketCache()
    private val appliedConfigCache = EbftPacketCache()

    override fun getPacketVersion(): Long = EBFT_VERSION

    override fun makeIdentPacket(forNode: NodeRid): ByteArray {
        val idMessage = Identification(forNode.data, blockchainRID, clock())
        val sigMaker = config.sigMaker()
        val signature = sigMaker.signMessage(idMessage.encoded(1).value)
        return SignedMessage(idMessage, config.pubKey, signature.data).encoded(1).value
    }

    override fun makeVersionPacket(): ByteArray {
        val versionMessage = EbftVersion(getPacketVersion())
        val sigMaker = config.sigMaker()
        val signature = sigMaker.signMessage(versionMessage.encoded(1).value)
        return SignedMessage(versionMessage, config.pubKey, signature.data).encoded(1).value
    }

    override fun encodePacket(packet: EbftMessage, packetVersion: Long): ByteArray {
        val signature = config.sigMaker().signMessage(packet.encoded(packetVersion).value)
        return SignedMessage(packet, signature.subjectID, signature.data).encoded(packetVersion).value
    }

    override fun parseIdentPacket(rawMessage: ByteArray): IdentPacketInfo {
        val signedMessage = decodeSignedMessage(rawMessage, 1)
        val message = verifySignedMessage(signedMessage, signedMessage.pubKey, 1)

        if (message !is Identification) {
            throw BadMessageException("Packet was not an Identification. Got ${message::class}")
        }

        if (!config.pubKey.contentEquals(message.pubKey)) {
            throw BadMessageException("'yourPubKey' ${message.pubKey.toHex()} of Identification is not mine")
        }

        val skew = Math.abs(clock() - message.timestamp)
        if (skew > IDENT_PACKET_MAX_CLOCK_SKEW_MS) {
            throw BadMessageException(
                    "Identification timestamp ${message.timestamp} is $skew ms away from local time")
        }

        return IdentPacketInfo(NodeRid(signedMessage.pubKey), message.blockchainRID, null)
    }

    override fun parseVersionPacket(rawMessage: ByteArray): Long {
        val signedMessage = decodeSignedMessage(rawMessage, 1)
        val message = verifySignedMessage(signedMessage, signedMessage.pubKey, 1)
        if (message !is EbftVersion) {
            throw BadMessageException("Packet was not an EbftVersion. Got ${message::class}")
        }
        return message.ebftVersion
    }

    override fun getVersionFromVersionPacket(packet: EbftMessage): Long {
        if (packet !is EbftVersion) {
            throw BadMessageException("Packet was not an EbftVersion. Got ${packet::class}")
        }
        return packet.ebftVersion
    }

    override fun decodePacket(pubKey: WrappedByteArray, rawMessage: ByteArray, packetVersion: Long): EbftMessage {
        val signedMessage = decodeSignedMessage(rawMessage, packetVersion)
        return when (signedMessage.message) {
            is Status -> statusCache.get(pubKey, rawMessage, signedMessage.topic)
                    ?: statusCache.put(pubKey, rawMessage, verifySignedMessage(signedMessage, pubKey.data, packetVersion))

            is AppliedConfig -> appliedConfigCache.get(pubKey, rawMessage, signedMessage.topic)
                    ?: appliedConfigCache.put(pubKey, rawMessage, verifySignedMessage(signedMessage, pubKey.data, packetVersion))

            is EbftVersion -> verifySignedMessage(signedMessage, pubKey.data, 1)
            else -> verifySignedMessage(signedMessage, pubKey.data, packetVersion)
        }
    }

    override fun isIdentPacket(rawMessage: ByteArray): Boolean =
            decodeSignedMessage(rawMessage, 1).message is Identification

    override fun isVersionPacket(rawMessage: ByteArray): Boolean =
            decodeSignedMessage(rawMessage, 1).message is EbftVersion

    override fun isVersionPacket(packet: EbftMessage): Boolean = packet is EbftVersion

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
        return if (verified) message.message else throw BadMessageException("Verification failed")
    }

    private fun decodeAndVerify(rawMessage: ByteArray, ebftVersion: Long): EbftMessage? {
        val message = SignedMessage.decode(rawMessage, ebftVersion)
        val verified = config.verifier()(message.message.encoded(ebftVersion).value, Signature(message.pubKey, message.signature))
        return if (verified) message.message else null
    }
}

class EbftPacketCodecFactory : XPacketCodecFactory<EbftMessage> {
    override fun create(config: PeerCommConfiguration, blockchainRID: BlockchainRid): XPacketCodec<EbftMessage> {
        return EbftPacketCodec(config, blockchainRID)
    }
}

class EbftPacketCache(
        private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
        private val maxBytes: Int = DEFAULT_MAX_BYTES,
        private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
        private val clock: () -> Long = System::currentTimeMillis
) {

    private class Entry(
            val rawMessage: ByteArray,
            val message: EbftMessage,
            val timestamp: Long
    ) {
        val size = rawMessage.size
    }

    private val cache = LinkedHashMap<WrappedByteArray, Entry>(16, 0.75f, true)
    private var totalBytes = 0

    companion object : KLogging() {
        const val DEFAULT_MAX_ENTRIES = 1_024
        const val DEFAULT_MAX_BYTES = 32 * 1024 * 1024
        const val DEFAULT_TTL_MILLIS = 10 * 60 * 1000L
    }

    @Synchronized
    fun get(pubKey: WrappedByteArray, rawMessage: ByteArray, topic: MessageTopic): EbftMessage? {
        evictExpired()
        val message = cache[pubKey]?.takeIf { it.rawMessage.contentEquals(rawMessage) }?.message
        if (logger.isTraceEnabled) {
            if (message != null) {
                logger.trace { "Cache HIT for pubKey $pubKey and topic $topic" }
            } else {
                logger.trace { "Cache MISS for pubKey $pubKey and topic $topic" }
            }
        }
        return message
    }

    @Synchronized
    fun put(pubKey: WrappedByteArray, rawMessage: ByteArray, message: EbftMessage): EbftMessage {
        if (rawMessage.size > maxBytes) return message
        evictExpired()
        cache.remove(pubKey)?.also { totalBytes -= it.size }
        cache[pubKey] = Entry(rawMessage, message, clock())
        totalBytes += rawMessage.size
        evictOverflow()
        return message
    }

    private fun evictExpired() {
        val now = clock()
        val iterator = cache.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value.timestamp > ttlMillis) {
                totalBytes -= entry.value.size
                iterator.remove()
            }
        }
    }

    private fun evictOverflow() {
        val iterator = cache.entries.iterator()
        while ((cache.size > maxEntries || totalBytes > maxBytes) && iterator.hasNext()) {
            val entry = iterator.next()
            totalBytes -= entry.value.size
            iterator.remove()
        }
    }
}