import jokrey.mockchain.Mockchain
import jokrey.mockchain.Nockchain
import jokrey.mockchain.consensus.ManualConsensusAlgorithm
import jokrey.mockchain.consensus.ManualConsensusAlgorithmCreator
import jokrey.mockchain.network.findForkIndex
import jokrey.mockchain.squash.BuildUponSquashHandler
import jokrey.mockchain.squash.PartialReplaceSquashHandler
import jokrey.mockchain.storage_classes.*
import jokrey.mockchain.visualization.util.EmptyApplication
import jokrey.utilities.network.link2peer.P2Link
import jokrey.utilities.simple.data_structure.stack.ConcurrentStackTest.sleep
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 *
 * @author jokrey
 */

class DistributedTests {
    @Test
    fun simpleTest3Nodes() {
        val instance1 = Nockchain(EmptyApplication(), P2Link.createPublicLink("localhost", 43221), consensus = ManualConsensusAlgorithmCreator())
        val instance2 = Nockchain(EmptyApplication(), P2Link.createPublicLink("localhost", 43222), consensus = ManualConsensusAlgorithmCreator())
        val instance3 = Nockchain(EmptyApplication(), P2Link.createPublicLink("localhost", 43223), consensus = ManualConsensusAlgorithmCreator())
        instance1.consensus as ManualConsensusAlgorithm
        instance2.consensus as ManualConsensusAlgorithm
        instance3.consensus as ManualConsensusAlgorithm

        instance1.connect(instance2.selfLink)
        instance2.connect(instance3.selfLink)

        val tx0 = Transaction(byteArrayOf(0))
        val tx1 = Transaction(byteArrayOf(1))
        val tx2 = Transaction(byteArrayOf(2))

        instance2.commitToMemPool(tx0)
        instance1.commitToMemPool(tx1)
        instance2.commitToMemPool(tx2)

        sleep(1000) //ensure the synchronization is completed between the peers - otherwise is basically fine too, because they would then request the tx

        instance1.consensus.performConsensusRound(false)

        sleep(1000) //ensure the synchronization is completed between the peers - otherwise is basically fine too, because they would then request the tx

        helper_assertEquals(instance1.chain.getPersistedTransactions().asSequence(), tx0, tx1, tx2)
        helper_assertEquals(instance2.chain.getPersistedTransactions().asSequence(), tx0, tx1, tx2)
        helper_assertEquals(instance3.chain.getPersistedTransactions().asSequence(), tx0, tx1, tx2)
    }

    @Test
    fun distributedWithSquash() {
        val appCreator = {
            object : EmptyApplication() {
                override fun getPartialReplaceSquashHandler(): PartialReplaceSquashHandler {
                    return { latestContent, _ -> latestContent.copyOf(latestContent.size-1) }
                }
                override fun getBuildUponSquashHandler(): BuildUponSquashHandler {
                    return {list, b -> list[0] + b }
                }
            }
        }

        val instance1 = Nockchain(appCreator(), P2Link.createPublicLink("localhost", 43224), consensus = ManualConsensusAlgorithmCreator())
        val instance2 = Nockchain(appCreator(), P2Link.createPublicLink("localhost", 43225), consensus = ManualConsensusAlgorithmCreator())
        val instance3 = Nockchain(appCreator(), P2Link.createPublicLink("localhost", 43226), consensus = ManualConsensusAlgorithmCreator())
        instance1.consensus as ManualConsensusAlgorithm
        instance2.consensus as ManualConsensusAlgorithm
        instance3.consensus as ManualConsensusAlgorithm

        instance1.connect(instance2.selfLink)
        instance2.connect(instance3.selfLink)

        val tx0 = Transaction(byteArrayOf(1,11,0))
        val tx1 = Transaction(byteArrayOf(2,22,1), Dependency(tx0, DependencyType.BUILDS_UPON))
        val tx2 = Transaction(byteArrayOf(3,33,2), Dependency(tx1, DependencyType.REPLACES_PARTIAL))
        val tx3 = Transaction(byteArrayOf(4,44,3), Dependency(tx2, DependencyType.BUILDS_UPON))

        instance2.commitToMemPool(tx0)
        instance1.commitToMemPool(tx1)
        instance2.commitToMemPool(tx2)
        instance3.commitToMemPool(tx3)

        sleep(1000) //ensure the synchronization is completed between the peers - otherwise is basically fine too, because they would then request the tx

        instance1.consensus.performConsensusRound(true)

        sleep(1000) //ensure the synchronization is completed between the peers - otherwise is basically fine too, because they would then request the tx
        //i.e.
//        tx0 -> [1,11,0]
//        tx1 -> [1,11,0, 2,22,1] -> [1,11,0, 2,22,]
//        tx2 -> [3,33,2]
//        tx3 -> [3,33,2, 4,44,3]

        println("instance1.chain.getPersistedTransactions().asSequence().toList() = ${instance1.chain.getPersistedTransactions().asSequence().toList()}")
        println("instance2.chain.getPersistedTransactions().asSequence().toList() = ${instance2.chain.getPersistedTransactions().asSequence().toList()}")
        println("instance3.chain.getPersistedTransactions().asSequence().toList() = ${instance3.chain.getPersistedTransactions().asSequence().toList()}")

        assertEquals(instance1.chain.getPersistedTransactions().asSequence().toList(),
                instance2.chain.getPersistedTransactions().asSequence().toList())
        assertEquals(instance1.chain.getPersistedTransactions().asSequence().toList(),
                instance3.chain.getPersistedTransactions().asSequence().toList())
//        helper_assertEquals(instance1.chain.getPersistedTransactions().asSequence(), tx0, tx1, tx2)
//        helper_assertEquals(instance2.chain.getPersistedTransactions().asSequence(), tx0, tx1, tx2)
//        helper_assertEquals(instance3.chain.getPersistedTransactions().asSequence(), tx0, tx1, tx2)
    }

