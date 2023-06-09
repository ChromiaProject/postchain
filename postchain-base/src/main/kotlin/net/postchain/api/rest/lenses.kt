package net.postchain.api.rest

import com.google.gson.JsonElement
import com.google.gson.JsonNull
import net.postchain.api.rest.json.JsonFactory
import net.postchain.api.rest.json.JsonFactory.auto
import net.postchain.api.rest.json.JsonFactory.json
import net.postchain.api.rest.model.ApiStatus
import net.postchain.api.rest.model.TxRid
import net.postchain.base.ConfirmationProof
import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.core.BlockRid
import net.postchain.core.TransactionInfoExt
import net.postchain.core.block.BlockDetail
import net.postchain.ebft.rest.contract.StateNodeStatus
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvNull
import net.postchain.gtv.gtvml.GtvMLEncoder
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.gtv.make_gtv_gson
import net.postchain.gtv.mapper.GtvObjectMapper
import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.format.auto
import org.http4k.lens.ContentNegotiation
import org.http4k.lens.ContentNegotiation.Companion.None
import org.http4k.lens.Invalid
import org.http4k.lens.LensFailure
import org.http4k.lens.Meta
import org.http4k.lens.ParamMeta
import org.http4k.lens.Path
import org.http4k.lens.Query
import org.http4k.lens.binary
import org.http4k.lens.boolean
import org.http4k.lens.httpBodyRoot
import org.http4k.lens.int
import org.http4k.lens.long
import org.http4k.lens.regex
import org.http4k.lens.string
import java.io.InputStream
import java.lang.UnsupportedOperationException

const val ridRegex = "([0-9a-fA-F]{64})"

val txRidPath = Path.regex(ridRegex).map { TxRid(it.hexStringToByteArray()) }.of("txRid", "Hex encoded transaction RID")
val blockRidPath = Path.regex(ridRegex).map { BlockRid(it.hexStringToByteArray()) }.of("blockRid", "Hex encoded block RID")
val heightPath = Path.long().of("height", "Block height")

val limitQuery = Query.int().optional("limit")
val beforeTimeQuery = Query.long().optional("before-time")
val beforeHeightQuery = Query.long().optional("before-height")
val txsQuery = Query.boolean().optional("txs")
val heightQuery = Query.long().map {
    if (it >= -1)
        it
    else
        throw LensFailure(listOf(
                Invalid(Meta(false, "query", ParamMeta.IntegerParam, "height", "Height must be -1 (current height) or a non-negative integer"))))
}.defaulted("height", -1)

val gtvGson = make_gtv_gson()
val prettyGson = JsonFactory.makePrettyJson()

