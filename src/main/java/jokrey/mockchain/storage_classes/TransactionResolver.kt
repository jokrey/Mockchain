package jokrey.mockchain.storage_classes

/**
 * Interface used within the squash algorithm to allow different models of storing transactions to be used to calculate the dependency level of a transaction
 */
interface TransactionResolver {
    /**
     * Returns the transaction at the given hash from the Mempool or permanent memory - if the hash is not resolvable the method will thrown an exepction
     */
    operator fun get(hash: TransactionHash) : Transaction
    /**
     * Returns the transaction at the given hash from the Mempool or permanent memory - or null if the hash is not resolvable
     */
    fun getUnsure(hash: TransactionHash): Transaction?

    /** Returns whether the given hash can be queried. */
    operator fun contains(hash: TransactionHash): Boolean


    fun combineWith(other: TransactionResolver): TransactionResolver {
        val original = this
        return object : TransactionResolver {
            override fun get(hash: TransactionHash)        = original.getUnsure(hash) ?: other[hash]
            override fun getUnsure(hash: TransactionHash)  = original.getUnsure(hash) ?: other.getUnsure(hash)
            override fun contains(hash: TransactionHash)   = original.contains(hash) || other.contains(hash)
        }
    }
}

fun Map<TransactionHash, Transaction>.asTxResolver(): TransactionResolver {
    val map = this
    return object : TransactionResolver {
        override fun get(hash: TransactionHash) = map.getValue(hash)
        override fun getUnsure(hash: TransactionHash) = map[hash]
        override fun contains(hash: TransactionHash): Boolean = hash in map
    }
}

fun Array<Transaction>.asTxResolver() = this.associateBy { it.hash }.asTxResolver()