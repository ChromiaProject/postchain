package net.postchain.managed.query

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDictionary

interface QueryRunner {
    fun query(name: String, args: Gtv = GtvDictionary.build(mapOf())): Gtv
}