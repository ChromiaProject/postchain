package net.postchain.base.configuration

data class BlockchainConfigurationOptions(
        val suppressSpecialTransactionValidation: Boolean
) {
    companion object {
        val DEFAULT = BlockchainConfigurationOptions(false)
    }
}
