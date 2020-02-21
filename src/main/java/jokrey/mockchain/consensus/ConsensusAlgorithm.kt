package jokrey.mockchain.consensus

import jokrey.mockchain.Mockchain
import jokrey.mockchain.squash.SquashAlgorithmState
import jokrey.mockchain.storage_classes.*
import jokrey.utilities.debug_analysis_helper.AverageCallTimeMarker

/**
 * Will propose blocks to the chain. Either based on internal rules on its own, or upon an external command.
 *
 * Will interface with chain(for storage), mempool(for querying txs) and application(for tx verification)
 *
 * Will receive data from the network (remotely proposed blocks)
 *
 * Can react to external changes such as new transactions being added to the mem pool.
 *
 * @author jokrey
 */
abstract class ConsensusAlgorithm(protected val instance: Mockchain) : Runnable {
    /**
     * Will attempt to add as many of the selected transactions AS POSSIBLE.
     * Those transactions that are rejected by either application verification and squash verification will be ignored.
     * Once the remaining transactions are persisted to the chain they will be removed from the mem pool automatically.
     */
    protected fun attemptCreateAndAddLocalBlock(selectedTransactions: List<Transaction>,
                                      proof: Proof,
                                      latestHash: Hash? = instance.chain.getLatestHash(),
                                      requestSquash: Boolean = false) {
        val selected = selectedTransactions.toMutableList()
        val newSquashState = removeAllRejectedTransactionsFrom(getLocalIdentity(), selected)
        createAndAddLocalBlock(newSquashState, selected, latestHash, proof, requestSquash)
    }

    /**
     * Will persist the given transactions to the chain. They have to be locally guaranteed to be valid and legal.
     * The given squash state has to be valid for reintroduction as well, preferably it was created using removeAllRejectedTransactionsFrom.
     * Once the transactions are persisted to the chain they will be removed from the mem pool automatically.
     * @see removeAllRejectedTransactionsFrom
     */
    protected fun createAndAddLocalBlock(newSquashState: SquashAlgorithmState?,
                               selectedVerifiedTransactions: List<Transaction>,
                               latestHash: Hash?,
                               proof: Proof,
                               requestSquash: Boolean = false,
                               merkleRoot: Hash = MerkleTree(*selectedVerifiedTransactions.map { it.hash }.toTypedArray()).getRoot()) {
        val selectedVerifiedTransactionHashes = selectedVerifiedTransactions.map { it.hash }.toMutableList()

        val newBlock = Block(latestHash, proof, merkleRoot, selectedVerifiedTransactionHashes.toTypedArray())
        val newBlockId = instance.chain.squashAndAppendVerifiedNewBlock(requestSquash, newSquashState, newBlock, selectedVerifiedTransactions)

        if(newBlockId != -1)
            instance.notifyNewLocalBlockAdded(newBlock)
    }

    /**
     * Will verify and if valid add the received remote block to the chain.
     * If even a single transaction in the given block is determined to be invalid the entire block will be rejected and not added to the chain.
     */
    internal fun attemptVerifyAndAddRemoteBlock(receivedBlock: Block, resolver: TransactionResolver): Int {
        val requestSquash = extractRequestSquashFromProof(receivedBlock.proof)
        val blockCreatorIdentity = extractBlockCreatorIdentityFromProof(receivedBlock.proof)

        val proposedTransactions = receivedBlock.map { resolver[it] }.toMutableList()
        val newSquashState = removeAllRejectedTransactionsFrom(blockCreatorIdentity, proposedTransactions)

        if(proposedTransactions.size != receivedBlock.size) //if even a single transaction was rejected
            return -1

        return instance.chain.squashAndAppendVerifiedNewBlock(requestSquash, newSquashState, receivedBlock, proposedTransactions)
    }

    internal fun runConsensusLoopInNewThread() {
        Thread(this).start()
    }


    abstract override fun run()
    internal abstract fun notifyNewLatestBlockPersisted(newBlock: Block)
    internal abstract fun notifyNewTransactionInMemPool(newTx: Transaction)

