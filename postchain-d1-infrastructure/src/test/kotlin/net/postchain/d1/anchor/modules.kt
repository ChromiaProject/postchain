package net.postchain.d1.anchor

import net.postchain.base.data.DatabaseAccess
import net.postchain.core.EContext

/**
 * This file holds helper classes only used for testing.
 */

fun table_anchor_blocks(ctx: EContext): String {
    val db = DatabaseAccess.of(ctx)
    return db.tableName(ctx, "anchor_blocks")
}


