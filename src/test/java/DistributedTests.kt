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
import jokrey.utilities.network.link2peer.node.DebugStats
import jokrey.utilities.simple.data_structure.stack.ConcurrentStackTest.sleep
import org.junit.jupiter.api.Test
import java.util.concurrent.ThreadLocalRandom
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 *
 * @author jokrey
 */

class DistributedTests {
    @Test
    fun simpleTest3Nodes() {
        val instance1 = Nockchain(EmptyApplication(), P2Link.Local.forTest(43221).unsafeAsDirect(), consensus = ManualConsensusAlgorithmCreator())
        val instance2 = Nockchain(EmptyApplication(), P2Link.Local.forTest(43222).unsafeAsDirect(), consensus = ManualConsensusAlgorithmCreator())
        val instance3 = Nockchain(EmptyApplication(), P2Link.Local.forTest(43223).unsafeAsDirect(), consensus = ManualConsensusAlgorithmCreator())

        instance1.connect(instance2.selfLink)
        instance2.connect(instance3.selfLink)

        val tx0 = Transaction(byteArrayOf(0))
        val tx1 = Transaction(byteArrayOf(1))
        val tx2 = Transaction(byteArrayOf(2))

        instance2.commitToMemPool(tx0)
        instance1.commitToMemPool(tx1)
        instance2.commitToMemPool(tx2)

        sleep(1000) //ensure the synchronization is completed between the peers - otherwise is basically fine too, because they would then request the tx

        (instance1.consensus as ManualConsensusAlgorithm).performConsensusRound(false)

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

        val instance1 = Nockchain(appCreator(), P2Link.Local.forTest(43224).unsafeAsDirect(), consensus = ManualConsensusAlgorithmCreator())
        val instance2 = Nockchain(appCreator(), P2Link.Local.forTest(43225).unsafeAsDirect(), consensus = ManualConsensusAlgorithmCreator())
        val instance3 = Nockchain(appCreator(), P2Link.Local.forTest(43226).unsafeAsDirect(), consensus = ManualConsensusAlgorithmCreator())

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

        (instance1.consensus as ManualConsensusAlgorithm).performConsensusRound(true)

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
        val instance1 = Nockchain(EmptyApplication(), P2Link.Local.forTest(44221).unsafeAsDirect(), consensus = ManualConsensusAlgorithmCreator())
        val instance2 = Nockchain(EmptyApplication(), P2Link.Local.forTest(44222).unsafeAsDirect(), consensus = ManualConsensusAlgorithmCreator())

        val tx0 = Transaction(byteArrayOf(0))
        val tx1 = Transaction(byteArrayOf(1))
        val tx2 = Transaction(byteArrayOf(2))

        instance1.commitToMemPool(tx0)
        instance1.commitToMemPool(tx1)
        instance1.commitToMemPool(tx2)

        instance1.connect(instance2.selfLink, catchup = false)

        (instance1.consensus as ManualConsensusAlgorithm).performConsensusRound(false)

        sleep(1000)

        helper_assertEquals(instance1.chain.getPersistedTransactions().asSequence(), tx0, tx1, tx2)
        helper_assertEquals(instance2.chain.getPersistedTransactions().asSequence(), tx0, tx1, tx2)
    }



