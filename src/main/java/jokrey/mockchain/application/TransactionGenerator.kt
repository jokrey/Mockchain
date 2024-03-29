package jokrey.mockchain.application

import jokrey.mockchain.Mockchain
import jokrey.mockchain.storage_classes.Dependency
import jokrey.mockchain.storage_classes.Transaction
import java.util.*

interface TransactionGenerator {
    /**
     * Generates a random, next transaction based on the given parameters 'step' and 'random'.
     *
     * Will either return the newly generated transaction can directly add the transaction to the Mempool through the chain parameter.
     *
     * @param step Should randomness not be desired this parameter can function as a differentiator between calls.
     * @param random Should a randomization be desirable this object should be used as the source so the randomness is reproducible
     */
    fun next(instance: Mockchain, step: Long, random: Random): Optional<Transaction>
}


class NoTransactionGenerator : TransactionGenerator {
    override fun next(instance: Mockchain, step: Long, random: Random): Optional<Transaction> {
        return Optional.empty()
    }
}

open class ManualTransactionGenerator : TransactionGenerator {
    protected val fifoQueue:Queue<Pair<ByteArray, Array<Dependency>>> = LinkedList()
    override fun next(instance: Mockchain, step: Long, random: Random): Optional<Transaction> {
        val (txContent, txDependencies) = fifoQueue.poll()
        return Optional.of(Transaction(txContent, *txDependencies))
    }
    fun addNextStep(txContent: ByteArray, txDependencies: Array<Dependency>) {
        fifoQueue.offer(Pair(txContent, txDependencies))
    }
}