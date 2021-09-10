package net.postchain.base.icmf

class MessageOpInjector(val controller: IcmfController) {

    /**
     * Returns maximum of "max" messages from the queue, or none if empty
     *
     * @param "max" is the maximum number of messages we want to return
     * @return messages from the queue
     */
    /*
    fun pullMessages(max: Int): List<IcmfMessage> {
        val retList = ArrayList<IcmfMessage>()
        var messagesToPull = max
        while (messagesToPull > 0 && !pumpStation.isEmpty()) {
            retList.add(pumpStation.getMessage()!!)
            messagesToPull--
        }

        return retList
    }
     */
}