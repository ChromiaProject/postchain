package net.postchain.common.rest

data class HighestBlockHeightAnchoringCheck(val cac: AnchoringChainCheck?, val sac: AnchoringChainCheck?, val evm: AnchoringChainCheck?)
data class AnchoringChainCheck(val height: Long? = null, val match: Boolean? = null, val error: String? = null)
