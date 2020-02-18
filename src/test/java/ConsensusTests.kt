import jokrey.mockchain.Mockchain
import jokrey.mockchain.consensus.ConsensusAlgorithmCreator
import jokrey.mockchain.consensus.ProofOfStaticStakeConsensusCreator
import jokrey.mockchain.consensus.ProofOfWorkConsensus
import jokrey.mockchain.consensus.SimpleProofOfWorkConsensusCreator
import jokrey.mockchain.storage_classes.ImmutableByteArray
import jokrey.mockchain.storage_classes.Transaction
import jokrey.mockchain.visualization.util.EmptyApplication
import jokrey.mockchain.visualization.util.UserAuthHelper
import jokrey.utilities.bitsandbytes.BitHelper
import jokrey.utilities.simple.data_structure.queue.ConcurrentQueueTest.sleep
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 *
 * @author jokrey
 */
class ConsensusTests {
    @Test
    fun powTest() {
        val instance = Mockchain(EmptyApplication(), consensus = SimpleProofOfWorkConsensusCreator(1, ImmutableByteArray(ByteArray(12))))

        for (i in 1..100) {
            val cont = ByteArray(4)
            BitHelper.writeInt32(cont, 0, i)
            instance.commitToMemPool(Transaction(cont))
        }

        sleep(1000)

        assertEquals(100, instance.chain.getPersistedTransactions().asSequence().toList().size)
    }

    @Test
    fun possTest_1node() {
        val keypair = UserAuthHelper.generateKeyPair()
        val instance = Mockchain(EmptyApplication(), consensus = ProofOfStaticStakeConsensusCreator(1000, arrayOf(keypair.public.encoded), keypair))

        for (i in 1..50) {
            val cont = ByteArray(4)
            BitHelper.writeInt32(cont, 0, i)
            instance.commitToMemPool(Transaction(cont))
        }

        sleep(1500)

        assertEquals(50, instance.chain.getPersistedTransactions().asSequence().toList().size)

        for (i in 51..100) {
            val cont = ByteArray(4)
            BitHelper.writeInt32(cont, 0, i)
            instance.commitToMemPool(Transaction(cont))
        }

        sleep(1500)

        assertEquals(100, instance.chain.getPersistedTransactions().asSequence().toList().size)
    }
}