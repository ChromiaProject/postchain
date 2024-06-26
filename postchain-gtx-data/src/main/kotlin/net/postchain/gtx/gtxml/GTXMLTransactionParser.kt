// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtx.gtxml

import jakarta.xml.bind.JAXB
import jakarta.xml.bind.JAXBElement
import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.common.types.WrappedByteArray
import net.postchain.common.wrap
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.SigMaker
import net.postchain.gtv.Gtv
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.gtv.gtxml.OperationsType
import net.postchain.gtv.gtxml.ParamType
import net.postchain.gtv.gtxml.SignersType
import net.postchain.gtv.gtxml.TransactionType
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkle.MerkleHashCalculator
import net.postchain.gtx.Gtx
import net.postchain.gtx.GtxBody
import net.postchain.gtx.GtxOp
import net.postchain.gtx.data.OpData
import java.io.StringReader

class TransactionContext(val blockchainRID: BlockchainRid?,
                         val params: Map<String, Gtv> = mapOf(),
                         val autoSign: Boolean = false,
                         val signers: Map<WrappedByteArray, SigMaker> = mapOf()) {

    companion object {
        fun empty() = TransactionContext(null)
    }
}


object GTXMLTransactionParser {

    /**
     * Parses XML represented as string into [Gtx] within the [TransactionContext]
     */
    fun parseGTXMLTransaction(xml: String, context: TransactionContext, cs: CryptoSystem): Gtx {
        return parseGTXMLTransaction(
                JAXB.unmarshal(StringReader(xml), TransactionType::class.java),
                context,
                cs)
    }

    /*
     * Parses XML represented as string into [Gtx] within the jaxbContext of [params] ('<param />') and [signers]
    fun parseGTXMLTransaction(xml: String,
                              params: Map<String, Gtv> = mapOf(),
                              signers: Map<ByteArrayKey, Signer> = mapOf()): Gtx {

        return parseGTXMLTransaction(
                xml,
                TransactionContext(null, params, true, signers))
    }
     */

    /**
     * TODO (et): Parses XML represented as string into [Gtx] within the [TransactionContext]
     */
    fun parseGTXMLTransaction(transaction: TransactionType, context: TransactionContext, cs: CryptoSystem): Gtx {
        // Asserting count(signers) == count(signatures)
        requireSignaturesCorrespondsSigners(transaction)

        val rid= parseBlockchainRID(transaction.blockchainRID, context.blockchainRID)
        val signers = parseSigners(transaction.signers, context.params)
        val signatures = parseSignatures(transaction, context.params)
        val ops = parseOperations(transaction.operations, context.params)

        val txBody = GtxBody(rid, ops.map { GtxOp.fromOpData(it) }, signers)

        if (context.autoSign) {
            val calculator = GtvMerkleHashCalculator(cs)
            signTransaction(txBody, signatures, context.signers, calculator)
        }

        return Gtx(txBody, signatures.toList())
    }

    private fun requireSignaturesCorrespondsSigners(tx: TransactionType) {
        if (tx.signatures != null && tx.signers.signers.size != tx.signatures.signatures.size) {
            throw IllegalArgumentException("Number of signers (${tx.signers.signers.size}) is not equal to " +
                    "the number of signatures (${tx.signatures.signatures.size})\n")
        }
    }

    private fun parseBlockchainRID(blockchainRID: String?, contextBlockchainRID: BlockchainRid?): BlockchainRid {
        return if (blockchainRID.isNullOrEmpty()) {
            contextBlockchainRID ?: BlockchainRid.ZERO_RID
        } else {
            val data = blockchainRID.hexStringToByteArray()
                    .takeIf { contextBlockchainRID == null || it.contentEquals(contextBlockchainRID.data) }
                    ?: throw IllegalArgumentException(
                            "BlockchainRID = '$blockchainRID' of parsed xml transaction is not equal to " +
                                    "TransactionContext.blockchainRID = '${contextBlockchainRID!!.toHex()}'"
                    )
            BlockchainRid(data)
        }
    }

    private fun parseSigners(signers: SignersType, params: Map<String, Gtv>): List<ByteArray> {
        return signers.signers
                .map { parseJAXBElementToByteArrayOrParam(it, params) }
    }

    private fun parseSignatures(transaction: TransactionType, params: Map<String, Gtv>): Array<ByteArray> {
        return if (transaction.signatures != null) {
            transaction.signatures.signatures
                    .map { parseJAXBElementToByteArrayOrParam(it, params) }
                    .toTypedArray()
        } else {
            Array(transaction.signers.signers.size) { byteArrayOf() }
        }
    }

    private fun parseJAXBElementToByteArrayOrParam(jaxbElement: JAXBElement<*>, params: Map<String, Gtv>): ByteArray {
        // TODO: [et]: Add better error handling
        return if (jaxbElement.value is ParamType) {
            params[(jaxbElement.value as ParamType).key]
                    ?.asByteArray()
                    ?: throw IllegalArgumentException("Unknown type of GTXMLValue")
        } else {
            jaxbElement.value as? ByteArray ?: byteArrayOf()
        }
    }

    private fun parseOperations(operations: OperationsType, params: Map<String, Gtv>): Array<OpData> {
        return operations.operation.map {
            OpData(
                    it.name,
                    it.parameters.map {
                        GtvMLParser.parseJAXBElementToGtvML(it, params)
                    }.toTypedArray())
        }.toTypedArray()
    }

    /**
     * Will provide all missing signatures
     *
     * @param tx is the transaction to sign
     * @param signersMap is a map that tells us what [SigMaker] should be usd for each signer
     */
    private fun signTransaction(tx: GtxBody, signatures: Array<ByteArray>, signersMap: Map<WrappedByteArray, SigMaker>, calculator: MerkleHashCalculator<Gtv>) {
        val txSigners = tx.signers
        val txBodyMerkleRoot = tx.calculateTxRid(calculator)
        for (i in 0 until txSigners.size) {
            if (signatures[i].isEmpty()) {
                val key = txSigners[i].wrap()
                val sigMaker = signersMap[key] ?: throw IllegalArgumentException("Signer $key is absent")
                signatures[i] = sigMaker.signDigest(txBodyMerkleRoot).data
            }
        }
    }
}