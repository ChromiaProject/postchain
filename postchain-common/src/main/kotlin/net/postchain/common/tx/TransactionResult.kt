package net.postchain.common.tx

enum class TransactionResult(val code: Int) {
    OK(0),
    FULL(1),
    DUPLICATE(2),
    INVALID(3),
    UNKNOWN(9999)
}