// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest.controller

import net.postchain.common.BlockchainRid

interface Modellable {

    /**
     * Attaches a [chainModel] associated with key [blockchainRid]
     */
    fun attachModel(blockchainRid: BlockchainRid, chainModel: ChainModel, container: String = "")

    /**
     * Detaches a model associated with key [blockchainRid]
     */
    fun detachModel(blockchainRid: BlockchainRid, container: String = "")

    /**
     * Retrieves models associated with key [blockchainRid]
     */
    fun retrieveModels(blockchainRid: BlockchainRid): List<ChainModel>
}