    @Test
    fun forkTestSimple1() {
        val instance1 = Nockchain(EmptyApplication(), P2Link.Local.forTest(44223).unsafeAsDirect(), consensus = ManualConsensusAlgorithmCreator())
        val instance2 = Nockchain(EmptyApplication(), P2Link.Local.forTest(44224).unsafeAsDirect(), consensus = ManualConsensusAlgorithmCreator())

        instance1.connect(instance2.selfLink, catchup = false)

        val tx0 = Transaction(byteArrayOf(0))
        val tx1 = Transaction(byteArrayOf(1))
        val tx2 = Transaction(byteArrayOf(2))
        val tx3 = Transaction(byteArrayOf(3))
        val tx4 = Transaction(byteArrayOf(4))
        val tx4_oo1 = Transaction(byteArrayOf(44))
        val tx5 = Transaction(byteArrayOf(5))

        instance1.commitToMemPool(tx0)
        sleep(100)
        (instance1.consensus as ManualConsensusAlgorithm).performConsensusRound(false)
        sleep(100)
        instance1.commitToMemPool(tx1)
        sleep(100)
        (instance2.consensus as ManualConsensusAlgorithm).performConsensusRound(false)
        sleep(100)
        instance2.commitToMemPool(tx2)
        sleep(100)
        (instance1.consensus as ManualConsensusAlgorithm).performConsensusRound(false)
        sleep(100)

        helper_assertEquals(instance1.chain.getPersistedTransactions().asSequence(), tx0, tx1, tx2)
        helper_assertEquals(instance2.chain.getPersistedTransactions().asSequence(), tx0, tx1, tx2)

        instance1.node.p2lNode.disconnectFromAll()

        sleep(1000)

        instance1.commitToMemPool(tx3)
        (instance1.consensus as ManualConsensusAlgorithm).performConsensusRound(false)

        instance2.commitToMemPool(tx3)
        (instance2.consensus as ManualConsensusAlgorithm).performConsensusRound(false)
        instance2.commitToMemPool(tx4_oo1)
        (instance2.consensus as ManualConsensusAlgorithm).performConsensusRound(false)

        instance1.commitToMemPool(tx4)
        (instance1.consensus as ManualConsensusAlgorithm).performConsensusRound(false)

        instance1.connect(instance2.selfLink, catchup = false)

        instance1.commitToMemPool(tx5)
        (instance1.consensus as ManualConsensusAlgorithm).performConsensusRound(false)

        sleep(1000)

        helper_assertEquals(instance1.chain.getPersistedTransactions().asSequence(), tx0, tx1, tx2, tx3, tx4, tx5)
        helper_assertEquals(instance2.chain.getPersistedTransactions().asSequence(), tx0, tx1, tx2, tx3, tx4, tx5)
    }

    @Test
    fun forkTestSimple2_manyTx() {
        val instance1 = Nockchain(EmptyApplication(), P2Link.Local.forTest(44225).unsafeAsDirect(), consensus = ManualConsensusAlgorithmCreator())
        val instance2 = Nockchain(EmptyApplication(), P2Link.Local.forTest(44226).unsafeAsDirect(), consensus = ManualConsensusAlgorithmCreator())

        instance1.connect(instance2.selfLink, catchup = false)

        for(i in 0 until 500) {
            instance1.commitToMemPool(Transaction(byteArrayOf(0) + rand(100)))
            sleep(10)
        }
        sleep(1000)
        (instance1.consensus as ManualConsensusAlgorithm).performConsensusRound(false)
        //      there are array exceptions in the long part receivers
        sleep(1000)
        for(i in 500 until 1000) {
            instance1.commitToMemPool(Transaction(byteArrayOf(1) + rand(100)))
            sleep(10)
        }
        sleep(1000)
        (instance2.consensus as ManualConsensusAlgorithm).performConsensusRound(false)
        sleep(1000)
        for(i in 1000 until 1500) {
            instance2.commitToMemPool(Transaction(byteArrayOf(2) + rand(100)))
            sleep(10)
        }
        sleep(1000)
        (instance1.consensus as ManualConsensusAlgorithm).performConsensusRound(false)
        sleep(10000)

        helper_assertEquals(instance1.chain.getPersistedTransactions().asSequence(), *instance2.chain.getPersistedTransactions().asSequence().toList().toTypedArray())

        instance1.node.p2lNode.disconnectFromAll()

        sleep(1000)

        for(i in 1500 until 2000) instance1.commitToMemPool(Transaction(byteArrayOf(3) + rand(1000)))
        (instance1.consensus as ManualConsensusAlgorithm).performConsensusRound(false)

        for(i in 1500 until 2000) instance2.commitToMemPool(Transaction(byteArrayOf(3) + rand(1000)))
        (instance2.consensus as ManualConsensusAlgorithm).performConsensusRound(false)
        for(i in 2000 until 2500) instance2.commitToMemPool(Transaction(byteArrayOf(44) + rand(1000)))
        (instance2.consensus as ManualConsensusAlgorithm).performConsensusRound(false)

        for(i in 2000 until 2500) instance1.commitToMemPool(Transaction(byteArrayOf(4) + rand(1000)))
        (instance1.consensus as ManualConsensusAlgorithm).performConsensusRound(false)

        instance1.connect(instance2.selfLink, catchup = false)

        for(i in 2500 until 3000) instance1.commitToMemPool(Transaction(byteArrayOf(5) + rand(1000)))
        (instance1.consensus as ManualConsensusAlgorithm).performConsensusRound(false)

        sleep(1000)

        instance1.commitToMemPool(Transaction(byteArrayOf(6)))
        (instance1.consensus as ManualConsensusAlgorithm).performConsensusRound(false)

        sleep(10000)

        helper_assertEquals(instance2.chain.getPersistedTransactions().asSequence(), *instance1.chain.getPersistedTransactions().asSequence().toList().toTypedArray())
    }

