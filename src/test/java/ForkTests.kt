import jokrey.mockchain.Nockchain
import jokrey.mockchain.application.ApplicationTestSuite
import jokrey.mockchain.application.examples.calculator.*
import jokrey.mockchain.consensus.ManualConsensusAlgorithm
import jokrey.mockchain.consensus.ManualConsensusAlgorithmCreator
import jokrey.mockchain.storage_classes.*
import jokrey.utilities.network.link2peer.P2Link
import jokrey.utilities.simple.data_structure.queue.ConcurrentQueueTest.sleep
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ForkTests {
    @Test
    fun testSuiteForkTest() {
        ApplicationTestSuite().runForkAcceptabilityTest(SingleStringCalculator())
    }

    @Test
    fun fork0TextPersisted() {
        forkTestWithoutSquash(persistentStorage = true, sameInitial = false)
    }
    @Test
    fun fork0TextNonPersisted() {
        forkTestWithoutSquash(persistentStorage = false, sameInitial = false)
    }

    @Test
    fun fork1TextPersisted() {
        forkTestWithoutSquash(persistentStorage = true, sameInitial = true)
    }
    @Test
    fun fork1TextNonPersisted() {
        forkTestWithoutSquash(persistentStorage = false, sameInitial = true)
    }

    @Test
    fun catchupPersisted() {
        forkTestWithoutSquash(persistentStorage = true, sameInitial = true, noFork=true)
    }
    @Test
    fun catchupNonPersisted() {
        forkTestWithoutSquash(persistentStorage = false, sameInitial = true, noFork=true)
    }



    private fun forkTestWithoutSquash(persistentStorage: Boolean, sameInitial: Boolean, noFork: Boolean = false) {
        val nodeLink1 = P2Link.from("localhost:40001")
        val chain1 = Nockchain(
            SingleStringCalculator(), nodeLink1,
            store = if (persistentStorage) PersistentStorage(
                File("forkTest_chain1.ldb"),
                true
            ) else NonPersistentStorage(),
            consensus = ManualConsensusAlgorithmCreator()
        )

        val nodeLink2 = P2Link.from("localhost:40002")
        val chain2 = Nockchain(
            SingleStringCalculator(), nodeLink2,
            store = if (persistentStorage) PersistentStorage(
                File("forkTest_chain2.ldb"),
                true
            ) else NonPersistentStorage(),
            consensus = ManualConsensusAlgorithmCreator()
        )

        try {
            val chain1_consensus = chain1.consensus as ManualConsensusAlgorithm
            val chain2_consensus = chain2.consensus as ManualConsensusAlgorithm

            val tx11 = Transaction(Initial(if (sameInitial) 0.0 else 0.0).toTxContent())
            val tx12 = Transaction(
                Addition(2.0).toTxContent(),
                *dependenciesFrom(tx11.hash, DependencyType.BUILDS_UPON, DependencyType.REPLACES)
            )
            val tx13 = Transaction(
                Multiplication(3.0).toTxContent(),
                *dependenciesFrom(tx12.hash, DependencyType.BUILDS_UPON, DependencyType.REPLACES)
            )

            val tx21 = Transaction(Initial(if (sameInitial) 0.0 else 12.0).toTxContent())
            val tx22 = Transaction(
                Subtraction(3545.0).toTxContent(),
                *dependenciesFrom(tx21.hash, DependencyType.BUILDS_UPON, DependencyType.REPLACES)
            )


            chain1.commitToMemPool(tx11)
            chain1_consensus.performConsensusRound(0)
            chain1.commitToMemPool(tx12)
            chain1_consensus.performConsensusRound(0)
            chain1.commitToMemPool(tx13)
            chain1_consensus.performConsensusRound(0)

            assertEquals(2.0 * 3.0, (chain1.app as SingleStringCalculator).getResults()[0])

            chain2.commitToMemPool(tx21)
            chain2_consensus.performConsensusRound(0)

            if(noFork) {
                chain2.commitToMemPool(tx22)
                chain2_consensus.performConsensusRound(0)
            }

            val successConnectAndCatchup = chain2.connect(chain1.selfLink, catchup = true)
            assertTrue(successConnectAndCatchup)


            sleep(1000) // wait for fork to complete

            assertEquals(2.0 * 3.0, (chain2.app as SingleStringCalculator).getResults()[0])
        } finally {
            chain1.close()
            chain2.close()
        }
    }
}