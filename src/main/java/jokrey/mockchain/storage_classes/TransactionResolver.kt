package jokrey.mockchain.storage_classes

/**
 * Interface used within the squash algorithm to allow different models of storing transactions to be used to calculate the dependency level of a transaction
 */
interface TransactionResolver {
    /**
     * Returns the transaction at the given hash from the Mempool or permanent memory - if the hash is not resolvable the method will thrown an exepction
     */
    operator fun get(aHash: TransactionHash) : Transaction
    /**
     * Returns the transaction at the given hash from the Mempool or permanent memory - or null if the hash is not resolvable
     */
    fun getUnsure(aHash: TransactionHash): Transaction?
}

fun Map<TransactionHash, Transaction>.asTxResolver(): TransactionResolver {
    val map = this
    return object : TransactionResolver {
        override fun get(aHash: TransactionHash) = map.getValue(aHash)
        override fun getUnsure(aHash: TransactionHash) = map[aHash]
    }
}