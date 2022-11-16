package net.postchain.core

import net.postchain.common.types.WrappedByteArray

/**
 * This is the public key of the node.
 *
 * Note: Sometimes a "node" is also a "peer", but doesn't have to be.
 */
typealias NodeRid = WrappedByteArray
