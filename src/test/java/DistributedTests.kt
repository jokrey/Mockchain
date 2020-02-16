import jokrey.mockchain.Nockchain
import jokrey.mockchain.application.Application
import jokrey.mockchain.consensus.ManualConsensusAlgorithm
import jokrey.mockchain.squash.BuildUponSquashHandler
import jokrey.mockchain.squash.PartialReplaceSquashHandler
import jokrey.mockchain.storage_classes.Dependency
import jokrey.mockchain.storage_classes.DependencyType
import jokrey.mockchain.storage_classes.Transaction
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
        val instance1 = Nockchain(EmptyApplication(), P2Link.createPublicLink("localhost", 43221))
        val instance2 = Nockchain(EmptyApplication(), P2Link.createPublicLink("localhost", 43222))
        val instance3 = Nockchain(EmptyApplication(), P2Link.createPublicLink("localhost", 43223))
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

        val instance1 = Nockchain(appCreator(), P2Link.createPublicLink("localhost", 43224))
        val instance2 = Nockchain(appCreator(), P2Link.createPublicLink("localhost", 43225))
        val instance3 = Nockchain(appCreator(), P2Link.createPublicLink("localhost", 43226))
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
}


fun helper_assertEquals(txs: Sequence<Transaction>, vararg given: Transaction) {
    val list = txs.toList()
    assertEquals(given.size, list.size)
    for(g in given)
        assertTrue(list.contains(g))
}