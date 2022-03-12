// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1.icmf

interface  IICMFPipe {
    fun shouldPoll(): Boolean
    fun poll(fetchInstructions: Any): IcmfPackage?
}

/**
 * Transports messages in a "pipe", ether:
 * - asynchronously via push, or
 * - synchronously without push (via [IcmfFetcher]).
 *
 * A [IcmfPipe] only handles point-2-point connections, with one "source chain" and one "listener chain" only.
 *
 * (Example: If we have 3 "listener chains" that listen to 2 "source chains" we must have 6 pipes in total.
 * A: Push   For push, messages coming from a source chain get duplicated in 3 pipes, which shouldn't consume memory
 *           if we re-use the same message instance for all 3 pipes. This is safe if the message is immutable.
 * B: Fetch  If we don't push, the same message will be fetched using the [IcmfFetcher] when the [IcmfReceiver]
 *           needs messages. This will happen 3 times, one per listening pipe, but this is rock solid
 *           and means we don't risk losing any messages this way.)
 *
 * Messages might be pushed into the pipe by one party [IcmfDispatcher], and extracted from the "other end"
 * by another party [IcmfReceiver] when the receiving party is ready (this way it's asynchronous)
 *
 */
class IcmfPipe(
    val sourceChainIid: Long, // Where packages coming from
    val listenerChainIid: Long,  // Where packages are going
    private val fetcher: IcmfFetcher // Using this to get more data into the pipe
) {
    val pipeId = "$sourceChainIid-$listenerChainIid" // This id will probably be unique in the system, for debugging
    var likelyCaughtUp = false

    val pendingPackets = mutableMapOf<Long, IcmfPackage>()

    @Synchronized
    fun push(newPkg: IcmfPackage) {
        pendingPackets[newPkg.height] = newPkg
    }

    @Synchronized
    fun hasNewPackets(): Boolean {
        return !likelyCaughtUp || (pendingPackets.size > 0);
    }

    /**
     * We can find a [IcmfPackage] two ways:
     * 1. in our pendingPackets cache (that got pushed to us previously), or
     * 2. we must fetch the package the hard way, by calling the [IcmfFetcher].
     *
     * @param height is the height of the source chain we are looking for.
     * @return a package if found.
     */
    @Synchronized
    fun fetch(height: Long): IcmfPackage? {
        if (height in pendingPackets) {
            likelyCaughtUp = true
            val pkt = pendingPackets[height]
            pendingPackets.remove(height)
            return pkt
        } else {
            return fetcher.fetch(sourceChainIid, height)
        }
    }

}
