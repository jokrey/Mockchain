package jokrey.mockchain.consensus

import jokrey.mockchain.Mockchain
import jokrey.mockchain.application.Application
import jokrey.mockchain.squash.SquashAlgorithmState
import jokrey.mockchain.storage_classes.*
import jokrey.utilities.debug_analysis_helper.AverageCallTimeMarker
import java.net.SocketAddress

/**
 * Will propose blocks to the chain. Either based on internal rules on its own, or upon an external command.
 *
 * Will interface with chain(for storage), mempool(for querying txs) and application(for tx verification)
 *
 * Will receive data from the network (remotely proposed blocks)
 *
 * Can react to external changes such as new transactions being added to the mem pool.
 *
 * NOTE: Synchronous consensus algorithms like BFT PoS may be incompatible with this interface.
 *
 * @author jokrey
 */
abstract class ConsensusAlgorithm(protected val instance: Mockchain) : Runnable {
    internal fun runConsensusLoopInNewThread() = Thread(this).start()

    /**
     * Will attempt to add as many of the selected transactions AS POSSIBLE.
     * Those transactions that are rejected by either application verification and squash verification will be ignored.
     * Once the remaining transactions are persisted to the chain they will be removed from the mem pool automatically.
     */
    protected fun attemptCreateAndAddLocalBlock(selectedTransactions: List<Transaction>,
                                      proof: Proof,
                                      latestHash: Hash? = instance.chain.getLatestHash(),
                                      requestSquash: Boolean = false) {
        val (newSquashState, verifiedSortedTransactions) = removeAllRejectedTransactionsFrom(
            blockCreatorIdentity = getLocalIdentity(),
            proposedTransactions = selectedTransactions
        )
        createAndAddLocalBlock(newSquashState, verifiedSortedTransactions, latestHash, proof, requestSquash)
    }

