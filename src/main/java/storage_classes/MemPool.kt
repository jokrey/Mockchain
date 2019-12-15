package storage_classes

/**
 * Straight forward implementation of a local Mempool.
 *
 * Transactions are stored in a map, mapping hashes to transactions.
 */
class MemPool {
    private val data = LinkedHashMap<TransactionHash, Transaction>() //PRESERVES ORDER, THIS MAY BE IMPORTANT IN SOME USE CASES

    operator fun get(hash: TransactionHash) = data[hash]
    operator fun contains(hash: TransactionHash) = data.containsKey(hash)
    operator fun set(hash: TransactionHash, value: Transaction) = data.set(hash, value)
    fun remove(hash: TransactionHash) = data.remove(hash)
    fun isNotEmpty() = data.isNotEmpty()
    fun isEmpty() = data.isEmpty()

    fun getTransactions() = data.values
    fun getTransactionHashes() = data.keys
    fun clear() = data.clear()
    fun byteSize(): Long = data.map { it.key.getHash().size + it.value.bDependencies.map { 1 + it.txp.getHash().size }.sum() + it.value.content.size }.sum().toLong()
}