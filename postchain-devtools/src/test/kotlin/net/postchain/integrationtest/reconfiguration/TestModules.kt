// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.integrationtest.reconfiguration

import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtx.GTXModule
import net.postchain.gtx.special.GTXSpecialTxExtension
import net.postchain.core.EContext
import net.postchain.core.TxEContext
import net.postchain.core.Transactor
import net.postchain.gtx.data.ExtOpData

open class AbstractDummyModule : GTXModule {

    override fun getSpecialTxExtensions(): List<GTXSpecialTxExtension> {
        return listOf()
    }

    override fun makeBlockBuilderExtensions(): List<BaseBlockBuilderExtension> {
        return listOf()
    }

    override fun makeTransactor(opData: ExtOpData): Transactor {
        return object : Transactor {
            override fun isSpecial(): Boolean {
                return false
            }

            override fun checkCorrectness() {}
            override fun apply(ctx: TxEContext): Boolean = true
        }
    }

    override fun getOperations(): Set<String> = emptySet()

    override fun getQueries(): Set<String> = emptySet()

    override fun query(ctxt: EContext, name: String, args: Gtv): Gtv = GtvNull

    override fun initializeDB(ctx: EContext) = Unit

    override fun shutdown() { }
}

class DummyModule1 : AbstractDummyModule()

class DummyModule2 : AbstractDummyModule()

class DummyModule3 : AbstractDummyModule()

class DummyModule4 : AbstractDummyModule()
