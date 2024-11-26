package net.postchain.web

import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.core.EContext
import net.postchain.core.Transactor
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtx.GTXModule
import net.postchain.gtx.GTXModuleFactory
import net.postchain.gtx.data.ExtOpData
import net.postchain.gtx.special.GTXSpecialTxExtension

@Suppress("unused")
class WebStaticGTXModuleFactory : GTXModuleFactory {
    override fun makeModule(config: Gtv, blockchainRID: BlockchainRid): GTXModule {
        val webConfig = config.asDict()["web_static"]?.asDict() ?: throw UserMistake("web_static config is missing")
        val contentMap = webConfig["content"]?.asDict() ?: throw UserMistake("web_static/content config is missing")
        val cacheTtlSeconds = webConfig["cache_ttl_seconds"]?.asInteger() ?: 60 // One minute by default
        return WebStaticGTXModule(contentMap.mapValues {
            val resource = it.value.asDict()
            val contentType = resource["content_type"]?.asString()
                    ?: throw UserMistake("content_type is missing for ${it.key}")
            val content = resource["content"] ?: throw UserMistake("content is missing for ${it.key}")
            gtv(mapOf("content_type" to gtv(contentType), "content" to content, "cache_ttl_seconds" to gtv(cacheTtlSeconds)))
        })
    }
}

const val QUERY_NAME = "web_static"

const val INDEX_FILE = "index.html"

class WebStaticGTXModule(private val contentMap: Map<String, Gtv>) : GTXModule {
    override fun getSpecialTxExtensions(): List<GTXSpecialTxExtension> = listOf()

    override fun makeBlockBuilderExtensions(): List<BaseBlockBuilderExtension> = listOf()

    override fun getOperations(): Set<String> = setOf()

    override fun initializeDB(ctx: EContext) {}

    override fun makeTransactor(opData: ExtOpData): Transactor {
        throw UserMistake("Operation not found")
    }

    override fun getQueries(): Set<String> = setOf(QUERY_NAME)

    override fun query(ctxt: EContext, name: String, args: Gtv): Gtv {
        if (name != QUERY_NAME) throw UserMistake("Query $name not found")

        val path = args.asDict()["path"]?.asArray()?.map { it.asString() } ?: throw UserMistake("path is missing")
        val key = path.joinToString(separator = "/")
        return contentMap[key]
                ?: contentMap[if (key.isEmpty()) INDEX_FILE else "$key/$INDEX_FILE"]
                ?: throw UserMistake("$key not found")
    }
}
