// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.devtools.testinfra.specialtx

import net.postchain.devtools.testinfra.TestOneOpGtxTransaction
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
}
