package net.postchain.base.data

import net.postchain.gtv.*

data class TableDefinition(
        val columnName: String,
        val dataType: String,
        val isNullable: Boolean,
        val columnDefault: String?
): BaseData() {

    override fun toGtv(): GtvArray<Gtv> {
        return GtvFactory.gtv(GtvString(columnName), GtvString(dataType), GtvInteger(isNullable.toLong()), GtvString(columnDefault.toString()))
    }

    override fun toHashGtv(): GtvArray<Gtv> {
        return toGtv()
    }

    companion object: FromGtv {
        override fun fromGtv(gtv: GtvArray<Gtv>): TableDefinition {
            return TableDefinition(
                    gtv[0].asString(),
                    gtv[1].asString(),
                    gtv[2].asBoolean(),
                    gtv[3].asString()
            )
        }
    }
}
