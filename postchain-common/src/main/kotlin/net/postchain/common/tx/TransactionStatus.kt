package net.postchain.common.tx

enum class TransactionStatus(val status: String) {
    UNKNOWN("unknown"),
    WAITING("waiting"),
    CONFIRMED("confirmed"),
    REJECTED("rejected")
}

