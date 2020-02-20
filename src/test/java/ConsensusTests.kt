import jokrey.mockchain.Mockchain
import jokrey.mockchain.Nockchain
import jokrey.mockchain.consensus.ProofOfStaticStakeConsensusCreator
import jokrey.mockchain.consensus.SimpleProofOfWorkConsensusCreator
import jokrey.mockchain.storage_classes.Transaction
import jokrey.mockchain.visualization.util.EmptyApplication
import jokrey.mockchain.visualization.util.UserAuthHelper
import jokrey.utilities.bitsandbytes.BitHelper
import jokrey.utilities.network.link2peer.P2Link
import jokrey.utilities.simple.data_structure.queue.ConcurrentQueueTest.sleep
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 *
 * @author jokrey
 */
class ConsensusTests {
    @Test
    fun powTest() {
        val instance = Mockchain(EmptyApplication(), consensus = SimpleProofOfWorkConsensusCreator(1, ByteArray(12)))

        for (i in 1..100)
            instance.commitToMemPool(Transaction(BitHelper.getBytes(i)))

        sleep(1000)

        assertEquals(100, instance.chain.persistedTxCount())
    }

    @Test
    fun possTest_1node() {
        val keypair = UserAuthHelper.generateKeyPair()
        val instance = Mockchain(EmptyApplication(), consensus = ProofOfStaticStakeConsensusCreator(1000, arrayOf(keypair.public.encoded), keypair))

        for (i in 1..50)
            instance.commitToMemPool(Transaction(BitHelper.getBytes(i)))

        sleep(1500)

        assertEquals(50, instance.chain.persistedTxCount())

        for (i in 51..100)
            instance.commitToMemPool(Transaction(BitHelper.getBytes(i)))

        sleep(1500)

        assertEquals(100, instance.chain.persistedTxCount())
    }

    @Test fun possTest_3nodes() {
        val keypair1 = UserAuthHelper.generateKeyPair()
        val keypair2 = UserAuthHelper.generateKeyPair()
        val keypair3 = UserAuthHelper.generateKeyPair()
        val identities = arrayOf(keypair1.public.encoded, keypair2.public.encoded, keypair3.public.encoded)
        val instance1 = Nockchain(EmptyApplication(), P2Link.createPublicLink("localhost", 43231),
                consensus = ProofOfStaticStakeConsensusCreator(1000, identities, keypair1))
        val instance2 = Nockchain(EmptyApplication(), P2Link.createPublicLink("localhost", 43232),
                consensus = ProofOfStaticStakeConsensusCreator(1000, identities, keypair2))
        val instance3 = Nockchain(EmptyApplication(), P2Link.createPublicLink("localhost", 43233),
                consensus = ProofOfStaticStakeConsensusCreator(1000, identities, keypair3))
        instance2.connect(instance1.selfLink, instance3.selfLink)
        instance1.connect(instance3.selfLink)

        for (i in 1..5)
            instance1.commitToMemPool(Transaction(BitHelper.getBytes(i)))

        sleep(1100)
        assertAllEqualAnd(5, instance1, instance2, instance3)

        for (i in 6..10)
            instance2.commitToMemPool(Transaction(BitHelper.getBytes(i)))

        sleep(1100)
        assertAllEqualAnd(10, instance1, instance2, instance3)

        for (i in 11..15)
            instance3.commitToMemPool(Transaction(BitHelper.getBytes(i)))

        sleep(1100)
        assertAllEqualAnd(15, instance1, instance2, instance3)

        for (i in 16..20)
            instance2.commitToMemPool(Transaction(BitHelper.getBytes(i)))

        sleep(1100)
        assertAllEqualAnd(20, instance1, instance2, instance3)
    }

    private fun assertAllEqualAnd(numPersisted: Int, vararg instances: Nockchain) {
        for(instance in instances)
            assertEquals(numPersisted, instance.chain.persistedTxCount())

        for(i in 0 until (instances.size-1)) {
            assertArrayEquals(instances[i].chain.getBlocks(), instances[i + 1].chain.getBlocks())
            assertEquals(instances[i].chain.getPersistedTransactions().asSequence().toList(), instances[i + 1].chain.getPersistedTransactions().asSequence().toList())
        }
    }
}