    @Test
    fun forkTestSimple2_manyBlocks() {
        val instance1 = Nockchain(EmptyApplication(), P2Link.Local.forTest(44227).unsafeAsDirect(), consensus = ManualConsensusAlgorithmCreator())
        val instance2 = Nockchain(EmptyApplication(), P2Link.Local.forTest(44228).unsafeAsDirect(), consensus = ManualConsensusAlgorithmCreator())

        instance1.connect(instance2.selfLink, catchup = false)

        for(i in 0 until 500) {
            instance1.commitToMemPool(Transaction(byteArrayOf(0, i.toByte()) + rand(1000)))
            (instance1.consensus as ManualConsensusAlgorithm).performConsensusRound(false)
            sleep(100)
        }
        sleep(1000)
        for(i in 500 until 1000) {
            instance1.commitToMemPool(Transaction(byteArrayOf(1, i.toByte()) + rand(1000)))
            sleep(100)
            (instance2.consensus as ManualConsensusAlgorithm).performConsensusRound(false)
        }
        sleep(1000)
        for(i in 1000 until 1500) {
            instance2.commitToMemPool(Transaction(byteArrayOf(2, i.toByte()) + rand(1000)))
            sleep(100)
            (instance1.consensus as ManualConsensusAlgorithm).performConsensusRound(false)
        }
        sleep(10000)

        helper_assertEquals(instance1.chain.getPersistedTransactions().asSequence(), *instance2.chain.getPersistedTransactions().asSequence().toList().toTypedArray())

        instance1.node.p2lNode.disconnectFromAll()

        sleep(1000)

        for(i in 1500 until 1600) {
            instance1.commitToMemPool(Transaction(byteArrayOf(3, i.toByte(), (i*13).toByte())))
            (instance1.consensus as ManualConsensusAlgorithm).performConsensusRound(false)
        }

        for(i in 1500 until 1600) {
            instance2.commitToMemPool(Transaction(byteArrayOf(3, i.toByte(), (i*13).toByte())))
            (instance2.consensus as ManualConsensusAlgorithm).performConsensusRound(false)
        }
        for(i in 1600 until 2000) {
            instance2.commitToMemPool(Transaction(byteArrayOf(44, i.toByte()) + rand(1000)))
            (instance2.consensus as ManualConsensusAlgorithm).performConsensusRound(false)
        }

        for(i in 1600 until 2500) {
            instance1.commitToMemPool(Transaction(byteArrayOf(4, i.toByte()) + rand(1000)))
            (instance1.consensus as ManualConsensusAlgorithm).performConsensusRound(false)
        }

        instance1.connect(instance2.selfLink, catchup = false)

        println("echelon after connect")

        for(i in 2500 until 2510) {
            instance1.commitToMemPool(Transaction(byteArrayOf(5, i.toByte()) + rand(1000)))
            (instance1.consensus as ManualConsensusAlgorithm).performConsensusRound(false) //after first consensus round the synchronization has occurred
            sleep(10000) //we need to wait a while here... Otherwise the synchronization may not have finished - due to missing pause functionality
        }
//        for(i in 2510 until 2520) {
//            instance2.commitToMemPool(Transaction(byteArrayOf(6, i.toByte()) + rand(1000)))
//            (instance2.consensus as ManualConsensusAlgorithm).performConsensusRound(false)
//            sleep(100)
//        }

        sleep(1000)

        helper_assertEquals(instance1.chain.getPersistedTransactions().asSequence(), *instance2.chain.getPersistedTransactions().asSequence().toList().toTypedArray())
    }


