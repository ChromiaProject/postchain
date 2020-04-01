package net.postchain.base.data

import net.postchain.gtv.*

data class TableDefinition(
        val columnName: String,
        val dataType: String,
        val isNullable: Boolean,
        val columnDefault: String?
) {
    fun toGtv(): GtvArray {
        return GtvFactory.gtv(GtvString(columnName), GtvString(dataType), GtvInteger(isNullable.toLong()), GtvString(columnDefault.toString()))
    }

    companion object {
        fun fromGtv(gtv: GtvArray): TableDefinition {
            return TableDefinition(
                    gtv[0].asString(),
                    gtv[1].asString(),
                    gtv[2].asBoolean(),
                    gtv[3].asString()
            )
        }
    }
}
