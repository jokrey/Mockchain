package jokrey.mockchain.storage_classes

import jokrey.mockchain.Mockchain
import jokrey.mockchain.application.Application
import jokrey.mockchain.squash.SquashAlgorithmState
import jokrey.mockchain.squash.VirtualChange
import jokrey.utilities.debug_analysis_helper.AverageCallTimeMarker
import java.util.*
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.logging.Logger
import kotlin.concurrent.read
import kotlin.concurrent.write

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
 *
 * Thread Safe.
 *    I.e. it is possible for a consensus algorithm to concurrently create a local block and receive a remote block.
 *    Both are verified in regards to the current state. Now one(remote) enters the squashAndAppendVerifiedNewBlock method its previous hash is verified to be equal to the real latest hash.
 *    It is persisted. Now the other(local) enters. The previous hash of that block is now different to the new latest hash and the block is rejected as a whole as 'out of chain order'.
 */
class Chain(val app: Application,
            internal val instance: Mockchain,
            internal val store: StorageModel = NonPersistentStorage()) : TransactionResolver {  //: changing squashEveryNRounds at runtime MAY cause problems, however it is unlikely and the more I think about it, this may actually be fine NOT SURE YET - IT CAN CAUSE AN ISSUE THIS WAS FIXED WITH roundSinceLastSquash counter
    private val rwLock = ReentrantReadWriteLock()
    internal var priorSquashState: SquashAlgorithmState? = null

    /**
     * Internal use by the consensus algorithm. Appends a verified new block and can run a squash introduction.
     *
     * fix.me: i kinda do not like the so very tight cross dependency of verify and squash - but it is very required to keep this efficient
     *
     * returns new block id
     */
    internal fun squashAndAppendVerifiedNewBlock(squash: Boolean, newSquashState: SquashAlgorithmState?, relayBlock: Block, proposed: List<Transaction>): Int {
        rwLock.write {
            var latestHash = getLatestHash()
            if (latestHash != relayBlock.previousBlockHash)
                //CHECK HAS TO BE DONE - IT IS PART OF ENSURING THREAD SAFETY AS DETAILED ABOVE
                //check has to be done up here, squash may change latest hash (in some storage impls)
                throw RejectedExecutionException("latestHash != relayBlock.previousBlockHash")
            if(proposed.map { it.hash }.toList() != relayBlock.toList()) throw RejectedExecutionException("proposed transactions in wrong order - dev error - should never occur")

            //change app state based on added transactions
            app.newBlock(instance, relayBlock, proposed)

            val proposedTransactions: MutableList<Transaction> = proposed.toMutableList()

            //SQUASH
            val squashStateToIntroduce = newSquashState ?: priorSquashState
            priorSquashState = squashStateToIntroduce
            if (squash && squashStateToIntroduce != null) {
                val (newLatestBlockHash, newlyProposedTx) = introduceChanges(squashStateToIntroduce.virtualChanges, proposedTransactions.toTypedArray())
                // - problem: If transactions are altered within the newest block they are invisible to both the apps 'newBlock' callback AND in the relay to other nodes...
                //     solve: app is presented with the relay block

                squashStateToIntroduce.reset()
                proposedTransactions.clear()
                proposedTransactions.addAll(newlyProposedTx)
                if (newLatestBlockHash != null)
                    latestHash = newLatestBlockHash
            }

            //STORAGE
            //persist transactions to chain (i.e. bundle and add as block[omitted dependenciesFrom prototype]):
            val (blockId, newBlock) = persist(store.highestBlockId() + 1, proposedTransactions, latestHash, relayBlock)

            instance.consensus.notifyNewLatestBlockPersisted(newBlock)

            return blockId
        }
    }

    fun appendForkBlock(forkStore: IsolatedStorage, squash: Boolean, forkSquashState: SquashAlgorithmState, newBlockId: Int, relayBlock: Block, proposed: List<Transaction>) : Int =
        rwLock.write {
            if (proposed.map { it.hash }.toList() != relayBlock.toList()) throw RejectedExecutionException("proposed transactions in wrong order - dev error - should never occur")

            var latestHash = relayBlock.previousBlockHash
            val proposedTransactions: MutableList<Transaction> = proposed.toMutableList()

            if (squash) {
                println("OTOSDOASJDAKLJSDHKAJSHD - TODO - does this work?! It is required?! AHHH")

                val (newLatestBlockHash, newlyProposedTx) = introduceChanges(forkSquashState.virtualChanges, proposed.toTypedArray(),
                    writeStore=forkStore)
                // - problem: If transactions are altered within the newest block they are invisible to both the apps 'newBlock' callback AND in the relay to other nodes...
                //     solve: app is presented with the relay block

                forkSquashState.reset()
                proposedTransactions.clear()
                proposedTransactions.addAll(newlyProposedTx)
                if (newLatestBlockHash != null)
                    latestHash = newLatestBlockHash
            }

            val (blockId, _) = persist(newBlockId, proposedTransactions, latestHash, relayBlock,
                writeStore=forkStore)
            return blockId
        }

    private fun persist(newBlockId : Int, txs: List<Transaction>, latestHash: Hash?, relayBlock: Block,
                        writeStore: WriteStorageModel = store): Pair<Int, Block> {
        AverageCallTimeMarker.mark_call_start("persist new block")

        val newlyAdded = LinkedList<TransactionHash>()
        for (tx in txs) {
            val txp = tx.hash
            writeStore.add(txp, tx.withBlockId(newBlockId))
            newlyAdded.add(txp)
        }
        val newBlock = Block(latestHash, relayBlock.proof, newlyAdded)
        writeStore.add(newBlock)

        writeStore.blockCommit()
        AverageCallTimeMarker.mark_call_end("persist new block")
        return Pair(newBlockId, newBlock)
    }

    private fun introduceChanges(changes: LinkedHashMap<TransactionHash, VirtualChange>, proposed: Array<Transaction>,
                                 writeStore: WriteStorageModel = store): Pair<Hash?, Array<Transaction>> {
        try {
            AverageCallTimeMarker.mark_call_start("introduceChanges")
            instance.log("squashChanges = $changes")
            val newLatestBlockHash = introduceSquashChangesToChain(changes, writeStore)
            return Pair(newLatestBlockHash, introduceSquashChangesToList(changes, proposed))
        } finally {
            AverageCallTimeMarker.mark_call_end("introduceChanges")
        }
    }

    private class VirtualBlockMutation {
        val deletions = LinkedList<TransactionHash>()
        val changes = LinkedList<Pair<TransactionHash, TransactionHash>>()
        fun isEmpty() = deletions.isEmpty() && changes.isEmpty()
        override fun toString() = "VirtualBlockMutation[$deletions, $changes]"
    }

    private fun introduceSquashChangesToChain(squashChanges: LinkedHashMap<TransactionHash, VirtualChange>,
                                              writeStore: WriteStorageModel): Hash? {
        val mutatedBlocks = HashMap<Int, VirtualBlockMutation>()

        for (entry in squashChanges) {
            val (oldHash, change) = entry
            val oldTX =
                writeStore[oldHash]// may be that the change is just a reserved hash marker or the change is to be done in mem-pool(not yet in tx store)

            val mutation = mutatedBlocks[oldTX.blockId] ?: VirtualBlockMutation()

            when (change) {
                VirtualChange.Deletion -> {
                    writeStore.remove(oldHash)

                    mutation.deletions.add(oldHash)

                    try {
                        app.txRemoved(instance, oldHash, oldTX, true)
                    } catch (t: Throwable) {
                        LOG.severe("App threw exception on txRemoved - which will be ignored")
                    }
                }
                is VirtualChange.Alteration -> {
                    val newTX = oldTX.changeContentAndRemoveDependencies(change.newContent) //ignore bDependencies, squash removes all bDependencies
                    val newHash = newTX.hash

                    writeStore.replace(oldHash, newHash, newTX)

                    mutation.changes.add(Pair(oldHash, newHash))

                    try {
                        app.txAltered(instance, oldHash, oldTX, newHash, newTX, true)
                    } catch (t: Throwable) {
                        LOG.severe("App threw exception on txAltered - which will be ignored")
                    }
                }
                is VirtualChange.DependencyAlteration, is VirtualChange.PartOfSequence -> {
                    change as VirtualChange.DependencyAlteration
                    if (change.newDependencies != null) {
                        val dependencyChangedTx = oldTX.changeDependencies(change.newDependencies)
                        writeStore.replace(oldHash, dependencyChangedTx)
                        //no callback to application, because no data has changed
                        //no change to blocks, because hash has not changed
                    }
                }
                VirtualChange.Error ->
                    throw IllegalStateException("squash algorithm had an internal error - which is illegal on the actual squash and should have been detected and rejected during verify")
            }

            if (!mutation.isEmpty())
                mutatedBlocks[oldTX.blockId] = mutation
        }


        if (mutatedBlocks.isNotEmpty()) {
            var currentBlockId = mutatedBlocks.minBy { it.key }!!.key
            val chainIterator = writeStore.muteratorFrom(currentBlockId)
            var lastBlockHash: Hash? = null
            for (block in chainIterator) {
                if (lastBlockHash == null)
                    lastBlockHash = block.previousBlockHash
                val mutation = mutatedBlocks[currentBlockId]
                val newBlock = if (mutation == null || mutation.isEmpty()) {
                    block.changePreviousHash(lastBlockHash)
                } else {
                    block.rebuildWithDeletionsAndChanges(lastBlockHash, mutation.changes, mutation.deletions)
                }
                chainIterator.set(newBlock)
                lastBlockHash = newBlock.getHeaderHash()
                currentBlockId++
            }
            return lastBlockHash
        }
        return null
    }


    /**
     * Stable - returned list will have the same relative order, though some items may change or be removed
     */
    private fun introduceSquashChangesToList(squashChanges: LinkedHashMap<TransactionHash, VirtualChange>, transactions: Array<Transaction>): Array<Transaction> {
        val squashedTx = transactions.toMutableList()
        //find altered and deleted transactions in the proposed block and update them before hand

        for (entry in squashChanges) {
            val (oldHash, change) = entry
            val proposedTx = transactions.find { oldHash == it.hash }
                    ?: continue// may be that the change is just a reserved hash marker or the change is to be done in mem-pool(not yet in tx store)

            LOG.finest("oldHash = $oldHash, change = $change")

            when (change) {
                VirtualChange.Deletion -> {
                    app.txRemoved(instance, proposedTx.hash, proposedTx, false)
                    squashedTx.removeIf { it.hash == proposedTx.hash }
                }
                is VirtualChange.Alteration -> {
                    val newTx = Transaction(change.newContent)
                    squashedTx.removeIf { it.hash == proposedTx.hash }
                    squashedTx.add(newTx)
                    app.txAltered(instance, proposedTx.hash, proposedTx, newTx.hash, newTx, false)
                }
                is VirtualChange.DependencyAlteration, is VirtualChange.PartOfSequence -> {
                    change as VirtualChange.DependencyAlteration
                    if (change.newDependencies != null) {
                        squashedTx.removeIf { it.hash == proposedTx.hash }
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
     * blockLimit is the highest block that will still be added, if < 0 or omitted it will apply the entire chain
     *
     * @param freshApp a freshly created application - an instance of the same type of the app that has initially been provided to the constructor of this class
     */
    fun applyReplayTo(freshApp: Application, blockLimit: Int = -1): Application {
        rwLock.write {
            var counter = 0
            //Verify omitted for obvious reasons
            for (block in store) {
                if(blockLimit in 0 until counter) break
                freshApp.newBlock(instance, block, block.map { this[it] })
                counter++
            }
            return freshApp
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
        rwLock.read {
            val blockIterator = store.iterator()
            var last = blockIterator.next()
            for (block in blockIterator) {
                if (!block.validatePrevious(last)) {
                    return false
                }
                if (block.merkleRoot != block.rebuildMerkleRoot())
                    return false
                if (!block.all { it == getUnsure(it)?.hash })
                    return false
                last = block
            }
            return true
        }
    }


    /**
     * Returns the transaction at the given hash from the Mempool or permanent memory - if the hash is not resolvable the method will thrown an exepction
     */
    override operator fun get(hash: TransactionHash) = getUnsure(hash)!!
////        rwLock.read {
//            store[hash]
////        }

    /**
     * Returns the transaction at the given hash from the Mempool or permanent memory - or null if the hash is not resolvable
     */
    override fun getUnsure(hash: TransactionHash) = store.getUnsure(hash)
////        rwLock.read {
//            store[hash]
////        }
    /**
     * Returns true if the hash is known to the chain, false if it is not resolvable
     */
    override operator fun contains(hash: TransactionHash) = isPersisted(hash)

    /**
     * Returns trust if the hash is stored in permanent memory
     */
    fun isPersisted(aHash: TransactionHash) =
//        rwLock.read {
            aHash in store
//        }

    fun queryBlockHash(id: Int) =
//        rwLock.read {
            store.queryBlockHash(id)
//        }
    fun queryBlock(id: Int): Block =
//        rwLock.read {
            store.queryBlock(id)
//        }

    /**
     * Loads and returns all blocks currently stored in the chain, should only be used on small blockchains.
     */
    fun getBlocks(): Array<Block> {
        rwLock.read {
            return store.iterator().asSequence().toList().toTypedArray()
        }
    }

    /**
     * Returns the current number of block stored in the chain
     */
    fun blockCount(): Int =
//        rwLock.read {
            store.numberOfBlocks()
//        }

    /**
     * Returns the current number of transactions stored in the chain
     */
    fun persistedTxCount(): Int =
//        rwLock.read {
            store.numberOfTx()
//        }

    /**
     * Returns the latest hash currently known to the chain. Can be used to efficiently compare block chains.
     */
    fun getLatestHash() =
        rwLock.read {
            store.getLatestHash()
        }

    /**
     * Returns an iterator over all persisted transactions
     * NOT THREAD SAFE
     */
    fun getPersistedTransactions() = store.txIterator()

    /**
     * Adds the current size of the permanent storage and the Mempool to roughly calculate the current size of the chain
     */
    fun calculateStorageRequirementsInBytes() = rwLock.read { store.byteSize() }
    fun close() = rwLock.write { store.close() }
}