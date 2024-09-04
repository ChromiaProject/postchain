// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtx

import net.postchain.PostchainContext
import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.EContext
import net.postchain.core.Shutdownable
import net.postchain.core.Transactor
import net.postchain.gtv.Gtv
import net.postchain.gtx.data.ExtOpData
import net.postchain.gtx.special.GTXSpecialTxExtension

const val NON_STRICT_QUERY_ARGUMENT = "~non-strict"

/**
 * The GTX Module is the basis of a "Dapp".
 */
interface GTXModule : Shutdownable {
    fun makeTransactor(opData: ExtOpData): Transactor
    fun getOperations(): Set<String>
    fun getQueries(): Set<String>
    fun query(ctxt: EContext, name: String, args: Gtv): Gtv
    fun initializeDB(ctx: EContext)
    fun makeBlockBuilderExtensions(): List<BaseBlockBuilderExtension>
    fun getSpecialTxExtensions(): List<GTXSpecialTxExtension>

    override fun shutdown() {}
}

interface PostchainContextAware {
    fun initializeContext(configuration: BlockchainConfiguration, postchainContext: PostchainContext)
}

fun interface TransactorMaker {
    fun makeTransactor(opData: ExtOpData): Transactor
}

interface OperationWrapper {
    fun getWrappingOperations(): Set<String>
    fun injectDelegateTransactorMaker(transactorMaker: TransactorMaker)
}

interface GTXModuleFactory {
    fun makeModule(config: Gtv, blockchainRID: BlockchainRid): GTXModule
}

/**
 * This template/dummy class provides simple implementations for everything except "initializeDB()"
 * (It's up to subclasses to override whatever they need)
 */
abstract class SimpleGTXModule<ConfT>(
        val conf: ConfT,
        val opmap: Map<String, (ConfT, ExtOpData) -> Transactor>,
        val querymap: Map<String, (ConfT, EContext, Gtv) -> Gtv>
) : GTXModule {

    override fun getSpecialTxExtensions(): List<GTXSpecialTxExtension> {
        return listOf()
    }

    override fun makeBlockBuilderExtensions(): List<BaseBlockBuilderExtension> {
        return listOf()
    }

    override fun makeTransactor(opData: ExtOpData): Transactor {
        if (opData.opName in opmap) {
            return opmap[opData.opName]!!(conf, opData)
        } else {
            throw UnknownOperation(opData.opName)
        }
    }

    override fun getOperations(): Set<String> {
        return opmap.keys
    }

    override fun getQueries(): Set<String> {
        return querymap.keys
    }

    override fun query(ctxt: EContext, name: String, args: Gtv): Gtv {
        if (name in querymap) {
            return querymap[name]!!(conf, ctxt, args)
        } else throw UnknownQuery(name)
    }
}

class UnknownQuery(val name: String) : UserMistake("Unknown query: $name")
class UnknownOperation(val name: String) : UserMistake("Unknown operation: $name")
