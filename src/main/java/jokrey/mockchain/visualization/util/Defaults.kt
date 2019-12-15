package jokrey.mockchain.visualization.util

import jokrey.mockchain.application.Application
import jokrey.mockchain.squash.BuildUponSquashHandler
import jokrey.mockchain.squash.PartialReplaceSquashHandler
import jokrey.mockchain.squash.SequenceSquashHandler
import jokrey.mockchain.storage_classes.*
import java.util.*

/**
 *
 * @author jokrey
 */


open class EmptyApplication : Application {
    override fun verify(chain: Chain, vararg txs: Transaction): List<Pair<Transaction, RejectionReason.APP_VERIFY>> = emptyList()
    override fun newBlock(chain: Chain, block: Block) {}
    override fun txRemoved(chain: Chain, oldHash: TransactionHash, oldTx: Transaction, txWasPersisted: Boolean) {}
    override fun txAltered(chain: Chain, oldHash: TransactionHash, oldTx: Transaction, newHash: TransactionHash, newTx: Transaction, txWasPersisted: Boolean) {}
    override fun txRejected(chain: Chain, oldHash: TransactionHash, oldTx: Transaction, reason: RejectionReason) { }

    //    override fun getBuildUponSquashHandler(): BuildUponSquashHandler = {list, b -> list.flatMap { (it + b).asIterable() }.toByteArray() }
//    override fun getPartialReplaceSquashHandler(): PartialReplaceSquashHandler = { latestContent, _ -> latestContent.copyOf(latestContent.size-1) }
//    override fun getSequenceSquashHandler(): SequenceSquashHandler = { list -> list.flatMap { (it + byteArrayOf(13)).asIterable() }.toByteArray() }
    override fun getBuildUponSquashHandler(): BuildUponSquashHandler = {list, b -> list.flatMap { (it + b).asIterable() }.toByteArray() }
    override fun getPartialReplaceSquashHandler(): PartialReplaceSquashHandler = { latestContent, _ -> latestContent.copyOf(latestContent.size-1) }
    override fun getSequenceSquashHandler(): SequenceSquashHandler = { list -> list.flatMap { (it + byteArrayOf(13)).asIterable() }.toByteArray() }
}
fun contentIsArbitrary() = contentIsArbitrary(11)
fun contentIsArbitrary(maxSize: Int) = contentIsArbitrary(Random(), maxSize)
fun contentIsArbitrary(random: Random, maxSize: Int) = contentIsArbitrary(random, 1, maxSize)
fun contentIsArbitrary(random: Random, minSize: Int, maxSize: Int) : ByteArray {
    return ByteArray(random.nextInt(maxSize-minSize)+minSize) {
        random.nextInt().toByte()
    }
}