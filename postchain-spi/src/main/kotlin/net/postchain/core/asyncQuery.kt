package net.postchain.core

import net.postchain.gtv.Gtv
import net.postchain.gtv.mapper.Name

enum class AsyncQueryResponseStatus {
    NOT_FOUND,
    PENDING,
    COMPLETED,
    FAILED,
}

data class AsyncQueryResponse(
        @Name("status") val status: AsyncQueryResponseStatus,
        @Name("response") val queryResponse: Gtv,
        @Name("error") val errorMessage: String?,
)
