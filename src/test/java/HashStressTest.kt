import jokrey.mockchain.Mockchain
import jokrey.mockchain.consensus.ManualConsensusAlgorithm
import org.junit.jupiter.api.Test
import jokrey.mockchain.squash.BuildUponSquashHandler
import jokrey.mockchain.squash.PartialReplaceSquashHandler
import jokrey.mockchain.storage_classes.DependencyType
import jokrey.mockchain.storage_classes.Transaction
import jokrey.mockchain.storage_classes.dependenciesFrom
import jokrey.mockchain.visualization.util.EmptyApplication
import jokrey.mockchain.visualization.util.contentIsArbitrary
import java.util.*

class HashStressTest {
    @Test
    fun hashTest_b10_squash10() {
        runDeterministicGenerationAndSquash(1000, 6, 11, 2, 10, 10, true)
    }
    @Test
    fun hashTest_b10_squash1() {
        runDeterministicGenerationAndSquash(1000, 6, 11, 2, 10, 1, true)
    }
    @Test
    fun hashTest_b5_squash1() {
        runDeterministicGenerationAndSquash(1000, 6, 11, 2, 5, 1, true)
    }
    @Test
    fun hashTest_b10_squashEnd() {
        runDeterministicGenerationAndSquash(1000, 6, 11, 2, 10, 200, true)
    }

    //the following is a function originally found for performance testing.
    //  however it revealed a lot of hash collision detection issue the algorithm still had (due to this function's high collision rate
    //  that collision rate made it too instable for performance testing, but it is perfect for hash collision detection verification
    private fun runDeterministicGenerationAndSquash(genTxNum: Int, minTxSize: Int, maxTxSize: Int, depNumPerTx: Int, blockEveryTx: Int, squashEveryBlock: Int, squashEnd: Boolean) {
        val random_seed = Random().nextLong()//caused nice issues with seed 1, but now it has to prove it doesn't cause issues ever
        println("Seed: $random_seed") // for replayability in case of emergency
        val random = Random(random_seed)
        val instance = Mockchain(object: EmptyApplication() {
            override fun getBuildUponSquashHandler(): BuildUponSquashHandler
                    = {list, b -> list.flatMap { half(it).asIterable() }.toByteArray()+half(b) }

            override fun getPartialReplaceSquashHandler(): PartialReplaceSquashHandler
                    = { latestContent, _ -> half(latestContent) }

            fun half(bs:ByteArray) = bs.copyOf(bs.size/2)
        })
        instance.consensus as ManualConsensusAlgorithm

        var numberOfGeneratedTx = 0
        var numberOfGeneratedBlocks = 0
        for(i in 0 until genTxNum) {
            val previous = instance.memPool.getTransactions() + instance.chain.getPersistedTransactions().asSequence().toList()
            try {
                if(previous.size > depNumPerTx*2) {
                    val depsOn = Array(depNumPerTx) {
                        val rand = random.nextInt(previous.size - 2) + 1
                        previous[rand].hash
                    }
                    val noDoubles = depsOn.toSet().toTypedArray()
                    instance.commitToMemPool(Transaction(contentIsArbitrary(random, minTxSize, maxTxSize), *dependenciesFrom(DependencyType.BUILDS_UPON, *noDoubles) + dependenciesFrom(DependencyType.REPLACES_PARTIAL, *noDoubles)))
                } else {
                    instance.commitToMemPool(Transaction(contentIsArbitrary(random, minTxSize, maxTxSize)))
                }
                numberOfGeneratedTx++
            } catch(ex: Exception) {
                ex.printStackTrace()
            }

            if(numberOfGeneratedTx % blockEveryTx == 0) {
                val squash = numberOfGeneratedBlocks!=0 && numberOfGeneratedBlocks % squashEveryBlock == 0
                (instance.consensus as ManualConsensusAlgorithm).performConsensusRound(squash)
                numberOfGeneratedBlocks++
            }
        }

        (instance.consensus as ManualConsensusAlgorithm).performConsensusRound(squashEnd)
    }
}