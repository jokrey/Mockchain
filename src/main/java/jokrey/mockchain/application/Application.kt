package jokrey.mockchain.application

import jokrey.mockchain.Mockchain
import jokrey.mockchain.squash.BuildUponSquashHandler
import jokrey.mockchain.squash.PartialReplaceSquashHandler
import jokrey.mockchain.squash.SequenceSquashHandler
import jokrey.mockchain.squash.SquashRejectedException
import jokrey.mockchain.storage_classes.Block
import jokrey.mockchain.storage_classes.RejectionReason
import jokrey.mockchain.storage_classes.Transaction
import jokrey.mockchain.storage_classes.TransactionHash

interface Application {
    /**
     * How a build upon dependency should be resolved in the context of this application
     *     DO NOT ALTER STATE FROM THIS METHOD
     *   This method should share the same logic that is applied in both verify and newBlock (just in a different way)
     *      TODO: find a general way to do that - so that the programmer of the application has to only find one way and not two
     */
    fun getBuildUponSquashHandler(): BuildUponSquashHandler = { _, _ -> throw SquashRejectedException("Application does not support build-on dependency relations") }
    /**
     * How a partial-replace dependency should be resolved in the context of this application
     *     DO NOT ALTER STATE FROM THIS METHOD
     *   This method should share the same logic that is applied in both verify and newBlock (just in a different way)
     *      TODO: find a general way to do that - so that the programmer of the application has to only find one way and not two
     */
    fun getPartialReplaceSquashHandler(): PartialReplaceSquashHandler = { _, _ -> throw SquashRejectedException("Application does not support partial-replace dependency relations") }
    /**
     * How a sequence should be resolved in the context of this application
     *     DO NOT ALTER STATE FROM THIS METHOD
     *   This method should share the same logic that is applied in both verify and newBlock (just in a different way)
     *      TODO: find a general way to do that - so that the programmer of the application has to only find one way and not two
     */
    fun getSequenceSquashHandler(): SequenceSquashHandler = {_ -> throw SquashRejectedException("Application does not support sequence dependency relations") }

    /**
     * Called prior to adding a tx to the mem pool locally.
     * If unsure that a transaction is really invalid, wait for the actual block verification callback and reject it there.
     * Any validation done here do not need to be redone in the block verification.
     * Generally speaking this method can be used to validate a transaction individually and the block verify method is used to validate that they are correct in order and with the current state.
     *
     * @return Optional of a rejection reason, if that optional is empty the transaction is considered accepted
     */
    fun preMemPoolVerify(instance: Mockchain, tx: Transaction): RejectionReason.APP_VERIFY?

    /**
     * Called right after a positive mem pool verify.
     * Notifies this application that the new transaction has passed preliminary verification and is now in the mem pool.
     * Can be used to react to the given transaction and (for example) add another transaction.
     * Note that just because this method is called, there is no guarantee that the transaction is ever persisted or an influence on the application state.
     *
     * This method should never change the application state.
     */
    fun newTxInMemPool(instance: Mockchain, tx: Transaction) {}

    /**
     * Returns the Transactions that were rejected
     *     DO NOT ALTER STATE FROM THIS METHOD
     *     But alter state virtually
     *          obviously in the same way that newBlock does
     *     It should return the same results if:
     *         rejected = verify(tx0, tx1, tx2)
     *         not_rejected = !(tx0, tx1, tx2)contains(rejected) = tx0, tx2
     *         newBlock(tx0, tx2)
     *     ---
     *         if( verify(tx0) == null )
     *             newBlock(tx0)
     *         if( verify(tx1) == null ) // false
     *             newBlock(tx1)
     *         if( verify(tx2) == null )
     *             newBlock(tx2)
     *
     * The Block creator identity is verified by the proof created and checked by the specific consensus algorithm.
     *     Specifically in PoW consensus algorithms an incentive may be given by allowing the block creator to add a special transaction.
     *     This information is required here to verify such a transaction.
     */
    fun blockVerify(instance: Mockchain, blockCreatorIdentity:ByteArray, vararg txs: Transaction) : List<Pair<Transaction, RejectionReason.APP_VERIFY>>

    /**
     * Alters the internal application state
     *     should quite likely take into consideration the dependency and already alter it's internal state based on them
     *     it should NOT wait for them to be removed from the actual chain in txRemoved or txAltered
     *
     * Implementations should not attempt to query the transactions in the block. The given transactions may neither be in mem pool or chain in some circumstances.
     * Additionally it is less efficient than simply accessing the given list.
     * The new transactions in the given list are in the same order as the hashes in the block.
     */
    fun newBlock(instance: Mockchain, block: Block, newTransactions: List<Transaction>)

    //: the following two methods might be more problem than helpful - and be theoretically not required
    //  according to the squash condition (a replay of the squashed chain should yield the same resulting application state as the old(unsquashed) chain)
    //      this condition can not be checked in a real world application
    //  therefore an change to the application state on squash is not required

    /**
     * If the squash algorithm removes a tx this method is called
     *     Generally speaking this method should not alter the internal state - only 'newBlock' should do so
     *     It however is required. It will need to be used by the application for dependency building.
     *     More precisely it can be used to determine that a specific transaction can no longer be depended on, since it was removed
     */
    fun txRemoved(instance: Mockchain, oldHash: TransactionHash, oldTx: Transaction, txWasPersisted: Boolean)
    /**
     * If the squash algorithm alters a tx this method is called
     *     Generally speaking this method should not alter the internal state - only 'newBlock' should do so
     *     It however is required. It will need to be used by the application for dependency building.
     *     More precisely it can be used to determine that a specific transaction has been altered and to store the new hash for future dependency building
     */
    fun txAltered(instance: Mockchain, oldHash: TransactionHash, oldTx: Transaction, newHash: TransactionHash, newTx: Transaction, txWasPersisted: Boolean)

    /**
     * Whenever a tx is rejected by any of the verification mechanisms that are set in place between commit to memory(mem-pool) and commit to storage(block-chain)
     *     It is also called if a tx is rejected by this application's verify method
     *
     * This method should not alter state. It should only be used to determine that a tx should no longer be used as a dependency
     */
    fun txRejected(instance: Mockchain, oldHash: TransactionHash, oldTx: Transaction, reason: RejectionReason)

    /**
     * Todo - this has some SERIOUS requirements to the application - in case of a fork it needs to be able to VERY quickly accept changes.
     */
    fun newEqualInstance(): Application
    /**
     * Todo - this has some SERIOUS requirements to the application - in case of a fork it needs to be able to VERY quickly accept changes.
     */
    fun cleanUpAfterForkInvalidatedThisState()
}