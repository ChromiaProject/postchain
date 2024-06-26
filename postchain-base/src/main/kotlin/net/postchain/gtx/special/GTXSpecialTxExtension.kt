package net.postchain.gtx.special

import net.postchain.base.SpecialTransactionPosition
import net.postchain.common.BlockchainRid
import net.postchain.core.BlockEContext
import net.postchain.crypto.CryptoSystem
import net.postchain.gtx.GTXModule
import net.postchain.gtx.data.OpData

/**
 * Holds various info regarding special TXs used by an extension, when a Spec TX is needed and how to create Spec TX etc.
 *
 * NOTE: Remember that the Sync Infra Extension is just a part of many extension interfaces working together
 * (examples: BBB Ext and Sync Ext).
 * To see how it all goes together, see: doc/extension_classes.graphml
 */
interface GTXSpecialTxExtension {

    /**
     * The extension is handed a lot of things that it might need
     */
    fun init(
        module: GTXModule,
        chainID: Long,
        blockchainRID: BlockchainRid,
        cs: CryptoSystem
    )

    /**
     * @return the names of all special operations relevant for this extension
     */
    fun getRelevantOps(): Set<String>

    /**
     * @param position is position in the block, either "begin" or "end"
     * @return true if this position needs a special transaction.
     */
    fun needsSpecialTransaction(position: SpecialTransactionPosition): Boolean

    /**
     * The parameters below should be enough to find the data needed to create a special operation:
     *
     * @param position is position in the block, either "begin" or "end"
     * @param bctx
     * @param blockchainRID is the alternative identifier of the chain (we can get chainIid from the [BlockEContext])
     * @return all new operations created
     */
    fun createSpecialOperations(
        position: SpecialTransactionPosition,
        bctx: BlockEContext
    ): List<OpData>

    /**
     * @return true if the list of operations are considered valid
     */
    fun validateSpecialOperations(
        position: SpecialTransactionPosition,
        bctx: BlockEContext,
        ops: List<OpData>
    ): Boolean

}
