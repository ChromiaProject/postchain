package net.postchain.devtools.testinfra

import net.postchain.base.merkle.Hash
import net.postchain.devtools.KeyPairHelper
import net.postchain.gtv.GtvFactory
import net.postchain.gtx.*

/**
 * This represents a "real" GTX transaction (which means it can be transformed to GTV and GTX without generating errors)
 * but is only meant to be used during tests.
 *
 * Out of simplicity, this is a one-operation only transaction.
 *
 * @property blockchainRID is the ID of the BC
 * @property id is used to make this TX unique, it is used as the argument of the operation
 * @property op_name is the name of the operation
 * @property signers are the binary IDs of the sigers
 * @property cryptoSystem is the system we will use to sign the transaction with
 */
class TestOneOpGtxTransaction(
        val factory: GTXTransactionFactory,
        val id: Int,
        val op_name: String,
        val signers: Array<ByteArray>) {


    private val blockchainRID = factory.blockchainRID
    private val cryptoSystem = factory.cs

    // Cache
    private var cachedBuilder: GTXDataBuilder? = null
    private var cachedGtxTx: GTXTransaction? = null

    /**
     * If we are super lazy and want don't have any signers, we can use this constructor
     */
    constructor(factory: GTXTransactionFactory, id: Int) :
            this(factory, id, "gtx_test", arrayOf())


    /**
     * Note: what makes the TX unique is the name of the argument, and the value (both are generated by [id] property)
     *
     * @return a binary representation of the TX
     */
    fun getRawData(): ByteArray {
        if (cachedBuilder == null) {
            buildTheTx()
        }

        return cachedGtxTx!!.getRawData()
    }

    /**
     * Returns the RID of the cachedGtxTx.
     */
    fun getRID(): Hash {
        if (cachedBuilder == null) {
            buildTheTx()
        }

        return cachedGtxTx!!.getRID()
    }

    fun getHash(): Hash {
        if (cachedBuilder == null) {
            buildTheTx()
        }

        return cachedGtxTx!!.getHash()
    }

    /**
     * We're delaying this to the last moment, but probably to no avail since we most likely need the
     * real GXT TX (that's why we are using this class, right)
     */
    private fun buildTheTx() {
        val b = GTXDataBuilder(blockchainRID, arrayOf(KeyPairHelper.pubKey(0)), cryptoSystem)
        val arg0 =GtvFactory.gtv(1.toLong())
        val arg1 =GtvFactory.gtv("${id} and ${id}")
        b.addOperation(op_name, arrayOf(arg0, arg1))
        b.finish()
        b.sign(cryptoSystem.buildSigMaker(KeyPairHelper.pubKey(0), KeyPairHelper.privKey(0)))
        cachedBuilder = b

        // So, the question here is: are we doing any work twice? I don't think so
        //     (apart from the relatively cheap transformation between GTV and GTX)
        cachedGtxTx = factory.build(b.getGTXTransactionData())
    }
}