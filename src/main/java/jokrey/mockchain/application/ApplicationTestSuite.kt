package jokrey.mockchain.application

import jokrey.mockchain.Nockchain
import jokrey.mockchain.application.examples.calculator.*
import jokrey.mockchain.consensus.ManualConsensusAlgorithm
import jokrey.mockchain.consensus.ManualConsensusAlgorithmCreator
import jokrey.mockchain.storage_classes.*
import jokrey.utilities.network.link2peer.P2Link
import jokrey.utilities.simple.data_structure.queue.ConcurrentQueueTest
import java.lang.IllegalStateException
import java.util.*

class ApplicationTestSuite {
    fun runAllTests(app: TestableApp) {
        runForkAcceptabilityTest(app)
    }

    fun runForkAcceptabilityTest(app: TestableApp) {
        forkTestWithoutSquash(app, sameInitial = false, catchup = false)
        forkTestWithoutSquash(app, sameInitial = false, catchup = true )
        forkTestWithoutSquash(app, sameInitial = true,  catchup = false)
        forkTestWithoutSquash(app, sameInitial = true,  catchup = true )
    }




    private fun forkTestWithoutSquash(app: TestableApp, sameInitial: Boolean, catchup: Boolean) {
        val nodeLink1 = P2Link.from("localhost:40001")
        val chain1 = Nockchain(
            app.newEqualInstance(), nodeLink1,
            store = NonPersistentStorage(), consensus = ManualConsensusAlgorithmCreator()
        )

        val nodeLink2 = P2Link.from("localhost:40002")
        val chain2 = Nockchain(
            app.newEqualInstance(), nodeLink2,
            store = NonPersistentStorage(), consensus = ManualConsensusAlgorithmCreator()
        )

        try {
            val tx11 = (chain1.app as TestableApp).next(chain1, 0, Random(0)).get()
            val tx12 = (chain1.app as TestableApp).next(chain1, 1, Random(0)).get()
            val tx13 = (chain1.app as TestableApp).next(chain1, 2, Random(0)).get()

            val tx21 = (chain2.app as TestableApp).next(chain2, 0, Random(0)).get()
            val tx22 = (chain2.app as TestableApp).next(chain2, 1, Random(0)).get()

            chain1.consensus as ManualConsensusAlgorithm
            chain2.consensus as ManualConsensusAlgorithm

            chain1.commitToMemPool(tx11)
            chain1.consensus.performConsensusRound(false)
            chain1.commitToMemPool(tx12)
            chain1.consensus.performConsensusRound(false)
            chain1.commitToMemPool(tx13)
            chain1.consensus.performConsensusRound(false)

            chain2.commitToMemPool(tx21)
            chain2.consensus.performConsensusRound(false)

            if(catchup) {
                chain2.commitToMemPool(tx22)
                chain2.consensus.performConsensusRound(false)
            }

            val successConnectAndCatchup = chain2.connect(chain1.selfLink, catchup = true)
            assertTrue(successConnectAndCatchup)


            ConcurrentQueueTest.sleep(1000) // wait for fork to complete

            assertEquals(2.0 * 3.0, (chain2.app as SingleStringCalculator).getResults()[0])
        } finally {
            chain1.close()
            chain2.close()
        }
    }


}

fun assertTrue(c: Boolean) {
    if(!c) throw IllegalStateException("assertion failed != true")
}
fun <T>assertEquals(t1: T, t2: T) {
    if(t1 != t2) throw IllegalStateException("assertion failed t1($t1) != t2($t2)")
}