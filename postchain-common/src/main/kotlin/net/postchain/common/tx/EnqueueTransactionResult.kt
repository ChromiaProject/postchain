package net.postchain.common.tx

enum class EnqueueTransactionResult(val code: Int) {
    OK(0),
    FULL(1),
    DUPLICATE(2),
    INVALID(3)
}