    @Test
    fun testNodeQueriesUnknownTxs() {
        val instance1 = Nockchain(EmptyApplication(), P2Link.createPublicLink("localhost", 44221), consensus = ManualConsensusAlgorithmCreator())
        val instance2 = Nockchain(EmptyApplication(), P2Link.createPublicLink("localhost", 44222), consensus = ManualConsensusAlgorithmCreator())
        instance1.consensus as ManualConsensusAlgorithm
        instance2.consensus as ManualConsensusAlgorithm

        val tx0 = Transaction(byteArrayOf(0))
        val tx1 = Transaction(byteArrayOf(1))
        val tx2 = Transaction(byteArrayOf(2))

        instance1.commitToMemPool(tx0)
        instance1.commitToMemPool(tx1)
        instance1.commitToMemPool(tx2)

        instance1.connect(instance2.selfLink, catchup = false)

        instance1.consensus.performConsensusRound(false)

        sleep(1000)

        helper_assertEquals(instance1.chain.getPersistedTransactions().asSequence(), tx0, tx1, tx2)
        helper_assertEquals(instance2.chain.getPersistedTransactions().asSequence(), tx0, tx1, tx2)
    }






    @Test
    fun testForkPointFinder() {
        val blocks = ArrayList<Block>(10)
        for(i in 0 until 10)
            blocks.add(Block(if(i==0) null else blocks[i-1].getHeaderHash(), Proof(ByteArray(1)), emptyArray()))

        run {
            val instance1 = Mockchain(EmptyApplication())
            val instance2 = Mockchain(EmptyApplication())

            putBlocks(instance1, blocks, 5)
            putBlocks(instance2, blocks)

            val forkPoint = findForkIndex(instance1.chain, instance1.chain.blockCount(), instance2.chain.getBlocks().map { it.getHeaderHash() }, 0, instance2.chain.blockCount())
            assertForkIndexCorrect(4, forkPoint, instance1, instance2)
        }

        run {
            val instance1 = Mockchain(EmptyApplication())
            val instance2 = Mockchain(EmptyApplication())

            putBlocks(instance1, blocks)
            putBlocks(instance2, blocks)

            val forkIndex = findForkIndex(instance1.chain, instance1.chain.blockCount(), instance2.chain.getBlocks().map { it.getHeaderHash() }, 0, instance2.chain.blockCount())
            assertForkIndexCorrect(blocks.size-1, forkIndex, instance1, instance2)
        }

        run {
            val instance1 = Mockchain(EmptyApplication())
            val instance2 = Mockchain(EmptyApplication())

            putBlocks(instance1, blocks, 5)
            for(i in 0..2)
                instance1.chain.store.add(Block(instance1.chain.getLatestHash(), Proof(ByteArray(0)), arrayOf(TransactionHash(ByteArray(12) {it.toByte()}))))
            putBlocks(instance2, blocks)

            val forkIndex = findForkIndex(instance1.chain, instance1.chain.blockCount(), instance2.chain.getBlocks().map { it.getHeaderHash() }, 0, instance2.chain.blockCount())
            assertForkIndexCorrect(4, forkIndex, instance1, instance2)
        }
    }

    private fun putBlocks(instance: Mockchain, blocks: List<Block>, stopWithSize: Int = (blocks.size)) {
        for(i in 0 until stopWithSize) instance.chain.store.add(blocks[i])
        instance.chain.store.commit()
    }

    private fun assertForkIndexCorrect(expectedForkIndex: Int, forkPointIn1: Int, instance1: Mockchain, instance2: Mockchain) {
        assertEquals(expectedForkIndex, forkPointIn1)

        val hashes1 = instance1.chain.getBlocks().map { it.getHeaderHash() }
        val hashes2 = instance2.chain.getBlocks().map { it.getHeaderHash() }

        assertEquals(hashes1[forkPointIn1], hashes2[forkPointIn1])
        assertTrue(forkPointIn1 == hashes1.size-1 || hashes1[forkPointIn1+1] != hashes2[forkPointIn1])
    }
}


fun helper_assertEquals(txs: Sequence<Transaction>, vararg given: Transaction) {
    val list = txs.toList()
    assertEquals(given.size, list.size)
    for(g in given)
        assertTrue(list.contains(g))
}