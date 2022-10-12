package net.postchain.managed.query

import net.postchain.gtv.Gtv

interface QueryRunner {
    fun query(name: String, args: Gtv): Gtv
}