package net.postchain.integrationtest.api

import net.postchain.core.EContext
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtx.SimpleGTXModule

class TestWebQueryModule : SimpleGTXModule<Unit>(Unit,
        mapOf(),
        mapOf(
                "get_page" to { _, _, args ->
                    require(args.asDict()["path"]?.asArray()?.firstOrNull()?.asString() == "front")
                    require(args.asDict()["query_params"]?.asDict()?.isEmpty() == true)
                    gtv(mapOf(
                            "content_type" to gtv("text/html"),
                            "content" to gtv("<h1>it works!</h1>")
                    ))
                },

                "get_picture" to { _, _, args ->
                    require(args.asDict()["path"]?.asArray()?.isEmpty() == true)
                    require(args.asDict()["query_params"]?.asDict()?.get("id")?.asArray()?.first()?.asString() == "1234")
                    gtv(mapOf(
                            "content_type" to gtv("image/png"),
                            "content" to gtv("abcd".toByteArray())
                    ))
                }
        )
) {
    override fun initializeDB(ctx: EContext) {
    }
}
