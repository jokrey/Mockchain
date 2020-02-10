package jokrey.mockchain.storage_classes

import jokrey.mockchain.application.Application
import jokrey.utilities.debug_analysis_helper.AverageCallTimeMarker
import jokrey.mockchain.squash.SquashAlgorithmState
import jokrey.mockchain.squash.VirtualChange
import java.util.*
import java.util.logging.Logger

val LOG = Logger.getLogger("Chain")


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
class Chain(val app: Application,
            private val store: StorageModel = NonPersistentStorage()) : TransactionResolver {  //: changing squashEveryNRounds at runtime MAY cause problems, however it is unlikely and the more I think about it, this may actually be fine NOT SURE YET - IT CAN CAUSE AN ISSUE THIS WAS FIXED WITH roundSinceLastSquash counter
    /**
     * Internal use by the consensus algorithm. Appends a verified new block and can run a squash introduction
     */
    fun appendVerifiedNewBlock(squash: Boolean, newSquashState: SquashAlgorithmState, proposedTransactions: MutableList<Transaction>, proof: Proof) {
        //SQUASH
        if (squash) {
            AverageCallTimeMarker.mark_call_start("introduceChanges")
            val newlyProposedTx = introduceChanges(newSquashState.virtualChanges, proposedTransactions.toTypedArray())
            AverageCallTimeMarker.mark_call_end("introduceChanges")

            newSquashState.reset()
            proposedTransactions.clear()
            proposedTransactions.addAll(newlyProposedTx)
        }

        //STORAGE
        //persist transactions to chain (i.e. bundle and add as block[omitted dependenciesFrom prototype]):
        if (proposedTransactions.isNotEmpty()) {
            AverageCallTimeMarker.mark_call_start("persist new block")
            val newlyAdded = LinkedList<TransactionHash>()
            for (tx in proposedTransactions) {
                val txp = tx.hash

                store[txp] = tx.withBlockId(store.highestBlockId() + 1)
                newlyAdded.add(txp)
            }
            val newBlock = Block(getLatestHash(), proof, newlyAdded)
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
    override operator fun get(hash: TransactionHash) = getUnsure(hash)!!
    /**
     * Returns the transaction at the given hash from the Mempool or permanent memory - or null if the hash is not resolvable
     */
    override fun getUnsure(hash: TransactionHash): Transaction? {
        return store[hash]
    }
    /**
     * Returns true if the hash is known to the chain, false if it is not resolvable
     */
    override operator fun contains(hash: TransactionHash) = isPersisted(hash)
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
        return store.byteSize()
    }

    /**
     * Returns the latest hash currently known to the chain. Can be used to efficiently compare block chains.
     */
    fun getLatestHash() = store.getLatestHash()

    /**
     * Returns an iterator over all persisted transactions
     */
    fun getPersistedTransactions() = store.txIterator()
}