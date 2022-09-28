package jokrey.mockchain.storage_classes

import jokrey.mockchain.squash.dependencyLevelSortWithinBlockBoundariesButAlsoEliminateMultiLevelDependencies
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.collections.LinkedHashMap
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Straight forward implementation of a local Mempool.
 *
 * Transactions are stored in a map, mapping hashes to transactions.
 *
 * Thread safe.
 */
class MemPool : TransactionResolver {
    private val rwLock = ReentrantReadWriteLock(false)
    private val data = LinkedHashMap<TransactionHash, Transaction>() //PRESERVES ORDER, THIS MAY BE IMPORTANT IN SOME USE CASES

    override operator fun get(hash: TransactionHash) = rwLock.read { data[hash]!! }
    override fun getUnsure(hash: TransactionHash) = rwLock.read { data[hash] }

    operator fun set(hash: TransactionHash, value: Transaction) {
        rwLock.write { data[hash] = value }
        fireChangeOccurred()
    }
    fun remove(hash: TransactionHash) {
        rwLock.write { data.remove(hash) }
        fireChangeOccurred()
    }
    fun clear() {
        rwLock.write { data.clear() }
        fireChangeOccurred()
    }

    override operator fun contains(hash: TransactionHash) = rwLock.read { data.containsKey(hash) }
    fun isNotEmpty() = rwLock.read { data.isNotEmpty() }
    fun isEmpty() = rwLock.read { data.isEmpty() }
    fun size() = rwLock.read { data.size }

    fun getTransactions() = rwLock.read { data.values.toTypedArray() }
    fun getTransactionHashes() = rwLock.read { data.keys.toTypedArray() }
    fun byteSize(): Long = rwLock.read { data.map { it.key.getHash().size + it.value.bDependencies.map { 1 + it.txp.getHash().size }.sum() + it.value.content.size }.sum().toLong() }


    private val changeOccurredCallbacks = LinkedList<() -> Unit>()
    fun addChangeListener(changeOccurredCallback: () -> Unit) {
        changeOccurredCallbacks.add(changeOccurredCallback)
    }
    private fun fireChangeOccurred() {
        try {
            for (c in changeOccurredCallbacks) c()
        } catch (t: Throwable) {System.err.println("mem pool callbacks are not supposed to throw exceptions.")}
    }
}