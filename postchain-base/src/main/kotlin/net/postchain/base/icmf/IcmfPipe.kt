package net.postchain.base.icmf

import java.lang.IllegalStateException
import java.util.*


/**
 * Transports new-block-height-messages in a "pipe", asynchronously.
 *
 * Heights will be pushed into the pipe by one party [IcmfDispatcher], and extracted from the "other end"
 * by another party [IcmfReceiver] when the receiving party is ready (this way it's asynchronous)
 *
 * Note: re lost messages:
 * If server goes down we lose messages, but it shouldn't matter since we'll only get a gap in the block height
 * sequence and next time a block is built we will take the missing block too
 */
class IcmfPipe(
    val sourceChainIid: Long, // Where packages coming from
    val targetChainIid: Long  // Where packages are going
) {
    private val list: LinkedList<IcmfPackage> = LinkedList()

    @Synchronized
    fun push(newPkg: IcmfPackage) {
        val newHeight = newPkg.height
        // We expect packages (from blocks) to be pushed in the correct order
        if (!isEmpty()) {
            for (oldPkg in list) {
                val oldHeight = oldPkg.height
                if (oldHeight >= newHeight) {
                    throw IllegalStateException("Why did we push height: $newPkg when we have $oldPkg in the pipe?")
                }
            }
        }
        list.addLast(newPkg) // Since we pull the first we put new package last
    }


    /**
     * Regarding persistence:
     *
     * Currently we always fetch packages from DB, since we might have suffered a restart and the in memory data might
     * not be 100% correct. (Task for the future: only fetch from DB when we have reason to believe data got lost).
     */
    @Synchronized
    fun pull(): IcmfPackage? {
        val first = list.pollFirst()
        val height = first.height

    }

    @Synchronized
    fun isEmpty() = list.isEmpty()

}
