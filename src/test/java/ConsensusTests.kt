import jokrey.mockchain.Mockchain
import jokrey.mockchain.consensus.ProofOfWorkConsensus
import jokrey.mockchain.consensus.SimpleProofOfWorkConsensusCreator
import jokrey.mockchain.storage_classes.ImmutableByteArray
import jokrey.mockchain.storage_classes.Transaction
import jokrey.mockchain.visualization.util.EmptyApplication
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
        val instance = Mockchain(EmptyApplication(), consensus = SimpleProofOfWorkConsensusCreator(2, ImmutableByteArray(ByteArray(12))))

        for (i in 1..100) {
            val cont = ByteArray(4)
            BitHelper.writeInt32(cont, 0, i)
            instance.commitToMemPool(Transaction(cont))
        }

        sleep(1000)

        assertEquals(100, instance.chain.getPersistedTransactions().asSequence().toList().size)
    }
}