// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.devtools.testinfra.specialtx

import net.postchain.crypto.devtools.KeyPairHelper
import net.postchain.devtools.testinfra.TestOneOpGtxTransaction
import net.postchain.gtv.GtvFactory
import net.postchain.gtx.GTXDataBuilder
import net.postchain.gtx.GTXTransactionFactory

/**
 * Same as [TestOneOpGtxTransaction] but we are using another
 *
 * @property factory is what we will use to build the TX with
 * @property id is used to make this TX unique, it is used as the argument of the operation
 * @property op_name is the name of the operation
 * @property signers are the binary IDs of the sigers
 */
class TestSpecialTxGtxTransaction(
    factory: GTXTransactionFactory,
    id: Int,
    op_name: String,
    signers: Array<ByteArray>
) : TestOneOpGtxTransaction(factory, id, op_name, signers) {

    /**
     * If we are super lazy and want don't have any signers, we can use this constructor
     */
    constructor(factory: GTXTransactionFactory, id: Int, op_name: String) :
            this(factory, id, op_name, arrayOf())


    /**
     * We're delaying this to the last moment, but probably to no avail since we most likely need the
     * real GXT TX (that's why we are using this class, right)
     */
    override fun buildTheTx() {
        val b = GTXDataBuilder(blockchainRID, arrayOf(KeyPairHelper.pubKey(0)), cryptoSystem)
        val arg0 = GtvFactory.gtv(1.toLong())
        val arg1 = GtvFactory.gtv("${id} and ${id}")
        b.addOperation(op_name, arrayOf(arg0, arg1))
        b.finish()
        b.sign(cryptoSystem.buildSigMaker(KeyPairHelper.pubKey(0), KeyPairHelper.privKey(0)))
        cachedBuilder = b

        // So, the question here is: are we doing any work twice? I don't think so
        //     (apart from the relatively cheap transformation between GTV and GTX)
        cachedGtxTx = factory.build(b.getGTXTransactionData())
    }
}