package jokrey.mockchain

import jokrey.mockchain.application.Application
import jokrey.mockchain.consensus.ConsensusAlgorithmCreator
import jokrey.mockchain.consensus.ManualConsensusAlgorithmCreator
import jokrey.mockchain.storage_classes.*
import jokrey.utilities.misc.Listenable
import java.lang.System.err

/**
 * Blockchain implementation with no network. I.e. a mockchain.
 * It mocks certain application relevant blockchain behaviour such as transactions and blocks.
 *
 * Can be used to tests applications. The visualization engine can be used to display and simulate and evolving application state as represented in the blockchain.
 *
 * The default consensus algorithm is manual and local. I.e. consensus rounds are initiated by simulation or the user and there is no 'proof' of authorization.
 * However the consensus algorithm already includes the squash feature that allows applications to selectively minimize their data usage after the fact.
 */

open class Mockchain(app: Application,
                     store: StorageModel = NonPersistentStorage(),
                     consensus: ConsensusAlgorithmCreator = ManualConsensusAlgorithmCreator()) : AutoCloseable, TransactionResolver {
    var app = app
        set(value) {
            val old = field
            field = value
            appListenable.notify(Pair(old, value))
        }
    internal val memPool = MemPool()
    val chain = Chain( this, store)
    val consensus =  consensus.create(this)

    val appListenable = Listenable<Pair<Application, Application>>()

    init {
        this.consensus.runConsensusLoopInNewThread() //todo - there is an inherent potential race condition here.. If the algorithm instantly produces a new block, then node in the subclass nockchain is not initialized yet.
    }

    fun verifyMemPool(tx: Transaction, storage: TransactionResolver): RejectionReason? {
        if (tx.hash in storage) {
            // - this 'fix' does not work in a distributed environment, it does! txs are guaranteed to be equal, memPool should also be synchronized
            app.txRejected(
                this,
                tx.hash,
                tx,
                RejectionReason.PRE_MEM_POOL("hash(${tx.hash} already known to the chain - try adding a timestamp field")
            )
            return RejectionReason.PRE_MEM_POOL("hash(${tx.hash} already known to the chain - try adding a timestamp field")
        }

        return app.preMemPoolVerify(this, tx)
    }

    /**
     * Commits the given transaction to the internal Mempool.
     * If the hash of the transaction is known to the chain it will be rejected and the application will be notified.
     *
     * The given transaction has to have an unset blockId, i.e. one that is smaller than 0. Otherwise the chain could not override that field.
     */
    open fun commitToMemPool(tx: Transaction, local: Boolean = true) {
        if (tx.blockId >= 0) throw IllegalArgumentException("block id is not decided by application. chain retains that sovereignty - app dev error")
        val rejected = if(tx.hash in memPool)
                RejectionReason.PRE_MEM_POOL("hash(${tx.hash} already known to the chain - try adding a timestamp field")
            else
                verifyMemPool(tx, chain)
        if(rejected != null) {
            app.txRejected(this, tx.hash, tx, rejected)
            throw IllegalArgumentException(rejected.description)
        }

        memPool[tx.hash] = tx
        consensus.notifyNewTransactionInMemPool(tx)
        app.newTxInMemPool(this, tx)
    }

    /** Resets the application state from the chain content */
    fun resetFromChain() {
        app = chain.applyReplayTo(app.newEqualInstance())
    }
    fun resetMemPool() {
        memPool.clear()
    }

    fun addChainChangeListener(changeOccurredCallback: () -> Unit) {
        chain.store.addCommittedChangeListener(changeOccurredCallback)
    }
    fun addMemPoolChangeListener(changeOccurredCallback: () -> Unit) {
        memPool.addChangeListener(changeOccurredCallback)
    }


    /** Returns true if the hash is known to this Mockchain node(i.e. in mempool or chain), false if it is not resolvable */
    override operator fun contains(hash: TransactionHash) = memPool.contains(hash) || chain.isPersisted(hash)
    /** Returns the tx if it is in either mem pool or chain, throws a null pointer exception otherwise */
    override operator fun get(hash: TransactionHash) = memPool.getUnsure(hash)?: chain[hash]
    /** Returns the tx if it is in either mem pool or chain, returns null otherwise */
    override fun getUnsure(hash: TransactionHash) = memPool.getUnsure(hash)?: chain.getUnsure(hash)

    /** Returns the block with the given id, should it exist in the chain */
    fun queryBlock(id: Int) = chain.queryBlock(id)

    /** Returns the current number of blocks stored in the chain */
    fun blockCount() = chain.blockCount()

    /** Returns the current number of tx stored in the chain */
    fun persistedTxCount() = chain.persistedTxCount()

    /** Returns the current number of txs in the mem pool */
    fun numInMemPool() = memPool.size()

    /**
     * Adds the current size of the permanent storage and the Mempool to roughly calculate the current size of the chain
     */
    fun calculateStorageRequirementsInBytes() : Long {
        return chain.calculateStorageRequirementsInBytes() +
                memPool.byteSize()
    }

    internal open fun notifyNewLocalBlockAdded(block: Block) {
        for (txp in block)
            memPool.remove(txp)

        log("mockchain - block locally added")
    }

    open fun log(s: String) {
        err.println(s)
    }

    var isClosed = false
        private set
    override fun close() {
        isClosed = true
        consensus.stop()
        chain.close()
        app.close()
    }
}




/**
 * Function to create a chain after a shutdown or crash.
 * Loads the given storage model into a newly created chain and applies a replay to the given, freshly created application.
 */
fun chainFromExistingData(freshApp: Application, store: StorageModel): Mockchain {
    val node = Mockchain(freshApp, store)
    node.chain.applyReplayTo(freshApp)
    return node
}