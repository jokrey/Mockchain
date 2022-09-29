import jokrey.mockchain.Mockchain
import jokrey.mockchain.consensus.ManualConsensusAlgorithm
import jokrey.mockchain.consensus.ManualConsensusAlgorithmCreator
import jokrey.mockchain.storage_classes.*
import jokrey.mockchain.visualization.util.EmptyApplication
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse

class TestMockchainRestartFromPersistent {
    @Test
    fun test() {
        val chain = Mockchain(
            EmptyApplication(),
            consensus = ManualConsensusAlgorithmCreator(),
            store = PersistentStorage(file = File("persistentRestartTest.ldb"), clean = true)
        )
        val tx1 = Transaction("Moin".toByteArray())
        chain.commitToMemPool(tx1)
        (chain.consensus as ManualConsensusAlgorithm).performConsensusRound(0)

        chain.close()

        var anyRejected = false
        val chainRestarted = Mockchain(
            object : EmptyApplication() {
                override fun txRejected(instance: Mockchain, oldHash: TransactionHash, oldTx: Transaction, reason: RejectionReason) {
                    anyRejected = true
                }
            },
            consensus = ManualConsensusAlgorithmCreator(),
            store = PersistentStorage(file = File("persistentRestartTest.ldb"), clean = false)
        )
        chainRestarted.commitToMemPool(Transaction("Depending".toByteArray(), Dependency(tx1.hash, DependencyType.BUILDS_UPON)))
        (chainRestarted.consensus as ManualConsensusAlgorithm).performConsensusRound(0)

        println("mem pool after: " + chainRestarted.memPool.getTransactionHashes().iterator().asSequence().toList())
        println("chain after: " + chainRestarted.chain.store.txIterator().asSequence().toList())
        chainRestarted.close()

        assertFalse(anyRejected)
    }
}