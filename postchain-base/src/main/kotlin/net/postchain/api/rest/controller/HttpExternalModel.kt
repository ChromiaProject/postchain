package net.postchain.api.rest.controller

class HttpExternalModel(
    override val path: String,
    override val chainIID: Long
) : ExternalModel {

    override var live = true

    override fun toString(): String {
        return "${this.javaClass.simpleName}(path=$path, chainId=$chainIID)"
    }

}