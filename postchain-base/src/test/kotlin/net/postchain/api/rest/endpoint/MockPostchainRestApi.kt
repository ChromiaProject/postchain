package net.postchain.api.rest.endpoint

import net.postchain.api.rest.json.JsonFactory
import net.postchain.base.BaseBlockWitness
import net.postchain.common.hexStringToByteArray
import net.postchain.core.block.BlockDetail
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.make_gtv_gson
import net.postchain.gtv.mapper.GtvObjectMapper
import org.http4k.asString
import org.http4k.core.ContentType
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.then
import org.http4k.filter.ServerFilters
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.Http4kServer
import org.http4k.server.SunHttp
import org.http4k.server.asServer
import java.io.Closeable

object MockPostchainRestApi : HttpHandler, Closeable {
    const val port = 9000

    val block = BlockDetail(
            "34ED10678AAE0414562340E8754A7CCD174B435B52C7F0A4E69470537AEE47E6".hexStringToByteArray(),
            "5AF85874B9CCAC197AA739585449668BE15650C534E08705F6D60A6993FE906D".hexStringToByteArray(),
            "023F9C7FBAFD92E53D7890A61B50B33EC0375FA424D60BD328AA2454408430C383".hexStringToByteArray(),
            0,
            listOf(),
            BaseBlockWitness.fromSignatures(arrayOf()).getRawData(),
            0
    )
    val gtvQueryResponse = gtv("answer")

    private val gson = JsonFactory.makeJson()
    private val gtvGson = make_gtv_gson()

    private val app = ServerFilters.CatchLensFailure.then(
            routes(
                    "/query/{blockchainRID}" bind Method.POST to { request ->
                        val gtvQuery = gtvGson.fromJson(request.body.payload.asString(), Gtv::class.java)
                        val queryName = gtvQuery[0].asString()
                        gtvQuery[1]

                        if (queryName == "error_query") {
                            Response(Status.BAD_REQUEST).header("Content-Type", ContentType.APPLICATION_JSON.value).body("error")
                        } else {
                            Response(Status.OK).header("Content-Type", ContentType.APPLICATION_JSON.value).body(gtvGson.toJson(gtvQueryResponse))
                        }

                    },
                    "/query_gtv/{blockchainRID}" bind Method.POST to { request ->
                        val gtvQuery = GtvDecoder.decodeGtv(request.body.payload.array())
                        gtvQuery[0].asString()
                        gtvQuery[1]

                        Response(Status.OK).header("Content-Type", ContentType.OCTET_STREAM.value).body(GtvEncoder.encodeGtv(gtvQueryResponse).inputStream())
                    },
                    "/blocks/{blockchainRID}/height/{height}" bind Method.GET to { request ->
                        when (request.header("Accept")) {
                            ContentType.OCTET_STREAM.value -> Response(Status.OK)
                                    .header("Content-Type", ContentType.OCTET_STREAM.value)
                                    .body(GtvEncoder.encodeGtv(GtvObjectMapper.toGtvDictionary(block)).inputStream())

                            else -> Response(Status.OK)
                                    .header("Content-Type", ContentType.APPLICATION_JSON.value)
                                    .body(gson.toJson(block))
                        }
                    }
            )
    )

    override fun invoke(request: Request): Response = app(request)

    private var server: Http4kServer? = null

    fun start() {
        server = app.asServer(SunHttp(port)).start()
    }

    override fun close() {
        server?.stop()
        server = null
    }
}
