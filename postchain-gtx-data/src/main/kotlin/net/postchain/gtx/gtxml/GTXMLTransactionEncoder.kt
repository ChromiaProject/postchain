// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtx.gtxml

import jakarta.xml.bind.JAXB
import net.postchain.gtv.gtvml.GtvMLEncoder
import net.postchain.gtv.gtxml.ObjectFactory
import net.postchain.gtv.gtxml.OperationsType
import net.postchain.gtv.gtxml.SignaturesType
import net.postchain.gtv.gtxml.SignersType
import net.postchain.gtx.Gtx
import net.postchain.gtx.GtxOp
import java.io.StringWriter


object GTXMLTransactionEncoder {

    private val objectFactory = ObjectFactory()

    /**
     * Encodes [GTXData] into XML format
     */
    fun encodeXMLGTXTransaction(gtxTxData: Gtx): String {
        val transactionType = objectFactory.createTransactionType()
        transactionType.blockchainRID = gtxTxData.gtxBody.blockchainRid.toHex()
        transactionType.signers = encodeSigners(gtxTxData.gtxBody.signers.toTypedArray())
        transactionType.operations = encodeOperations(gtxTxData.gtxBody.operations)
        transactionType.signatures = encodeSignature(gtxTxData.signatures.toTypedArray())

        val jaxbElement = objectFactory.createTransaction(transactionType)

        val xmlWriter = StringWriter()
        JAXB.marshal(jaxbElement, xmlWriter)

        return xmlWriter.toString()
    }

    private fun encodeSigners(signers: Array<ByteArray>): SignersType {
        with(objectFactory.createSignersType()) {
            signers.map(objectFactory::createBytea) // See [ObjectFactory.createBytearrayElement]
                    .toCollection(this.signers)
            return this
        }
    }

    private fun encodeOperations(operations: List<GtxOp>): OperationsType {
        with(objectFactory.createOperationsType()) {
            operations.forEach {
                val operationType = objectFactory.createOperationType()
                operationType.name = it.opName
                it.args.map(GtvMLEncoder::encodeGTXMLValueToJAXBElement)
                        .toCollection(operationType.parameters)
                this.operation.add(operationType)
            }
            return this
        }
    }

    private fun encodeSignature(signatures: Array<ByteArray>): SignaturesType {
        with(objectFactory.createSignaturesType()) {
            signatures.map(objectFactory::createBytea) // See [ObjectFactory.createBytearrayElement]
                    .toCollection(this.signatures)
            return this
        }
    }
}