val errorJsonBody = Body.auto<ErrorBody>().toLens()
val errorGtvBody = Body.binary(ContentType.OCTET_STREAM, "error GTV").map(
        {
            val gtv = GtvDecoder.decodeGtv(it)
            ErrorBody(gtv.asString())
        },
        {
            val gtv = GtvFactory.gtv(it.error)
            GtvEncoder.encodeGtv(gtv).inputStream()
        }
).toLens()
val errorBody = ContentNegotiation.auto(errorJsonBody, errorGtvBody)
val txBody = Body.auto<Tx>().map({ it.tx.hexStringToByteArray() }, { Tx(it.toHex()) }).toLens()
val txInfoBody = Body.auto<TransactionInfoExt>().toLens()
val txInfosBody = Body.auto<List<TransactionInfoExt>>().toLens()
val proofBody = Body.auto<ConfirmationProof>().toLens()
val statusBody = Body.auto<ApiStatus>().toLens()
val blocksBody = Body.auto<List<BlockDetail>>().toLens()
val blockJsonBody = Body.auto<BlockDetail>().toLens()
val blockGtvBody = Body.binary(ContentType.OCTET_STREAM, "block GTV").map(
        { inputStream ->
            val gtv = inputStream.use { GtvDecoder.decodeGtv(it) }
            GtvObjectMapper.fromGtv(gtv, BlockDetail::class)
        },
        {
            val gtv = it.let { GtvObjectMapper.toGtvDictionary(it) }
            GtvEncoder.encodeGtv(gtv).inputStream()
        }
).toLens()
val blockBody = ContentNegotiation.auto(blockJsonBody, blockGtvBody)
val prettyJsonBody = Body.string(ContentType.APPLICATION_JSON, "pretty JSON").map(
        {
            prettyGson.fromJson(it, JsonElement::class.java)
        },
        {
            prettyGson.toJson(it)
        }
).toLens()
val nullJsonBody = Body.json("null JSON").map(
        {
            require(it.isJsonNull) { "null JSON expected" }
        },
        {
            JsonNull.INSTANCE
        }
).toLens()
val nullGtvBody = Body.binary(ContentType.OCTET_STREAM, "null GTV").map(
        { inputStream ->
            val gtv = inputStream.use { GtvDecoder.decodeGtv(it) }
            require(gtv.isNull()) { "null GTV expected" }
        },
        {
            GtvEncoder.encodeGtv(GtvNull).inputStream()
        }
).toLens()
val nullBody = ContentNegotiation.auto(nullJsonBody, nullGtvBody)
val emptyJsonBody = Body.auto<Empty>().toLens()
val emptyGtvBody = Body.binary(ContentType.OCTET_STREAM, "empty GTV").map(
        { inputStream ->
            inputStream.use { GtvDecoder.decodeGtv(it) }
            Empty
        },
        {
            GtvEncoder.encodeGtv(GtvFactory.gtv(mapOf())).inputStream()
        }
).toLens()
val emptyBody = ContentNegotiation.auto(emptyJsonBody, emptyGtvBody)
val binaryBody = Body.binary(ContentType.OCTET_STREAM, "binary").map(
        { inputStream ->
            inputStream.use { it.readAllBytes() }
        },
        {
            it.inputStream()
        }
).toLens()
val gtvJsonBody = Body.json("GTV JSON").map(
        {
            gtvGson.fromJson(it, Gtv::class.java)
        },
        {
            gtvGson.toJsonTree(it, Gtv::class.java)
        }
).toLens()
val batchQueriesBody = Body.json("queries").map {
    it.asJsonObject["queries"].asJsonArray.map { e -> gtvGson.fromJson(e, Gtv::class.java) }
}.toLens()
val gtxQueriesBody = Body.auto<GtxQueries>().toLens()
val stringsBody = Body.auto<List<String>>().toLens()
val nodeStatusBody = Body.auto<StateNodeStatus>().toLens()
val nodeStatusesBody = Body.auto<List<StateNodeStatus>>().toLens()
val textBody = Body.string(ContentType.TEXT_PLAIN).toLens()
val blockHeightBody = Body.auto<BlockHeight>().toLens()
val transactionsCountBody = Body.auto<TransactionsCount>().toLens()
@Suppress("UNREACHABLE_CODE")
val configurationXmlOutBody = httpBodyRoot(listOf(Meta(true, location = "body",
        ParamMeta.StringParam, "configuration", "GtvML")), ContentType.TEXT_XML, None)
        .map({ it.stream }, { Body(it) }).map(
                { _: InputStream -> (throw UnsupportedOperationException("output only lens")) as ByteArray },
                { it: ByteArray ->
                    GtvMLEncoder.encodeXMLGtv(GtvDecoder.decodeGtv(it)).byteInputStream()
                }
        ).toLens()
val configurationOutBody = ContentNegotiation.auto(configurationXmlOutBody, binaryBody)
val gtvmlBody = Body.string(ContentType.TEXT_XML, "GtvML").map(
        {
            GtvMLParser.parseGtvML(it)
        },
        {
            GtvMLEncoder.encodeXMLGtv(it)
        }
).toLens()
val gtvBody = Body.binary(ContentType.OCTET_STREAM, "GTV").map(
        { inputStream ->
            inputStream.use { GtvDecoder.decodeGtv(it) }
        },
        {
            GtvEncoder.encodeGtv(it).inputStream()
        }
).toLens()
val configurationInBody = ContentNegotiation.auto(gtvmlBody, gtvBody)
val versionBody = Body.auto<Version>().toLens()

sealed interface BlockchainRef
data class BlockchainRidRef(val rid: BlockchainRid) : BlockchainRef
data class BlockchainIidRef(val iid: Long) : BlockchainRef

data class GtxQueries(val queries: List<String>)
data class BlockHeight(val blockHeight: Long)
data class TransactionsCount(val transactionsCount: Long)
data class Tx(val tx: String)
data class ErrorBody(val error: String = "")
data class Version(val version: Int)
object Empty
