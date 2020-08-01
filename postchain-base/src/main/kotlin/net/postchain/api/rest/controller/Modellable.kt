// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest.controller

interface Modellable {

    /**
     * Attaches a [model] associated with key [blockchainRid]
     */
    fun attachModel(blockchainRid: String, model: AbstractModel)

    /**
     * Detaches a model associated with key [blockchainRid]
     */
    fun detachModel(blockchainRid: String)

    /**
     * Retrieves a model associated with key [blockchainRid]
     */
    fun retrieveModel(blockchainRid: String): AbstractModel?

}