    @Test
    fun forkTestSimple_nextBlockRightAway_pauseTest() {
        val instance1 = Nockchain(EmptyApplication(), P2Link.Local.forTest(44230).unsafeAsDirect(), consensus = ManualConsensusAlgorithmCreator())
        val instance2 = Nockchain(EmptyApplication(), P2Link.Local.forTest(44231).unsafeAsDirect(), consensus = ManualConsensusAlgorithmCreator())

        instance1.connect(instance2.selfLink, catchup = false)

        val tx0 = Transaction(byteArrayOf(0))
        val tx1 = Transaction(byteArrayOf(1))
        val tx2 = Transaction(byteArrayOf(2))
        val tx3 = Transaction(byteArrayOf(3))
        val tx4 = Transaction(byteArrayOf(4))
        val tx4_oo1 = Transaction(byteArrayOf(44))
        val tx5 = Transaction(byteArrayOf(5))
        val tx6 = Transaction(byteArrayOf(6))
        val tx7 = Transaction(byteArrayOf(7))

        instance1.commitToMemPool(tx0)
        sleep(100)
        (instance1.consensus as ManualConsensusAlgorithm).performConsensusRound(false)
        sleep(100)
        instance1.commitToMemPool(tx1)
        sleep(100)
        (instance2.consensus as ManualConsensusAlgorithm).performConsensusRound(false)
        sleep(100)
        instance2.commitToMemPool(tx2)
        sleep(100)
        (instance1.consensus as ManualConsensusAlgorithm).performConsensusRound(false)
        sleep(100)

        helper_assertEquals(instance1.chain.getPersistedTransactions().asSequence(), tx0, tx1, tx2)
        helper_assertEquals(instance2.chain.getPersistedTransactions().asSequence(), tx0, tx1, tx2)

        instance1.node.p2lNode.disconnectFromAll()

        sleep(1000)

        instance1.commitToMemPool(tx3)
        (instance1.consensus as ManualConsensusAlgorithm).performConsensusRound(false)

        instance2.commitToMemPool(tx3)
        (instance2.consensus as ManualConsensusAlgorithm).performConsensusRound(false)
        instance2.commitToMemPool(tx4_oo1)
        (instance2.consensus as ManualConsensusAlgorithm).performConsensusRound(false)

        instance1.commitToMemPool(tx4)
        (instance1.consensus as ManualConsensusAlgorithm).performConsensusRound(false)
        for(i in 0 until 500) {
            for(j in 0 until 5)
                instance1.commitToMemPool(Transaction(rand(2000)))
            (instance1.consensus as ManualConsensusAlgorithm).performConsensusRound(false)
        }

        instance1.connect(instance2.selfLink, catchup = false)

        instance1.commitToMemPool(Transaction(rand(2000)))
        (instance1.consensus as ManualConsensusAlgorithm).performConsensusRound(false)
        instance1.commitToMemPool(tx6) //without the pause-and-record feature this transaction and block falls right into the fork operation and either fucks everything up or is simply rejected(I think it is the latter)
        (instance1.consensus as ManualConsensusAlgorithm).performConsensusRound(false)
        for(i in 0 until 5)
            instance1.commitToMemPool(Transaction(rand(2000))) //without the pause-and-record feature this transaction and block falls right into the fork operation and either fucks everything up or is simply rejected(I think it is the latter)
        (instance1.consensus as ManualConsensusAlgorithm).performConsensusRound(false)
        for(i in 0 until 5)
            instance1.commitToMemPool(Transaction(rand(2000))) //without the pause-and-record feature this transaction and block falls right into the fork operation and either fucks everything up or is simply rejected(I think it is the latter)
        (instance1.consensus as ManualConsensusAlgorithm).performConsensusRound(false)

        sleep(1000)

        println("instance1.chain.getBlocks() = ${instance1.chain.getBlocks().toList()}")
        println("instance2.chain.getBlocks() = ${instance2.chain.getBlocks().toList()}")

        instance1.commitToMemPool(tx7)
        (instance1.consensus as ManualConsensusAlgorithm).performConsensusRound(false)
        sleep(10000)

        helper_assertEquals(instance2.chain.getPersistedTransactions().asSequence(), *instance1.chain.getPersistedTransactions().asSequence().toList().toTypedArray())
//        helper_assertEquals(instance1.chain.getPersistedTransactions().asSequence(), tx0, tx1, tx2, tx3, tx4, tx5, tx6, tx7)
//        helper_assertEquals(instance2.chain.getPersistedTransactions().asSequence(), tx0, tx1, tx2, tx3, tx4, tx5, tx6, tx7)
    }

