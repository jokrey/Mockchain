package jokrey.mockchain.storage_classes

import java.util.*
import kotlin.collections.LinkedHashMap

/**
 * Straight forward implementation of a local Mempool.
 *
 * Transactions are stored in a map, mapping hashes to transactions.
 */
class MemPool : TransactionResolver {
    private val data = LinkedHashMap<TransactionHash, Transaction>() //PRESERVES ORDER, THIS MAY BE IMPORTANT IN SOME USE CASES

    override operator fun get(hash: TransactionHash) = data[hash]!!
    override fun getUnsure(hash: TransactionHash) = data[hash]

    operator fun set(hash: TransactionHash, value: Transaction) {
        data[hash] = value
        fireChangeOccurred()
    }
    fun remove(hash: TransactionHash) {
        data.remove(hash)
        fireChangeOccurred()
    }
    fun clear() {
        data.clear()
        fireChangeOccurred()
    }

    override operator fun contains(hash: TransactionHash) = data.containsKey(hash)
    fun isNotEmpty() = data.isNotEmpty()
    fun isEmpty() = data.isEmpty()

    fun getTransactions() = data.values
    fun getTransactionHashes() = data.keys
    fun byteSize(): Long = data.map { it.key.getHash().size + it.value.bDependencies.map { 1 + it.txp.getHash().size }.sum() + it.value.content.size }.sum().toLong()


    private val changeOccurredCallbacks = LinkedList<() -> Unit>()
    fun addChangeListener(changeOccurredCallback: () -> Unit) {
        changeOccurredCallbacks.add(changeOccurredCallback)
    }
    private fun fireChangeOccurred() {
        for(c in changeOccurredCallbacks) c()
    }
}