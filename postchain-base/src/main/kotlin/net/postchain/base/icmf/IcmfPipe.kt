package net.postchain.base.icmf

import net.postchain.base.BlockchainRelatedInfo
import java.lang.IllegalStateException
import java.util.*


/**
 * Transports messages in a "pipe", asynchronously.
 * A [IcmfPipe] only handles point-2-point connections, with one "source chain" and one "listener chain" only.
 * (Example: If we have 3 "listener chains" that listen to 2 "source chains" we must have 6 pipes in total.
 *           Messages coming from a source chain get duplicated in 3 pipes, which shouldn't consume memory
 *           if we re-use the same message instance for all 3 pipes. This is safe if the message is immutable.)
 *
 * Messages will be pushed into the pipe by one party [IcmfDispatcher], and extracted from the "other end"
 * by another party [IcmfReceiver] when the receiving party is ready (this way it's asynchronous)
 *
 * Note: re lost messages:
 * // TODO: Olle: impl
 * If server goes down we lose messages, but it shouldn't matter since we'll only get a gap in the block height
 * sequence and next time a block is built we will take the missing block too
 */
class IcmfPipe(
    val sourceChainIid: Long, // Where packages coming from
    val listenerChainInfo: BlockchainRelatedInfo  // Where packages are going, we might only have the BcRID in the beginning
) {
    val pipeId = "$sourceChainIid-${listenerToString()}" // This id will probably be unique in the system, for debugging
    private val packetQueue: Queue<IcmfPackage> = LinkedList() // Hope Java's LinkedList impl is efficient as Queue?

    fun listenerToString(): String {
        return if (listenerChainInfo.chainId != null) {
            "${listenerChainInfo.chainId}"
        } else {
            "${listenerChainInfo.blockchainRid.toShortHex()}"
        }
    }

    @Synchronized
    fun push(newPkg: IcmfPackage) {
        val newHeight = newPkg.height
        // We expect packages (from blocks) to be pushed in the correct order
        if (!isEmpty()) {
            for (oldPkg in packetQueue) {
                val oldHeight = oldPkg.height
                if (oldHeight >= newHeight) {
                    throw IllegalStateException("Why did we push height: $newPkg when we have $oldPkg in the pipe? PipeId: $pipeId")
                }
            }
        }
        // TODO: Olle: Overflow check?  What if the receiving chain is down?
        packetQueue.add(newPkg) // Since we pull the first we put new package last
    }


    /**
     * Regarding persistence:
     *
     * Currently we always fetch packages from DB, since we might have suffered a restart and the in memory data might
     * not be 100% correct. (Task for the future: only fetch from DB when we have reason to believe data got lost).
     */
    @Synchronized
    fun pull(): IcmfPackage? {
        val first = packetQueue.remove()
        // TODO: Validate with DB?
        return first
    }

    @Synchronized
    fun isEmpty() = packetQueue.isEmpty()

}