    @Test
    fun forkTestSimple_concurrentToPerform_pauseTest() {
        val instance1 = Nockchain(EmptyApplication(), P2Link.Local.forTest(44251).unsafeAsDirect(), consensus = ManualConsensusAlgorithmCreator())
        val instance2 = Nockchain(EmptyApplication(), P2Link.Local.forTest(44252).unsafeAsDirect(), consensus = ManualConsensusAlgorithmCreator())
        val instance3 = Nockchain(EmptyApplication(), P2Link.Local.forTest(44253).unsafeAsDirect(), consensus = ManualConsensusAlgorithmCreator())

        instance1.connect(instance2.selfLink, catchup = false)
        instance1.connect(instance3.selfLink, catchup = false)

        val tx0 = Transaction(byteArrayOf(0))
        val tx1 = Transaction(byteArrayOf(1))
        val tx2 = Transaction(byteArrayOf(2))
        val tx3 = Transaction(byteArrayOf(3))
        val tx4 = Transaction(byteArrayOf(4))
        val tx4_oo1 = Transaction(byteArrayOf(44))
        val tx5 = Transaction(byteArrayOf(5))
        val tx6 = Transaction(byteArrayOf(6))
        val tx7 = Transaction(byteArrayOf(7))

        instance1.commitToMemPool(tx0)
        sleep(100)
        (instance1.consensus as ManualConsensusAlgorithm).performConsensusRound(false)
        sleep(100)
        instance1.commitToMemPool(tx1)
        sleep(100)
        (instance2.consensus as ManualConsensusAlgorithm).performConsensusRound(false)
        sleep(100)
        instance2.commitToMemPool(tx2)
        sleep(100)
        (instance1.consensus as ManualConsensusAlgorithm).performConsensusRound(false)
        sleep(100)

        helper_assertEquals(instance1.chain.getPersistedTransactions().asSequence(), tx0, tx1, tx2)
        helper_assertEquals(instance2.chain.getPersistedTransactions().asSequence(), tx0, tx1, tx2)

        instance1.node.p2lNode.disconnectFrom(instance2.selfLink)

        sleep(1000)

        instance1.commitToMemPool(tx3)
        (instance1.consensus as ManualConsensusAlgorithm).performConsensusRound(false)

        instance2.commitToMemPool(tx3)
        (instance2.consensus as ManualConsensusAlgorithm).performConsensusRound(false)
        instance2.commitToMemPool(tx4_oo1)
        (instance2.consensus as ManualConsensusAlgorithm).performConsensusRound(false)

        instance1.commitToMemPool(tx4)
        (instance1.consensus as ManualConsensusAlgorithm).performConsensusRound(false)
        for(i in 0 until 10) {
            for(j in 0 until 1)
                instance1.commitToMemPool(Transaction(rand(2000)))
            (instance1.consensus as ManualConsensusAlgorithm).performConsensusRound(false)
        }

        instance1.connect(instance2.selfLink, catchup = false)

        Thread(Runnable {
            instance3.commitToMemPool(tx6) //without the pause-and-record feature this transaction and block falls right into the fork operation and either fucks everything up or is simply rejected(I think it is the latter)
            (instance3.consensus as ManualConsensusAlgorithm).performConsensusRound(false)
            for (i in 0 until 5)
                instance3.commitToMemPool(Transaction(rand(2000))) //without the pause-and-record feature this transaction and block falls right into the fork operation and either fucks everything up or is simply rejected(I think it is the latter)
            (instance3.consensus as ManualConsensusAlgorithm).performConsensusRound(false)
            for (i in 0 until 5)
                instance3.commitToMemPool(Transaction(rand(2000))) //without the pause-and-record feature this transaction and block falls right into the fork operation and either fucks everything up or is simply rejected(I think it is the latter)
            (instance3.consensus as ManualConsensusAlgorithm).performConsensusRound(false)
        }).start()

        for(j in 0 until 100)
            instance1.commitToMemPool(Transaction(rand(2000)))
        (instance1.consensus as ManualConsensusAlgorithm).performConsensusRound(false)


        sleep(5000)

        instance1.commitToMemPool(Transaction(rand(2000)))
        (instance1.consensus as ManualConsensusAlgorithm).performConsensusRound(false)

        sleep(5000)

        println("instance1.chain.getBlocks() = ${instance1.chain.getBlocks().toList()}")
        println("instance2.chain.getBlocks() = ${instance2.chain.getBlocks().toList()}")
        println("instance3.chain.getBlocks() = ${instance3.chain.getBlocks().toList()}")
//
//        instance1.commitToMemPool(tx7)
//        (instance1.consensus as ManualConsensusAlgorithm).performConsensusRound(false)
//        sleep(2000)

        helper_assertEquals(instance2.chain.getPersistedTransactions().asSequence(), *instance1.chain.getPersistedTransactions().asSequence().toList().toTypedArray())
        helper_assertEquals(instance3.chain.getPersistedTransactions().asSequence(), *instance1.chain.getPersistedTransactions().asSequence().toList().toTypedArray())
//        helper_assertEquals(instance1.chain.getPersistedTransactions().asSequence(), tx0, tx1, tx2, tx3, tx4, tx5, tx6, tx7)
//        helper_assertEquals(instance2.chain.getPersistedTransactions().asSequence(), tx0, tx1, tx2, tx3, tx4, tx5, tx6, tx7)
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

            println("forkPoint = ${forkPoint}")
            println("instance1.chain.getBlocks().map { it.getHeaderHash() } = ${instance1.chain.getBlocks().map { it.getHeaderHash() }}")
            println("instance2.chain.getBlocks().map { it.getHeaderHash() } = ${instance2.chain.getBlocks().map { it.getHeaderHash() }}")
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






    @Test
    fun catchUpTest() {
        val instance1 = Nockchain(EmptyApplication(), P2Link.Local.forTest(44232).unsafeAsDirect(), consensus = ManualConsensusAlgorithmCreator())
        val instance2 = Nockchain(EmptyApplication(), P2Link.Local.forTest(44233).unsafeAsDirect(), consensus = ManualConsensusAlgorithmCreator())

        val tx0 = Transaction(byteArrayOf(0))
        val tx1 = Transaction(byteArrayOf(1))
        val tx2 = Transaction(byteArrayOf(2))

        instance1.commitToMemPool(tx0)
        (instance1.consensus as ManualConsensusAlgorithm).performConsensusRound(false)
        instance2.commitToMemPool(tx0)
        (instance2.consensus as ManualConsensusAlgorithm).performConsensusRound(false)
        instance1.commitToMemPool(tx1)
        (instance1.consensus as ManualConsensusAlgorithm).performConsensusRound(false)
        instance1.commitToMemPool(tx2)
        (instance1.consensus as ManualConsensusAlgorithm).performConsensusRound(false)

        instance2.connect(instance1.selfLink, catchup = true) //order is important here.. instance2 is behind and it will ask instance1 to catch her up, but if instance1 were behind instance2 would not automatically catch 'em up

        DebugStats.print(true)

        helper_assertEquals(instance1.chain.getPersistedTransactions().asSequence(), tx0, tx1, tx2)
        helper_assertEquals(instance2.chain.getPersistedTransactions().asSequence(), tx0, tx1, tx2)
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

fun rand(size: Int): ByteArray {
    val b = ByteArray(size)
    ThreadLocalRandom.current().nextBytes(b)
    return b
}

fun helper_assertEquals(txs: Sequence<Transaction>, vararg given: Transaction) {
    val list = txs.toList()
    assertEquals(given.size, list.size)
    println("given.size = ${given.size}")
    for(g in given) {
        if(!list.contains(g))
            println("txs missing g = ${g}")
        assertTrue(list.contains(g))
    }
}