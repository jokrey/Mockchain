import jokrey.utilities.debug_analysis_helper.AverageCallTimeMarker
import org.junit.jupiter.api.Test
import squash.BuildUponSquashHandler
import squash.PartialReplaceSquashHandler
import storage_classes.Chain
import storage_classes.DependencyType
import storage_classes.Transaction
import storage_classes.dependenciesFrom
import visualization.util.EmptyApplication
import visualization.util.contentIsArbitrary
import java.lang.Long.max
import java.util.*

import java.util.logging.LogManager

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
        val chain = Chain(object: EmptyApplication() {
            override fun getBuildUponSquashHandler(): BuildUponSquashHandler
                    = {list, b -> list.flatMap { half(it).asIterable() }.toByteArray()+half(b) }

            override fun getPartialReplaceSquashHandler(): PartialReplaceSquashHandler
                    = { latestContent, _ -> half(latestContent) }

            fun half(bs:ByteArray) = bs.copyOf(bs.size/2)
        })

        var numberOfGeneratedTx = 0
        var numberOfGeneratedBlocks = 0
        for(i in 0 until genTxNum) {
            val previous = chain.getMemPoolContent() + chain.getPersistedTransactions().asSequence().toList()
            try {
                if(previous.size > depNumPerTx*2) {
                    val depsOn = Array(depNumPerTx) {
                        val rand = random.nextInt(previous.size - 2) + 1
                        previous[rand].hash
                    }
                    val noDoubles = depsOn.toSet().toTypedArray()
                    chain.commitToMemPool(Transaction(contentIsArbitrary(random, minTxSize, maxTxSize), *dependenciesFrom(DependencyType.BUILDS_UPON, *noDoubles) + dependenciesFrom(DependencyType.REPLACES_PARTIAL, *noDoubles)))
                } else {
                    chain.commitToMemPool(Transaction(contentIsArbitrary(random, minTxSize, maxTxSize)))
                }
                numberOfGeneratedTx++
            } catch(ex: Exception) {
                ex.printStackTrace()
            }

            if(numberOfGeneratedTx % blockEveryTx == 0) {
                val squash = numberOfGeneratedBlocks!=0 && numberOfGeneratedBlocks % squashEveryBlock == 0
                chain.performConsensusRound(squash)
                numberOfGeneratedBlocks++
            }
        }

        chain.performConsensusRound(squashEnd)
    }
}