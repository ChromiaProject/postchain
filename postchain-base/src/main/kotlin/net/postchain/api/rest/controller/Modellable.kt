// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest.controller

import net.postchain.common.BlockchainRid

interface Modellable {

    /**
     * Attaches a [chainModel] associated with key [blockchainRid]
     */
    fun attachModel(blockchainRid: BlockchainRid, chainModel: ChainModel)

    /**
     * Detaches a model associated with key [blockchainRid]
     */
    fun detachModel(blockchainRid: BlockchainRid)

    /**
     * Retrieves a model associated with key [blockchainRid]
     */
    fun retrieveModel(blockchainRid: BlockchainRid): ChainModel?

}