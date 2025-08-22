package net.postchain.api.rest.model

import net.postchain.gtx.ArgumentMetadata
import net.postchain.gtx.ReturnMetadata

/**
 * Metadata about operations and queries from all GTX modules.
 */
data class ApiMetadata(
        val operations: Map<String, OperationMetadata>,

        val queries: Map<String, QueryMetadata>,
)

data class OperationMetadata(
        val gtxModule: String,
        val args: List<ArgumentMetadata>,
)

data class QueryMetadata(
        val gtxModule: String,
        val args: List<ArgumentMetadata>,
        val returnType: ReturnMetadata,
)