    internal abstract fun validateJustReceivedProof(proof: Proof, previousBlockHash: Hash?, merkleRoot: Hash): Boolean

    protected abstract fun extractRequestSquashFromProof(proof: Proof): Boolean
    protected abstract fun extractBlockCreatorIdentityFromProof(proof: Proof): ByteArray
    protected abstract fun getLocalIdentity(): ByteArray

    abstract fun getCreator(): ConsensusAlgorithmCreator


    //todo - i kinda do not like the so very tight cuppling of verify and squash - but it is very required to keep this efficient
    protected fun removeAllRejectedTransactionsFrom(blockCreatorIdentity: ByteArray, proposed: MutableList<Transaction>) : SquashAlgorithmState? {
        var newSquashState: SquashAlgorithmState? = null
        do {
            var rejectedTxCount = 0
            AverageCallTimeMarker.mark_call_start("app verify")
            val appRejectedTransactions = instance.app.verify(instance, blockCreatorIdentity, *proposed.toTypedArray())
            handleRejection(proposed, appRejectedTransactions)
            proposed.removeAll(appRejectedTransactions.map { it.first })
            rejectedTxCount += appRejectedTransactions.size
            AverageCallTimeMarker.mark_call_end("app verify")

            if(newSquashState!=null && (proposed.isEmpty() || rejectedTxCount == 0))
                break // if app does not reject anything after a squash run, then squash is not required to be run again - because nothing changed

            AverageCallTimeMarker.mark_call_start("squash verify")
            newSquashState = instance.chain.squashVerify(proposed)
            handleRejection(proposed, newSquashState.rejections)
            proposed.removeAll(newSquashState.rejections.map { it.first })
            rejectedTxCount += newSquashState.rejections.size
            AverageCallTimeMarker.mark_call_end("squash verify")
        } while(proposed.isNotEmpty() && rejectedTxCount > 0)

        return newSquashState
    }


    //TODO - is it reasonable to remove the transaction from the mem pool right away?
    //     - the app should have a say in that, no?
    private fun handleRejection(proposed: List<Transaction>, rejected: List<Pair<Transaction, RejectionReason>>) {
        for ((rejectedTransaction, reason) in rejected) {
            if(rejectedTransaction !in proposed) {
                if(rejectedTransaction.hash !in instance.chain)
                    throw IllegalStateException("$reason rejected unknown transaction(hash=${rejectedTransaction.hash})")
                else
                    throw IllegalStateException("$reason rejected persisted transaction(hash=${rejectedTransaction.hash})")
            }
            instance.memPool.remove(rejectedTransaction.hash)
            instance.app.txRejected(instance, rejectedTransaction.hash, rejectedTransaction, reason)
            instance.log("$reason rejected: $rejectedTransaction")
        }
    }


    /*
    Ways for a tx into the local chain:
    1.  ALL LOCAL
        - tx committed locally
        - tx added to local mempool
        - tx selected into staged block        /by local consensus instance
        - tx among verified in staged block    /by local consensus instance
        - tx in local proposed block
        (- local proposed block broadcast to peers
        - proposed block introduced into the local chain
    2.  REMOTE COMMIT - LOCAL PROPOSAL
        - tx committed remotely
        - tx received via mempool synchronization
        - tx added to local mempool
        --- ETC. SEE 1.
    3.  REMOTE COMMIT - REMOTE PROPOSAL
        - tx committed remotely
        - tx added to remote mempool
        - tx selected into staged block        /by remote consensus instance
        - tx among verified in staged block    /by remote consensus instance
        - tx in remote proposed block
        - remote proposed block broadcast to peers
        - remote proposed block received by local mockchain instance
        - proposed block proof verified        /by local consensus instance
        - ALL txs proposed of block verified   /by local consensus instance
        (- accepted block further broadcast to peers {OR DIRECTLY AFTER PROOF VERIFY FOR SPEED??}
        - proposed block introduced into the local chain
    4.  LOCAL COMMIT - REMOTE PROPOSAL
        - trivial
    FORK BLOCK RECEIVED NOT HANDLED YET....
     */
}
