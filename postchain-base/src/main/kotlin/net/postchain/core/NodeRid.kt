package net.postchain.core

import net.postchain.common.data.ByteArrayKey

/**
 * This is the public key of the node.
 *
 * Note: Sometimes a "node" is also a "peer", but doesn't have to be.
 *
 * (The type [ByteArrayKey] has everything we need)
 */
typealias NodeRid = ByteArrayKey
