// Copyright (c) 2025 ChromaWay AB. See README for license information.

package net.postchain.core

import java.sql.Connection

/**
 * Implemented by GTX modules that need to create global (schema-level, non-chain-specific)
 * database structures such as functions or tables shared across all blockchains.
 *
 * Implementations are discovered via [java.util.ServiceLoader] and called once during node
 * initialization, in a committed transaction, before any blockchain starts. This avoids the
 * deadlock that occurs when multiple blockchains race to create the same global structures
 * inside their own uncommitted write transactions.
 *
 * Implementations must be idempotent — they may be invoked on every node startup.
 *
 * Register implementations in:
 * META-INF/services/net.postchain.core.GlobalStorageInitializer
 */
interface GlobalStorageInitializer {
    fun initializeGlobalStorage(connection: Connection)
}
