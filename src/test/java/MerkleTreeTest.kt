import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import storage_classes.*
import visualization.util.contentIsArbitrary

/**
 *
 * @author jokrey
 */

class MerkleTreeTest {
    @Test
    fun testPartialValidation() {
        for(i in 1..256) {
            val hashes = Array(i) { Hash(contentIsArbitrary()) }
            val tree = MerkleTree(*hashes)
            for(ii in 0 until i) {
                val partial = tree.getPartialVerificationTreeFor(ii)
                assertTrue(partial.last() == tree.getRoot())
                assertTrue(validateWithPartialTree(partial, hashes[ii]))
                assertArrayEquals(partial, bytesToPartial(partialToBytes(partial)))
            }
        }
    }
}



