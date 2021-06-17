package net.postchain.base.icmf

import java.util.*


/**
 * Stores [IcmfMessage]s in a "pipe"
 *
 * Messages will be pushed into the pipe by one party,
 * and extracted from the "other end" by another party.
 *
 * Impl:
 * Initially we just use a linked list.
 *
 * TODO: If server goes down we lose messages
 */
class IcmfMessagePipe {
    private val list: LinkedList<IcmfMessage> = LinkedList()

    fun pushMessage(msg: IcmfMessage) {
        list.add(msg)
    }

    fun pullMessage(): IcmfMessage? {
        return list.first
    }

    fun isEmpty() = list.isEmpty()

}

/**
 * The most common [IcmfMessage] is a block header
 */
open class IcmfMessage(val type: Int) {

}