package net.postchain.gtx

import net.postchain.gtv.GtvType

/**
 * Metadata for a GTX module.
 */
data class GTXModuleMetadata(
        val operations: Map<String, OperationMetadata>,

        val queries: Map<String, QueryMetadata>,
)

data class OperationMetadata(
        val args: List<ArgumentMetadata>,
)

data class QueryMetadata(
        val args: List<ArgumentMetadata>,

        val returnType: ReturnMetadata,
)

data class ArgumentMetadata(
        /**
         * The name of the argument.
         */
        val name: String,

        /**
         * The set of GTV types that are accepted, it cannot be empty.
         */
        val gtvTypes: Set<GtvType>,

        /**
         * Optional extended type information defined by the GTX module.
         * Should be a human-readable string.
         */
        val extendedType: String? = null,

        val required: Boolean = true,

        /**
         * Optional default value for the argument, it can only be set if `required` is false.
         *
         * Should be a human-readable string.
         */
        val defaultValue: String? = null,
)

data class ReturnMetadata(
        /**
         * The set of GTV types that are returned, it cannot be empty.
         */
        val gtvTypes: Set<GtvType>,

        /**
         * Optional extended type information defined by the GTX module.
         *
         * Should be a human-readable string.
         */
        val extendedType: String? = null,
)