    /**
     * Will persist the given transactions to the chain. They have to be locally guaranteed to be valid+legal+sorted.
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
        if(isPaused) throw IllegalStateException("cannot create local block if system is paused")

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
    internal fun attemptVerifyAndAddRemoteBlock(receivedBlock: Block, resolver: TransactionResolver, overridePause: Boolean = false): Int {
        if(isPaused && !overridePause) throw IllegalStateException("cannot create remote block if system is paused")

        val requestSquash = extractRequestSquashFromProof(receivedBlock.proof)
        val blockCreatorIdentity = extractBlockCreatorIdentityFromProof(receivedBlock.proof)

        val proposedTransactions = receivedBlock.map { resolver[it] }
        val (newSquashState, verifiedSortedTransactions) = removeAllRejectedTransactionsFrom(
            blockCreatorIdentity = blockCreatorIdentity,
            proposedTransactions = proposedTransactions
        )

        if(verifiedSortedTransactions.size != receivedBlock.size) //if even a single transaction was rejected
            return -1

        return instance.chain.squashAndAppendVerifiedNewBlock(requestSquash, newSquashState, receivedBlock, verifiedSortedTransactions)
    }


    fun attemptVerifyAndAddForkedBlock(
        forkStore: IsolatedStorage,
        receivedForkBlock: Block, receivedBlockId: Int, resolver: TransactionResolver, forkSquashState: SquashAlgorithmState?, forkApplication: Application, isFirst: Boolean): Pair<SquashAlgorithmState?, Int> {
        val requestSquash = extractRequestSquashFromProof(receivedForkBlock.proof)
        val blockCreatorIdentity = extractBlockCreatorIdentityFromProof(receivedForkBlock.proof)

        val proposedTransactions = receivedForkBlock.map { resolver[it] }
        val (newSquashState, verifiedSortedTransactions) = removeAllRejectedTransactionsFrom(
            forkApplication,
            blockCreatorIdentity,
            forkStore,
            proposedTransactions,
            overridePreviousSquashState = true,
            previousSquashStateOverride = forkSquashState
        )

        if(verifiedSortedTransactions.size != receivedForkBlock.size) //if even a single transaction was rejected
            return Pair(null, -1)

        return Pair(newSquashState, instance.chain.appendForkBlock(forkStore, requestSquash, newSquashState, receivedBlockId, receivedForkBlock, verifiedSortedTransactions))
    }


    abstract override fun run()
    internal abstract fun notifyNewLatestBlockPersisted(newBlock: Block)
    internal abstract fun notifyNewTransactionInMemPool(newTx: Transaction)

    internal abstract fun validateJustReceivedProof(proof: Proof, previousBlockHash: Hash?, merkleRoot: Hash): Boolean
    abstract fun allowFork(forkIndex: Int, ownBlockHeight: Int, remoteBlockHeight: Int): Boolean

    protected abstract fun extractRequestSquashFromProof(proof: Proof): Boolean
    protected abstract fun extractBlockCreatorIdentityFromProof(proof: Proof): ByteArray
    protected abstract fun getLocalIdentity(): ByteArray

    abstract fun getCreator(): ConsensusAlgorithmCreator

    internal var isPaused = false
        private set
    open fun pause() { isPaused = true }
    open fun resume() { isPaused = false }
    open fun stop() {}


    //i kinda do not like the so very tight cuppling of verify and squash - but it is very required to keep this efficient
    /**
     * Returns new SquashAlgorithmState and sorted+verified proposed txs
     *     (must be sorted, because input is mem pool and mem pool is chaos)
     */
    protected fun removeAllRejectedTransactionsFrom(
        app: Application = instance.app, blockCreatorIdentity: ByteArray,
        storage: WriteStorageModel = instance.chain.store, proposedTransactions: List<Transaction>,
        overridePreviousSquashState: Boolean = false, previousSquashStateOverride: SquashAlgorithmState? = null
    ) : Pair<SquashAlgorithmState, List<Transaction>> {
        var newSquashState: SquashAlgorithmState? = null
        var proposed = proposedTransactions.toMutableList()
        do {
            var rejectedTxCount = 0
            AverageCallTimeMarker.mark_call_start("app verify")
            //todo - in most cases single verify is called twice. This is not cool.
            val appRejectedTransactionsSingleVerify = proposed.mapNotNull { Pair(it, instance.verifyMemPool(it, storage)?: return@mapNotNull null) }
            val appRejectedTransactionsBlockVerify = app.blockVerify(instance, blockCreatorIdentity, *proposed.toTypedArray())
            val appRejectedTransactions = appRejectedTransactionsSingleVerify + appRejectedTransactionsBlockVerify
            handleRejection(proposed, appRejectedTransactions)
            proposed.removeAll(appRejectedTransactions.map { it.first })
            rejectedTxCount += appRejectedTransactions.size
            AverageCallTimeMarker.mark_call_end("app verify")

            if(newSquashState!=null && (proposed.isEmpty() || rejectedTxCount == 0))
                break // if app does not reject anything after a squash run, then squash is not required to be run again - because nothing changed

            AverageCallTimeMarker.mark_call_start("squash verify")
            val changes = jokrey.mockchain.squash.findChanges(
                    storage,
                    if(overridePreviousSquashState) previousSquashStateOverride else instance.chain.priorSquashState,
                    app.getBuildUponSquashHandler(),
                    app.getSequenceSquashHandler(),
                    app.getPartialReplaceSquashHandler(),
                    proposed.toTypedArray()
                )
            newSquashState = changes.first

            handleRejection(proposed, newSquashState.rejections)
            rejectedTxCount += newSquashState.rejections.size
            AverageCallTimeMarker.mark_call_end("squash verify")
            proposed = changes.second.toMutableList()
        } while(proposed.isNotEmpty() && rejectedTxCount > 0)

        return Pair(newSquashState!!, proposed)
    }


    //TODO - is it reasonable to remove the transaction from the mem pool right away?
    //     - the app should have a say in that, no? If it for example should be resubmitted
//         - technically though the app does have a say - it gets a callback and can resubmit
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

    open fun allowProvideCatchUpTo(peer: SocketAddress, blockCount: Int, remoteBlockHeight: Int): Boolean = true


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
