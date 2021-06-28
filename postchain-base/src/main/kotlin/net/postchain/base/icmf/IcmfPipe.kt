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
    val sourceChainIid: Long,
    val targetChainIid: Long
) {
    private val list: LinkedList<Long> = LinkedList()

    @Synchronized
    fun pushHeight(newHeight: Long) {
        // We expect heights to be pushed in the correct order
        if (!isEmpty()) {
            for (oldHeight in list) {
                if (oldHeight >= newHeight) {
                    throw IllegalStateException("Why did we push height: $newHeight when we have $oldHeight in the pipe?")
                }
            }
        }
        list.addLast(newHeight) // Since we pull the first we put new messages last
    }


    @Synchronized
    fun pullHeight(): Long? {
        return list.pollFirst()
    }

    @Synchronized
    fun isEmpty() = list.isEmpty()

}
