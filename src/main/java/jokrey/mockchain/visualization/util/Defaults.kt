package jokrey.mockchain.visualization.util

import jokrey.mockchain.Mockchain
import jokrey.mockchain.application.Application
import jokrey.mockchain.squash.BuildUponSquashHandler
import jokrey.mockchain.squash.PartialReplaceSquashHandler
import jokrey.mockchain.squash.SequenceSquashHandler
import jokrey.mockchain.storage_classes.Block
import jokrey.mockchain.storage_classes.RejectionReason
import jokrey.mockchain.storage_classes.Transaction
import jokrey.mockchain.storage_classes.TransactionHash
import java.util.*

/**
 *
 * @author jokrey
 */


open class EmptyApplication : Application {
    override fun preMemPoolVerify(instance: Mockchain, tx: Transaction): RejectionReason.APP_VERIFY? = null //accept all
    override fun newTxInMemPool(instance: Mockchain, tx: Transaction) {}
    override fun blockVerify(instance: Mockchain, blockCreatorIdentity:ByteArray, vararg txs: Transaction): List<Pair<Transaction, RejectionReason.APP_VERIFY>> = emptyList()
    override fun newBlock(instance: Mockchain, block: Block, newTransactions: List<Transaction>) {}
    override fun txRemoved(instance: Mockchain, oldHash: TransactionHash, oldTx: Transaction, txWasPersisted: Boolean) {}
    override fun txAltered(instance: Mockchain, oldHash: TransactionHash, oldTx: Transaction, newHash: TransactionHash, newTx: Transaction, txWasPersisted: Boolean) {}
    override fun txRejected(instance: Mockchain, oldHash: TransactionHash, oldTx: Transaction, reason: RejectionReason) { }

//    override fun getBuildUponSquashHandler(): BuildUponSquashHandler = {list, b -> list.flatMap { (it + b).asIterable() }.toByteArray() }
//    override fun getPartialReplaceSquashHandler(): PartialReplaceSquashHandler = { latestContent, _ -> latestContent.copyOf(latestContent.size-1) }
//    override fun getSequenceSquashHandler(): SequenceSquashHandler = { list -> list.flatMap { (it + byteArrayOf(13)).asIterable() }.toByteArray() }
    override fun getBuildUponSquashHandler(): BuildUponSquashHandler = {list, b -> list.flatMap { (it + b).asIterable() }.toByteArray() }
    override fun getPartialReplaceSquashHandler(): PartialReplaceSquashHandler = { latestContent, _ -> latestContent.copyOf(latestContent.size-1) }
    override fun getSequenceSquashHandler(): SequenceSquashHandler = { list -> list.flatMap { (it + byteArrayOf(13)).asIterable() }.toByteArray() }
    override fun newEqualInstance() = EmptyApplication()
    override fun cleanUpAfterForkInvalidatedThisState() {} //NO NEED TO DO ANYTHING SINCE THE GC WILL TAKE CARE OF IT
}
fun contentIsArbitrary() = contentIsArbitrary(11)
fun contentIsArbitrary(maxSize: Int) = contentIsArbitrary(Random(), maxSize)
fun contentIsArbitrary(random: Random, maxSize: Int) = contentIsArbitrary(random, 1, maxSize)
fun contentIsArbitrary(random: Random, minSize: Int, maxSize: Int) : ByteArray {
    return ByteArray(random.nextInt(maxSize-minSize)+minSize) {
        random.nextInt().toByte()
    }
}