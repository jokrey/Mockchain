package jokrey.mockchain.storage_classes

import jokrey.mockchain.application.Application
import jokrey.utilities.debug_analysis_helper.AverageCallTimeMarker
import jokrey.mockchain.squash.SquashAlgorithmState
import jokrey.mockchain.squash.VirtualChange
import java.util.*
import java.util.logging.Logger

val LOG = Logger.getLogger("Chain")

/**
 * Function to create a chain after a shutdown or crash.
 * Loads the given storage model into a newly created chain and applies a replay to the given, freshly created application.
 */
fun chainFromExistingData(freshApp: Application, store: StorageModel): Chain {
    val chain = Chain(freshApp, store)
    chain.applyReplayTo(freshApp)
    return chain
}

/**
 * The chain is the core of the mockchain framework.
 * It provides methods for committing transactions to the Mempool and performing simulated consensus rounds.
 *
 * It will call the application with any updates.
 *
 * It will run the squash algorithm if instructed to do so and holds some internal variables the algorithm requires.
 *
 * It will access the internal storage model and provides methods for users to query it as well.
 * The storage should not be mutated from outside this chain.
 */
class Chain(val app:Application,
            private val store: StorageModel = NonPersistentStorage(),
            var squashEveryNRounds: Int = -1) : TransactionResolver {  //: changing squashEveryNRounds at runtime MAY cause problems, however it is unlikely and the more I think about it, this may actually be fine NOT SURE YET - IT CAN CAUSE AN ISSUE THIS WAS FIXED WITH roundSinceLastSquash counter

    private val memPool = MemPool()

    /**
     * Commits the given transaction to the internal Mempool.
     * If the hash of the transaction is known to the chain it will be rejected and the application will be notified.
     *
     * The given transaction has to have an unset blockId, i.e. one that is smaller than 0. Otherwise the chain could not override that field.
     */
    fun commitToMemPool(tx: Transaction) {
        if(store[tx.hash] != null || memPool[tx.hash] != null) {
            // - this 'fix' does not work in a distributed environment, it does! txs are guaranteed to be equal, memPool should also be synchronized
            app.txRejected(this, tx.hash, tx, RejectionReason.PRE_MEM_POOL("hash(${tx.hash} already known to the chain - try adding a timestamp field"))
            throw IllegalArgumentException("hash already known to chain this is illegal for now, due to the hash uniqueness problem - tx: $tx")
        }
        if(tx.blockId >= 0)
            throw IllegalArgumentException("block id is not decided by application. chain retains that sovereignty")
        LOG.info("tx committed to mem pool = $tx")
        memPool[tx.hash] = tx
    }


    private var consensusRoundCounter = 0
    private var priorSquashState: SquashAlgorithmState? = null

    /**
     * Will commit the Mempool to the 'permanent' chain, under the verify and squash rules
     */
    fun performConsensusRound(forceSquash: Boolean = false) {
        consensusRoundCounter++

        val proposedTransactions = memPool.getTransactions().toMutableList()

        //VERIFICATION
        //Verified by popular vote within application(s) - actual distributed consensus omitted from this prototype
        var newSquashState:SquashAlgorithmState? = null
        var rejectedTxCount: Int
        do {
            rejectedTxCount=0
            //app verify needs to be before squash verify - otherwise if a tx is rejected here it no longer exists, but squash verify did not know that
            AverageCallTimeMarker.mark_call_start("app verify")
            val appRejectedTransactions = app.verify(this, *proposedTransactions.toTypedArray())
            proposedTransactions.removeAll(appRejectedTransactions.map { it.first })
            rejectedTxCount += appRejectedTransactions.size
            handleRejection(appRejectedTransactions)
            AverageCallTimeMarker.mark_call_end("app verify")

            if(newSquashState!=null && (proposedTransactions.isEmpty() || rejectedTxCount == 0))
                break // if app does not reject anything after a squash run, then squash is not required to be run again - because nothing changed

            AverageCallTimeMarker.mark_call_start("squash verify")
            newSquashState = jokrey.mockchain.squash.findChanges(this, app.getPartialReplaceSquashHandler(), app.getBuildUponSquashHandler(), app.getSequenceSquashHandler(),
                                                                priorSquashState, proposedTransactions.toTypedArray())
            proposedTransactions.removeAll(newSquashState.deniedTransactions.map { it.first })
            rejectedTxCount += newSquashState.deniedTransactions.size
            handleRejection(newSquashState.deniedTransactions)
            AverageCallTimeMarker.mark_call_end("squash verify")
        } while(proposedTransactions.isNotEmpty() && (rejectedTxCount > 0 || newSquashState!!.deniedTransactions.isNotEmpty()))

        newSquashState as SquashAlgorithmState

        if (proposedTransactions.isEmpty()) {
            LOG.info("all transactions have been rejected - not creating a new block")
            assert(memPool.isEmpty())
        }

        //SQUASH
        if(forceSquash || (squashEveryNRounds>0 && consensusRoundCounter % squashEveryNRounds == 0)) {
            AverageCallTimeMarker.mark_call_start("introduceChanges")
            val newlyProposedTx = introduceChanges(newSquashState.virtualChanges, proposedTransactions.toTypedArray())
            AverageCallTimeMarker.mark_call_end("introduceChanges")

            newSquashState.reset()
            proposedTransactions.clear()
            memPool.clear()
            proposedTransactions.addAll(newlyProposedTx)
        }
        priorSquashState = newSquashState


        //STORAGE

        //persist transactions to chain (i.e. bundle and add as block[omitted dependenciesFrom prototype]):
        if(proposedTransactions.isNotEmpty()) {
            AverageCallTimeMarker.mark_call_start("persist new block")
            val newlyAdded = LinkedList<TransactionHash>()
            for (tx in proposedTransactions) {
                memPool.remove(tx.hash)
                val txp = tx.hash

                store[txp] = tx.withBlockId(store.highestBlockId() + 1)
                newlyAdded.add(txp)
            }
            if(!memPool.isEmpty()) throw IllegalStateException("Mem pool should be empty")
            val newBlock = Block(getLatestHash(), newlyAdded)
            store.add(newBlock)
            AverageCallTimeMarker.mark_call_end("persist new block")

            //commit changes generated during squash and addition of newest block (required to be done before app.newBlock, because that one is likely to query the store)
            store.commit()

            //change app state based on added transactions
            app.newBlock(this, newBlock)
        } else {
            store.commit()
        }
    }

    private fun handleRejection(rejected: List<Pair<Transaction, RejectionReason>>) {
        for ((rejectedTransaction, reason) in rejected) {
            if(rejectedTransaction.hash !in memPool) {
                if(rejectedTransaction.hash !in store)
                    throw IllegalStateException("$reason rejected unknown transaction(hash=${rejectedTransaction.hash})")
                else
                    throw IllegalStateException("$reason rejected persisted transaction(hash=${rejectedTransaction.hash})")
            }
            memPool.remove(rejectedTransaction.hash)
            app.txRejected(this, rejectedTransaction.hash, rejectedTransaction, reason)
            LOG.info("$reason rejected: $rejectedTransaction")
        }
    }

    private fun introduceChanges(changes: LinkedHashMap<TransactionHash, VirtualChange>, proposed: Array<Transaction>): Array<Transaction> {
        LOG.info("squashChanges = ${changes}")
        introduceSquashChangesToChain(changes)
        return introduceSquashChangesToList(changes, proposed)
    }

    private class VirtualBlockMutation {
        val deletions = LinkedList<TransactionHash>()
        val changes = LinkedList<Pair<TransactionHash, TransactionHash>>()
        fun isEmpty() = deletions.isEmpty() && changes.isEmpty()
        override fun toString() = "VirtualBlockMutation[$deletions, $changes]"
    }
    private fun introduceSquashChangesToChain(squashChanges: LinkedHashMap<TransactionHash, VirtualChange>) {
        val mutatedBlocks = HashMap<Int, VirtualBlockMutation>()

        for(entry in squashChanges) {
            val (oldHash, change) = entry
            val oldTX = store[oldHash] ?: continue// may be that the change is just a reserved hash marker or the change is to be done in mem-pool(not yet in tx store)

            val mutation = mutatedBlocks[oldTX.blockId] ?: VirtualBlockMutation()

            when (change) {
                VirtualChange.Deletion -> {
                    store.remove(oldHash)

                    mutation.deletions.add(oldHash)

                    try {
                        app.txRemoved(this, oldHash, oldTX, true)
                    } catch (t: Throwable) {
                        LOG.severe("App threw exception on txRemoved - which will be ignored")
                    }
                }
                is VirtualChange.Alteration -> {
                    val newTX = oldTX.changeContentAndRemoveDependencies(change.newContent) //ignore bDependencies, squash removes all bDependencies
                    val newHash = newTX.hash

                    store.replace(oldHash, newHash, newTX)

                    mutation.changes.add(Pair(oldHash, newHash))

                    try {
                        app.txAltered(this, oldHash, oldTX, newHash, newTX, true)
                    } catch (t: Throwable) {
                        LOG.severe("App threw exception on txAltered - which will be ignored")
                    }
                }
                is VirtualChange.DependencyAlteration, is VirtualChange.PartOfSequence -> {
                    change as VirtualChange.DependencyAlteration
                    if (change.newDependencies != null) {
                        val dependencyChangedTx = oldTX.changeDependencies(change.newDependencies)
                        store[oldHash] = dependencyChangedTx
                        //no callback to application, because no data has changed
                        //no change to blocks, because hash has not changed
                    }
                }
                VirtualChange.Error ->
                    throw IllegalStateException("squash algorithm had an internal error - which is illegal on the actual squash and should have been detected and rejected during verify")
            }

            if(!mutation.isEmpty())
                mutatedBlocks[oldTX.blockId] = mutation
        }

        if(mutatedBlocks.isNotEmpty()) {
            var currentBlockId = mutatedBlocks.minBy { it.key }!!.key
            val chainIterator = store.muteratorFrom(currentBlockId)
            var lastBlockHash: Hash? = null
            for (block in chainIterator) {
                if(lastBlockHash == null)
                    lastBlockHash = block.previousBlockHash
                val mutation = mutatedBlocks[currentBlockId]
                val newBlock = if(mutation == null || mutation.isEmpty()) {
                    block.changePreviousHash(lastBlockHash)
                } else {
                    block.rebuildWithDeletionsAndChanges(lastBlockHash, mutation.changes, mutation.deletions)
                }
                chainIterator.set(newBlock)
                lastBlockHash = newBlock.getHeaderHash()
                currentBlockId++
            }
        }
    }


    /**
     * Stable - returned list will have the same relative order, though some items may change or be removed
     */
    private fun introduceSquashChangesToList(squashChanges: LinkedHashMap<TransactionHash, VirtualChange>, transactions: Array<Transaction>): Array<Transaction> {
        val squashedTx = transactions.toMutableList()
        //find altered and deleted transactions in the proposed block and update them before hand

        for(entry in squashChanges) {
            val (oldHash, change) = entry
            val proposedTx = transactions.find { oldHash == it.hash } ?: continue// may be that the change is just a reserved hash marker or the change is to be done in mem-pool(not yet in tx store)

            LOG.finest("oldHash = $oldHash, change = $change")

            when (change) {
                VirtualChange.Deletion -> {
                    app.txRemoved(this, proposedTx.hash, proposedTx, false)
                    squashedTx.removeIf {it.hash == proposedTx.hash}
                }
                is VirtualChange.Alteration -> {
                    val newTx = Transaction(change.newContent)
                    squashedTx.removeIf {it.hash == proposedTx.hash}
                    squashedTx.add(newTx)
                    app.txAltered(this, proposedTx.hash, proposedTx, newTx.hash, newTx, false)
                }
                is VirtualChange.DependencyAlteration, is VirtualChange.PartOfSequence -> {
                    change as VirtualChange.DependencyAlteration
                    if(change.newDependencies != null) {
                        squashedTx.removeIf {it.hash == proposedTx.hash}
                        squashedTx.add(proposedTx.changeDependencies(change.newDependencies))
                    }
                }
                VirtualChange.Error ->
                    throw IllegalStateException("any transaction with an error would have been rejected")
            }
        }
        return squashedTx.toTypedArray()
    }

    /**
     * For every block in the transaction the application's newBlock callback will be called.
     * Since the blocks are already stored, no verification is required.
     *
     * @param freshApp a freshly created application - an instance of the same type of the app that has initially been provided to the constructor of this class
     */
    fun applyReplayTo(freshApp: Application) {
        //Verify omitted for obvious reasons
        for(block in store) {
            freshApp.newBlock(this, block)
        }
    }

    /**
     * Validates for every block that is has the correct previous hash,
     *   that the Merkle root is correct and
     *   that the transaction hashes inside exist and point to the correct transactions.
     *
     * This method queries all transactions in the entire blockchain and is therefore inherently slow.
     * It should only be used for debugging and verification purposes and not in any common part of the application.
     */
    fun validateHashChain(): Boolean {
        val blockIterator = store.iterator()
        var last = blockIterator.next()
        for(block in blockIterator) {
            if (!block.validatePrevious(last))
                return false
            if(block.merkleRoot != block.rebuildMerkleRoot())
                return false
            if(!block.all { it == getUnsure(it)?.hash })
                return false
            last = block
        }
        return true
    }


    /**
     * Returns the transaction at the given hash from the Mempool or permanent memory - if the hash is not resolvable the method will thrown an exepction
     */
    override operator fun get(aHash: TransactionHash) = getUnsure(aHash)!!
    /**
     * Returns the transaction at the given hash from the Mempool or permanent memory - or null if the hash is not resolvable
     */
    override fun getUnsure(aHash: TransactionHash): Transaction? {
        return memPool[aHash] ?: store[aHash]
    }
    /**
     * Returns true if the hash is known to the chain, false if it is not resolvable
     */
    operator fun contains(aHash: TransactionHash) = memPool.contains(aHash) || isPersisted(aHash)
    /**
     * Returns trust if the hash is stored in permanent memory
     */
    fun isPersisted(aHash: TransactionHash) = aHash in store

    /**
     * Loads and returns all blocks currently stored in the chain, should only be used on small blockchains.
     */
    fun getBlocks(): Array<Block> {
        val iterator = store.iterator()
        return Array(store.numberOfBlocks()) {
            iterator.next()
        }
    }

    /**
     * Returns the current number of block stored in the chain
     */
    fun blockCount(): Int = store.numberOfBlocks()

    /**
     * Returns all transactions that either have a dependency or are depended upon by another transaction
     */
    fun getAllTransactionWithDependenciesOrThatAreDependedUpon(): Set<Transaction> = store.getAllPersistedTransactionWithDependenciesOrThatAreDependedUpon()


    /**
     * Adds the current size of the permanent storage and the Mempool to roughly calculate the current size of the chain
     */
    fun calculateStorageRequirementsInBytes() : Long {
        return store.byteSize() +
               memPool.byteSize()
    }

    /**
     * Returns all transactions currently in the Mempool
     */
    fun getMemPoolContent(): Array<Transaction> = memPool.getTransactions().toTypedArray()
    /**
     * Returns all transaction hashes currently in the Mempool
     */
    fun getMemPoolHashes(): Array<TransactionHash> = memPool.getTransactionHashes().toTypedArray()

    /**
     * Returns the latest hash currently known to the chain. Can be used to efficiently compare block chains.
     */
    fun getLatestHash() = store.getLatestHash()

    /**
     * Returns an iterator over all persisted transactions
     */
    fun getPersistedTransactions() = store.txIterator()


}