import jokrey.mockchain.Mockchain
import jokrey.mockchain.application.examples.calculator.*
import jokrey.mockchain.consensus.ManualConsensusAlgorithm
import jokrey.mockchain.consensus.ManualConsensusAlgorithmCreator
import org.junit.jupiter.api.Test
import jokrey.mockchain.storage_classes.Dependency
import jokrey.mockchain.storage_classes.DependencyType
import jokrey.mockchain.storage_classes.Transaction
import kotlin.test.assertEquals

class CalculatorTest {
    @Test
    fun testSingleStringCalculator() {
        //ALSO TESTS THAT A BUILD UPON EDGE IS ALLOWED TO GENERATE AN EXISTING HASH IF REPLACE ELIMINATES THAT HASH IN THE SAME ROUND

        //now works, the comments are nonetheless valid
        //: this does not work for squash EVERY round(even with consensus after every commit), because "last" is squashed and therefore alters its hash before being used as a dependency
        //:   the hash change is invisible to the test tx adder (the application receives the changes over txAltered, but this test AND arguably normal users later won't)
        //(fix): 1. add a listener to txAltered to which a potential end user(and this test case) can subscribe to
        //(fix): 2. ADD LOGIC TO SQUASH ALGORITHM THAT WILL ALTER MEM POOL DEPENDENCIES  ((THIS IS NOW DONE - ACTUALLY WAS AT THE TIME BUT I FORGOT))
        //           however - this essentially just shifts the problem - it becomes possible at commit time to verify that the dependency exists, but after that... (if anything outside of app and chain is caching a dependency for the future - there is NOTHING that can be done about this)

        val variations: Array<Pair<Int, Int>> = arrayOf(Pair(1, /* squashEveryNRounds*/ -1), Pair(1, /* squashEveryNRounds*/ 1), Pair(1, /* squashEveryNRounds*/ 10)
                ,Pair(10, /* squashEveryNRounds*/ -1), Pair(10, /* squashEveryNRounds*/ 10), Pair(10, /* squashEveryNRounds*/ 10)
        )

        var result = 0.0
        val calcs = Array(4) {
            if(it == 0) {
                result = 123.0
                Initial(123.0)
            } else if(it % 4 == 0) {
                result += (it / 4).toDouble()
                Addition((it / 4).toDouble())
            } else if(it % 4 == 1) {
                result -= (it / 4).toDouble()
                Subtraction((it / 4).toDouble())
            } else if(it % 4 == 2 && it/4 != 0) {
                result /= (it / 4).toDouble()
                Division((it / 4).toDouble())
            } else if(it % 4 == 2) {
                result *= (it).toDouble()
                Multiplication((it * 101).toDouble())
            } else {
                result *= (it / 4).toDouble()
                Multiplication((it / 4).toDouble())
            }
        }

        var definiteState:String? = null
        for(variation in variations) {
            println("testing variation: $variation")
            val (performConsensusEvery, squashEveryNRounds) = variation
            val app = SingleStringCalculator()
            val instance = Mockchain(app, consensus = ManualConsensusAlgorithmCreator(squashEveryNRounds))
            val consensus = instance.consensus as ManualConsensusAlgorithm

            for ((i, calc) in calcs.withIndex()) {
                val last = app.getLastInString(calc.string)
                val new = if(last == null)
                    Transaction(calc.toTxContent())
                else
                    Transaction(calc.toTxContent(), Dependency(last.hash, DependencyType.BUILDS_UPON), Dependency(last.hash, DependencyType.REPLACES))
                println("calc = $calc")
                instance.commitToMemPool(new)

                if(i % performConsensusEvery == 0) //NOT ANYMORE TRUE:::: not possible, due to hash changes of the bDependencies - which are invisible to the mem pool
                    consensus.performConsensusRound(false)
            }
            consensus.performConsensusRound(false)

            println("result = $result")
            println("definiteState = $definiteState")

            if(definiteState==null)
                definiteState = app.exhaustiveStateDescriptor()
            else
                assertEquals(definiteState, app.exhaustiveStateDescriptor())

            assertEquals(result, app.getResults()[0])

            consensus.performConsensusRound(true)

            assertEquals(definiteState, app.exhaustiveStateDescriptor())

            val freshCompareAppAfter = app.newEqualInstance() as MashedCalculator
            instance.chain.applyReplayTo(freshCompareAppAfter)

            assertEquals(result, freshCompareAppAfter.getResults()[0])
            assertEquals(definiteState, freshCompareAppAfter.exhaustiveStateDescriptor())
        }
    }




}