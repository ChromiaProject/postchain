package net.postchain.base.icmf

/**
 * Receives the heights sent from the [IcmfDispatcher] (via [IcmfPipe]).
 */
class IcmfReceiver {

    // On the receiving end we want to be able to access all pipes for a target
    val targetChainToPipes: MutableMap<Long, MutableList<IcmfPipe>> = HashMap()


    /**
     * @return true if given source and target chains are already connected
     */
    fun isSourceAndTargetConnected(sourceChainIid: Long, targetChainIid: Long): Boolean {
        val targetsPipes = getListOrAddIfNotExists(targetChainIid)
        for (pipe in targetsPipes) {
            if (pipe.sourceChainIid == sourceChainIid) {
                return true
            }
        }
        return false
    }

    /**
     * Return all pipes for the given chain that have new data
     *
     * @param targetChainIid is the target chain we want to check updates for
     * @return a mutable list of pipes (yeah, this is unsafe, but don't wanna make a defensive copy)
     */
    fun getPipesForChain(targetChainIid: Long): List<IcmfPipe> {
        val allPipes = getListOrAddIfNotExists(targetChainIid)
        val dataPipes = ArrayList<IcmfPipe>()
        for (pipe in allPipes) {
            if (!pipe.isEmpty()) {
                dataPipes.add(pipe)
            }
        }
        return dataPipes
    }

    /**
     * Adds a pipe for the given (target) chain and returns the new pipe
     *
     * @param sourceChainIid is the chain that should send heights
     * @param targetChainIid is the chain that should receive heights
     */
    fun connectPipe(sourceChainIid: Long, targetChainIid: Long): IcmfPipe {
        val newPipe = IcmfPipe(sourceChainIid, targetChainIid)
        val pipes = getListOrAddIfNotExists(targetChainIid)
        pipes.add(newPipe)
        return newPipe
    }


    private fun getListOrAddIfNotExists(targetChainIid: Long): MutableList<IcmfPipe> {
        var pipes: MutableList<IcmfPipe>? = targetChainToPipes[targetChainIid]
        if (pipes == null) {
            pipes = ArrayList<IcmfPipe>()
            targetChainToPipes[targetChainIid] = pipes
        }
        return pipes
    }

}