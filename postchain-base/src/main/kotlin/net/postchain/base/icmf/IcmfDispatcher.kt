package net.postchain.base.icmf

import mu.KLogging

/**
 * Responsible for signalling a block was built to the relevant pipes
 * Height signals will be received by [IcmfReceiver]
 */
class IcmfDispatcher {

    companion object : KLogging()

    val icmfPipesMap = HashMap<Long, ArrayList<IcmfPipe>>()


    /**
     * @param chainIid is the process that "owns" the pipe
     * @param pipe is the pipe we should add
     */
    fun addMessagePipe(pipe: IcmfPipe) {
        val sourceChainIid = pipe.sourceChainIid
        var pipes = icmfPipesMap[sourceChainIid]
        if (pipes == null) {
            pipes = ArrayList<IcmfPipe>()
            icmfPipesMap[sourceChainIid] = pipes
        }
        pipes.add(pipe)
    }

    /**
     * Put new height in all pipes for the given process
     *
     * @param sourceChainIid is the BC we want to dispatch messages for
     * @param height is the new height
     */
    fun triggerPipes(sourceChainIid: Long, height: Long) {
        var pipes = icmfPipesMap[sourceChainIid]
        if (pipes == null) {
            logger.warn("triggerPipes() -- chain: $sourceChainIid doesn't have any pipes") // Isn't this strange? All chains have anchor chain at least?
        } else {
            if (logger.isDebugEnabled) {
                logger.debug("triggerPipes() -- trigger chain: $sourceChainIid for height: $height with ${pipes.size} pipes")
            }
            for (pipe in pipes) {
                val pkg = IcmfPackage.build(height) // TODO: should add messages
                pipe.push(pkg)
            }
        }
    }

}