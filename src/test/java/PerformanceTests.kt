import com.google.common.io.Files
import jokrey.mockchain.Mockchain
import jokrey.mockchain.consensus.ManualConsensusAlgorithm
import jokrey.utilities.debug_analysis_helper.AverageCallTimeMarker
import jokrey.utilities.debug_analysis_helper.BoxPlotDataGatherer
import jokrey.mockchain.squash.BuildUponSquashHandler
import jokrey.mockchain.squash.PartialReplaceSquashHandler
import jokrey.mockchain.storage_classes.*
import jokrey.mockchain.visualization.util.EmptyApplication
import jokrey.mockchain.visualization.util.contentIsArbitrary
import java.util.logging.LogManager
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.abs
import kotlin.math.max


class PerformanceTests {
//    @Test
    fun run_ThesisTests() {
        LogManager.getLogManager().reset() //remove print outs - they may impact performance

        val iterations = 101

        arrayOf(1,10,100,1000,10000).forEach { squash -> runPersistent(iterations, 10000, 4, 1000, squash) }
        arrayOf(1,10,100,1000,10000).forEach { squash -> runPersistent(iterations, 10000, 4, 100, squash) }
        arrayOf(1,10,100,1000,10000).forEach { squash -> runPersistent(iterations, 10000, 4, 10, squash) }
        arrayOf(1,10,100,1000,10000).forEach { squash -> runPersistent(iterations, 10000, 4, 1, squash) }

        arrayOf(1,10,100,1000,10000).forEach { squash -> runPersistent(iterations, 10000, 10, 10, squash) }
        arrayOf(1,10,100,1000,10000).forEach { squash -> runPersistent(iterations, 10000, 0, 10, squash) }
        arrayOf(1,10,100,1000,10000).forEach { squash -> runPersistent(iterations, 10000, 2, 10, squash) }

        arrayOf(1,10,100,1000,10000).forEach { squash -> runPersistent(iterations, 100, 4, 10, squash) }
        arrayOf(1,10,100,1000,10000).forEach { squash -> runPersistent(iterations, 1000, 4, 10, squash) }


        AverageCallTimeMarker.print_all(AverageCallTimeMarker.get_all().sortedBy { it.average_in_nano }.toTypedArray(), null)
        BoxPlotDataGatherer.print_all(true)
    }



    val genTxNums = intArrayOf(100, 1000, 3000, 20000)
    val depPerTxNums = intArrayOf(2, /*5,*/ 10)
    val blockEveryTx_s = intArrayOf(1, 10)
    val minTxSize = 60
    val maxTxSize = 120

//    @Test
    fun perfTestAll() {
    LogManager.getLogManager().reset() //remove print outs - they may impact performance

        for(genTxNum in genTxNums) {
            for(depPerTxNum in depPerTxNums) {
                for(blockEveryTx in blockEveryTx_s) {
                    allPersistanceConfigsWith(genTxNum, depPerTxNum, blockEveryTx, 1)
                    Thread.sleep(1000)
                    allPersistanceConfigsWith(genTxNum, depPerTxNum, blockEveryTx, 10)
                    Thread.sleep(1000)
                    allPersistanceConfigsWith(genTxNum, depPerTxNum, blockEveryTx, 200)
                }
            }
        }

        AverageCallTimeMarker.print_all(AverageCallTimeMarker.get_all().sortedBy { it.average_in_nano }.toTypedArray(), null)
        BoxPlotDataGatherer.print_all()
    }

    fun allPersistanceConfigsWith(genTxNum: Int, depNumPerTx: Int, blockEveryTx: Int, squashEveryBlock: Int) {
//        runDeterministicGenerationAndSquash(genTxNum, minTxSize, maxTxSize, depNumPerTx, blockEveryTx, squashEveryBlock, true,
//                NonPersistentBlockChainStorage(), NonPersistentTransactionStorage())
//        runDeterministicGenerationAndSquash(genTxNum, minTxSize, maxTxSize, depNumPerTx, blockEveryTx, squashEveryBlock, true,
//                PersistentBlockChainStorage(Files.createTempDir(), true), NonPersistentTransactionStorage())
        runDeterministicGenerationAndSquash(genTxNum, minTxSize, maxTxSize, depNumPerTx, blockEveryTx, squashEveryBlock, true,
                PersistentStorage(Files.createTempDir(), true))
    }

    fun runPersistent(iterations: Int, genTxNum: Int, depNumPerTx: Int, blockEveryTx: Int, squashEveryBlock: Int) {
        repeat(iterations) {
            runDeterministicGenerationAndSquash(genTxNum, minTxSize, maxTxSize, depNumPerTx, blockEveryTx, squashEveryBlock, true,
                    PersistentStorage(Files.createTempDir(), true), it == 0)
        }
    }


    fun runDeterministicGenerationAndSquash(genTxNum: Int, minTxSize: Int, maxTxSize: Int, depNumPerTx: Int, blockEveryTx: Int,
                                            squashEveryXTransaction: Int, squashEnd: Boolean,
                                            store: StorageModel,
                                            print: Boolean = true) {
        val callId = "runDeterministicGenerationAndSquash_${genTxNum}_deps${depNumPerTx}_b${blockEveryTx}_s${squashEveryXTransaction}_${squashEnd}_${store.javaClass.simpleName}"
        val callIdRelevantSection = "runDeterministicGenerationAndSquash_${genTxNum}_deps${depNumPerTx}_b${blockEveryTx}_s${squashEveryXTransaction}_${squashEnd}_${store.javaClass.simpleName} - only squash"
        BoxPlotDataGatherer.mark_call_start(callId)
        AverageCallTimeMarker.mark_call_start(callId)

        var numberOfCurrentlyPersistedTransactions = 0

        val selectedPrevious = ArrayList<TransactionHash>(100)
        val instance = Mockchain(object:EmptyApplication() {
            override fun getBuildUponSquashHandler(): BuildUponSquashHandler
                    = {list, b -> list.flatMap { half(it).asIterable() }.toByteArray()+half(b) }

            override fun getPartialReplaceSquashHandler(): PartialReplaceSquashHandler
                    = { latestContent, _ -> latestContent.copyOf(latestContent.size-1)
            }

            fun half(bs:ByteArray) = bs.copyOf(bs.size/2)

            //because of txAltered selectedPrevious is unpredictable among different blockEveryTx and squashEveryBlock parameters
            //   For some configurations it remains possible to have dependencies on transactions that already have dependencies
            //   but if squashEveryBlock = 1, then that is not possible.
            override fun txAltered(instance: Mockchain, oldHash: TransactionHash, oldTx: Transaction, newHash: TransactionHash, newTx: Transaction, txWasPersisted: Boolean) {
                val index = selectedPrevious.indexOf(oldHash)
                if(index != -1)
                    selectedPrevious[index] = newHash
            }
            override fun txRejected(instance: Mockchain, oldHash: TransactionHash, oldTx: Transaction, reason: RejectionReason) {
                selectedPrevious.remove(oldHash)
                numberOfCurrentlyPersistedTransactions--
            }
            override fun txRemoved(instance: Mockchain, oldHash: TransactionHash, oldTx: Transaction, txWasPersisted: Boolean) {
                selectedPrevious.remove(oldHash)
                numberOfCurrentlyPersistedTransactions--
            }
        }, store)
        instance.consensus as ManualConsensusAlgorithm

        val txGenRandom = Random(1) //deterministic since seeded

        var maxStorageRequirements = Long.MIN_VALUE
        var maxPersistedTransactions = Int.MIN_VALUE
        var numberOfGeneratedTx = 0
        var numberOfGeneratedBlocks = 0
        var numberOfSquashes = 0

        for(i in 0 until genTxNum) {
            try {
                val genTx =
                    if(depNumPerTx > 0 && selectedPrevious.size > depNumPerTx*2) {
                        val depsOn = Array(if(i%2==0) {depNumPerTx} else {depNumPerTx/2}) {
                            selectedPrevious[abs((i * selectedPrevious.size) % selectedPrevious.size)]
                        }
                        val noDoubles = depsOn.toSet().toTypedArray()
                        if(i%2 == 0) {
                            selectedPrevious.removeAll(noDoubles) // to limit nto1 dependencies (which would be a fine thing to test, but are very volatile in occurrence in different configurations)
                            Transaction(contentIsArbitrary(txGenRandom, minTxSize, maxTxSize),
                                    *dependenciesFrom(DependencyType.REPLACES, *noDoubles))
                        } else {
                            Transaction(contentIsArbitrary(txGenRandom, minTxSize, maxTxSize),
                                    *dependenciesFrom(DependencyType.BUILDS_UPON, *noDoubles) + dependenciesFrom(DependencyType.REPLACES_PARTIAL, *noDoubles))
                        }
                    } else {
                        Transaction(contentIsArbitrary(txGenRandom, minTxSize, maxTxSize))
                    }
                if(selectedPrevious.size < depNumPerTx*2)
                    selectedPrevious.add(genTx.hash)
                else if(i%4 == 0 && i < genTxNum/10)
                    selectedPrevious.add(genTx.hash)
                else if(i%16 == 0)
                    selectedPrevious.add(genTx.hash)

                numberOfCurrentlyPersistedTransactions++
                instance.commitToMemPool(genTx)
                numberOfGeneratedTx++
            } catch(ex: Exception) {
                ex.printStackTrace()
            }

            maxStorageRequirements = max(maxStorageRequirements, instance.calculateStorageRequirementsInBytes())
            maxPersistedTransactions = max(maxPersistedTransactions, numberOfCurrentlyPersistedTransactions)


            if(numberOfGeneratedTx % blockEveryTx == 0) {
                val squash = numberOfGeneratedTx != 0 && numberOfGeneratedTx / squashEveryXTransaction > numberOfSquashes
                    if(squash){
                        AverageCallTimeMarker.mark_call_start(callIdRelevantSection)
//                        BoxPlotDataGatherer.mark_call_start(callIdRelevantSection)
                    }
                        (instance.consensus as ManualConsensusAlgorithm).performConsensusRound(squash)
                numberOfGeneratedBlocks++
                    if(squash) {
                        AverageCallTimeMarker.mark_call_end(callIdRelevantSection)
//                        BoxPlotDataGatherer.mark_call_end(callIdRelevantSection)
//                        println("numberOfGeneratedTx = ${numberOfGeneratedTx}")
//                        println("squashEveryXTransaction = ${squashEveryXTransaction}")
//                        println("numberOfSquashes = ${numberOfSquashes}")

                        numberOfSquashes++
                    }
            }

            maxStorageRequirements = max(maxStorageRequirements, instance.calculateStorageRequirementsInBytes())
            maxPersistedTransactions = max(maxPersistedTransactions, numberOfCurrentlyPersistedTransactions)
        }

            if(squashEnd){ AverageCallTimeMarker.mark_call_start(callIdRelevantSection)
//                BoxPlotDataGatherer.mark_call_start(callIdRelevantSection)
            }
        (instance.consensus as ManualConsensusAlgorithm).performConsensusRound(squashEnd)
        maxStorageRequirements = max(maxStorageRequirements, instance.calculateStorageRequirementsInBytes())
            if(squashEnd) { AverageCallTimeMarker.mark_call_end(callIdRelevantSection)
//                BoxPlotDataGatherer.mark_call_end(callIdRelevantSection)
                numberOfSquashes++
            }

        AverageCallTimeMarker.mark_call_end(callId)
        BoxPlotDataGatherer.mark_call_end(callId)

        if(print) {
            println("$callId\nended(numberOfSquashes=$numberOfSquashes, numberOfBlocks=$numberOfGeneratedBlocks, numberOfGeneratedTx=$numberOfGeneratedTx)")
            println("RAW STORAGE REQUIREMENTS: after: " + instance.calculateStorageRequirementsInBytes() + "bytes, max: " + maxStorageRequirements + "bytes")
            println("PERSISTED TRANSACTION COUNT: after: " + numberOfCurrentlyPersistedTransactions + "txs, max: " + maxPersistedTransactions + "txs")
        }
    }
}




//runDeterministicGenerationAndSquash_10000_deps4_b1000_s1_true_PersistentStorage
//ended(numberOfSquashes=11, numberOfBlocks=10, numberOfGeneratedTx=10000)
//RAW STORAGE REQUIREMENTS: after: 3145744bytes, max: 3273916bytes
//PERSISTED TRANSACTION COUNT: after: 8760txs, max: 8854txs
//runDeterministicGenerationAndSquash_10000_deps4_b1000_s10_true_PersistentStorage
//ended(numberOfSquashes=11, numberOfBlocks=10, numberOfGeneratedTx=10000)
//RAW STORAGE REQUIREMENTS: after: 3145744bytes, max: 3273916bytes
//PERSISTED TRANSACTION COUNT: after: 8760txs, max: 8854txs
//runDeterministicGenerationAndSquash_10000_deps4_b1000_s100_true_PersistentStorage
//ended(numberOfSquashes=11, numberOfBlocks=10, numberOfGeneratedTx=10000)
//RAW STORAGE REQUIREMENTS: after: 3145744bytes, max: 3273916bytes
//PERSISTED TRANSACTION COUNT: after: 8760txs, max: 8854txs
//runDeterministicGenerationAndSquash_10000_deps4_b1000_s1000_true_PersistentStorage
//ended(numberOfSquashes=11, numberOfBlocks=10, numberOfGeneratedTx=10000)
//RAW STORAGE REQUIREMENTS: after: 3145744bytes, max: 3273916bytes
//PERSISTED TRANSACTION COUNT: after: 8760txs, max: 8854txs
//runDeterministicGenerationAndSquash_10000_deps4_b1000_s10000_true_PersistentStorage
//ended(numberOfSquashes=2, numberOfBlocks=10, numberOfGeneratedTx=10000)
//RAW STORAGE REQUIREMENTS: after: 4194320bytes, max: 4194320bytes
//PERSISTED TRANSACTION COUNT: after: 8760txs, max: 9602txs
//runDeterministicGenerationAndSquash_10000_deps4_b100_s1_true_PersistentStorage
//ended(numberOfSquashes=101, numberOfBlocks=100, numberOfGeneratedTx=10000)
//RAW STORAGE REQUIREMENTS: after: 4194320bytes, max: 4207150bytes
//PERSISTED TRANSACTION COUNT: after: 8760txs, max: 8770txs
//runDeterministicGenerationAndSquash_10000_deps4_b100_s10_true_PersistentStorage
//ended(numberOfSquashes=101, numberOfBlocks=100, numberOfGeneratedTx=10000)
//RAW STORAGE REQUIREMENTS: after: 4194320bytes, max: 4207150bytes
//PERSISTED TRANSACTION COUNT: after: 8760txs, max: 8770txs
//runDeterministicGenerationAndSquash_10000_deps4_b100_s100_true_PersistentStorage
//ended(numberOfSquashes=101, numberOfBlocks=100, numberOfGeneratedTx=10000)
//RAW STORAGE REQUIREMENTS: after: 4194320bytes, max: 4207150bytes
//PERSISTED TRANSACTION COUNT: after: 8760txs, max: 8770txs
//runDeterministicGenerationAndSquash_10000_deps4_b100_s1000_true_PersistentStorage
//ended(numberOfSquashes=11, numberOfBlocks=100, numberOfGeneratedTx=10000)
//RAW STORAGE REQUIREMENTS: after: 4194320bytes, max: 4207063bytes
//PERSISTED TRANSACTION COUNT: after: 8760txs, max: 8826txs
//runDeterministicGenerationAndSquash_10000_deps4_b100_s10000_true_PersistentStorage
//ended(numberOfSquashes=2, numberOfBlocks=100, numberOfGeneratedTx=10000)
//RAW STORAGE REQUIREMENTS: after: 4194320bytes, max: 4194320bytes
//PERSISTED TRANSACTION COUNT: after: 8760txs, max: 9574txs
//runDeterministicGenerationAndSquash_10000_deps4_b10_s1_true_PersistentStorage
//ended(numberOfSquashes=1001, numberOfBlocks=1000, numberOfGeneratedTx=10000)
//RAW STORAGE REQUIREMENTS: after: 7400770bytes, max: 10492110bytes
//PERSISTED TRANSACTION COUNT: after: 8838txs, max: 8838txs
//runDeterministicGenerationAndSquash_10000_deps4_b10_s10_true_PersistentStorage
//ended(numberOfSquashes=1001, numberOfBlocks=1000, numberOfGeneratedTx=10000)
//RAW STORAGE REQUIREMENTS: after: 7400770bytes, max: 10491147bytes
//PERSISTED TRANSACTION COUNT: after: 8838txs, max: 8838txs
//runDeterministicGenerationAndSquash_10000_deps4_b10_s100_true_PersistentStorage
//ended(numberOfSquashes=101, numberOfBlocks=1000, numberOfGeneratedTx=10000)
//RAW STORAGE REQUIREMENTS: after: 4194320bytes, max: 4195726bytes
//PERSISTED TRANSACTION COUNT: after: 8838txs, max: 8844txs
//runDeterministicGenerationAndSquash_10000_deps4_b10_s1000_true_PersistentStorage
//ended(numberOfSquashes=11, numberOfBlocks=1000, numberOfGeneratedTx=10000)
//RAW STORAGE REQUIREMENTS: after: 4194320bytes, max: 4195724bytes
//PERSISTED TRANSACTION COUNT: after: 8838txs, max: 8900txs
//runDeterministicGenerationAndSquash_10000_deps4_b10_s10000_true_PersistentStorage
//ended(numberOfSquashes=2, numberOfBlocks=1000, numberOfGeneratedTx=10000)
//RAW STORAGE REQUIREMENTS: after: 4194320bytes, max: 4194320bytes
//PERSISTED TRANSACTION COUNT: after: 8838txs, max: 9648txs
//runDeterministicGenerationAndSquash_10000_deps4_b1_s1_true_PersistentStorage
//ended(numberOfSquashes=10001, numberOfBlocks=10000, numberOfGeneratedTx=10000)
//RAW STORAGE REQUIREMENTS: after: 23170854bytes, max: 27231088bytes
//PERSISTED TRANSACTION COUNT: after: 9190txs, max: 9190txs
//runDeterministicGenerationAndSquash_10000_deps4_b1_s10_true_PersistentStorage
//ended(numberOfSquashes=1001, numberOfBlocks=10000, numberOfGeneratedTx=10000)
//RAW STORAGE REQUIREMENTS: after: 14728433bytes, max: 18806731bytes
//PERSISTED TRANSACTION COUNT: after: 9190txs, max: 9190txs
//runDeterministicGenerationAndSquash_10000_deps4_b1_s100_true_PersistentStorage
//ended(numberOfSquashes=101, numberOfBlocks=10000, numberOfGeneratedTx=10000)
//RAW STORAGE REQUIREMENTS: after: 7357382bytes, max: 10479897bytes
//PERSISTED TRANSACTION COUNT: after: 9190txs, max: 9196txs
//runDeterministicGenerationAndSquash_10000_deps4_b1_s1000_true_PersistentStorage
//ended(numberOfSquashes=11, numberOfBlocks=10000, numberOfGeneratedTx=10000)
//RAW STORAGE REQUIREMENTS: after: 5242896bytes, max: 5243113bytes
//PERSISTED TRANSACTION COUNT: after: 9190txs, max: 9252txs
//runDeterministicGenerationAndSquash_10000_deps4_b1_s10000_true_PersistentStorage
//ended(numberOfSquashes=2, numberOfBlocks=10000, numberOfGeneratedTx=10000)
//RAW STORAGE REQUIREMENTS: after: 5242896bytes, max: 5242896bytes
//PERSISTED TRANSACTION COUNT: after: 9190txs, max: 10000txs
//runDeterministicGenerationAndSquash_10000_deps10_b10_s1_true_PersistentStorage
//ended(numberOfSquashes=1001, numberOfBlocks=1000, numberOfGeneratedTx=10000)
//RAW STORAGE REQUIREMENTS: after: 12711404bytes, max: 14773236bytes
//PERSISTED TRANSACTION COUNT: after: 8843txs, max: 8843txs
//runDeterministicGenerationAndSquash_10000_deps10_b10_s10_true_PersistentStorage
//ended(numberOfSquashes=1001, numberOfBlocks=1000, numberOfGeneratedTx=10000)
//RAW STORAGE REQUIREMENTS: after: 12711404bytes, max: 14773236bytes
//PERSISTED TRANSACTION COUNT: after: 8843txs, max: 8843txs
//runDeterministicGenerationAndSquash_10000_deps10_b10_s100_true_PersistentStorage
//ended(numberOfSquashes=101, numberOfBlocks=1000, numberOfGeneratedTx=10000)
//RAW STORAGE REQUIREMENTS: after: 5242896bytes, max: 5244288bytes
//PERSISTED TRANSACTION COUNT: after: 8843txs, max: 8849txs
//runDeterministicGenerationAndSquash_10000_deps10_b10_s1000_true_PersistentStorage
//ended(numberOfSquashes=11, numberOfBlocks=1000, numberOfGeneratedTx=10000)
//RAW STORAGE REQUIREMENTS: after: 4194320bytes, max: 4195724bytes
//PERSISTED TRANSACTION COUNT: after: 8843txs, max: 8905txs
//runDeterministicGenerationAndSquash_10000_deps10_b10_s10000_true_PersistentStorage
//ended(numberOfSquashes=2, numberOfBlocks=1000, numberOfGeneratedTx=10000)
//RAW STORAGE REQUIREMENTS: after: 4194320bytes, max: 4194320bytes
//PERSISTED TRANSACTION COUNT: after: 8843txs, max: 9650txs
//runDeterministicGenerationAndSquash_10000_deps0_b10_s1_true_PersistentStorage
//ended(numberOfSquashes=1001, numberOfBlocks=1000, numberOfGeneratedTx=10000)
//RAW STORAGE REQUIREMENTS: after: 3145744bytes, max: 3147137bytes
//PERSISTED TRANSACTION COUNT: after: 10000txs, max: 10000txs
//runDeterministicGenerationAndSquash_10000_deps0_b10_s10_true_PersistentStorage
//ended(numberOfSquashes=1001, numberOfBlocks=1000, numberOfGeneratedTx=10000)
//RAW STORAGE REQUIREMENTS: after: 3145744bytes, max: 3147137bytes
//PERSISTED TRANSACTION COUNT: after: 10000txs, max: 10000txs
//runDeterministicGenerationAndSquash_10000_deps0_b10_s100_true_PersistentStorage
//ended(numberOfSquashes=101, numberOfBlocks=1000, numberOfGeneratedTx=10000)
//RAW STORAGE REQUIREMENTS: after: 3145744bytes, max: 3147137bytes
//PERSISTED TRANSACTION COUNT: after: 10000txs, max: 10000txs
//runDeterministicGenerationAndSquash_10000_deps0_b10_s1000_true_PersistentStorage
//ended(numberOfSquashes=11, numberOfBlocks=1000, numberOfGeneratedTx=10000)
//RAW STORAGE REQUIREMENTS: after: 3145744bytes, max: 3147137bytes
//PERSISTED TRANSACTION COUNT: after: 10000txs, max: 10000txs
//runDeterministicGenerationAndSquash_10000_deps0_b10_s10000_true_PersistentStorage
//ended(numberOfSquashes=2, numberOfBlocks=1000, numberOfGeneratedTx=10000)
//RAW STORAGE REQUIREMENTS: after: 3145744bytes, max: 3147137bytes
//PERSISTED TRANSACTION COUNT: after: 10000txs, max: 10000txs
//runDeterministicGenerationAndSquash_10000_deps2_b10_s1_true_PersistentStorage
//ended(numberOfSquashes=1001, numberOfBlocks=1000, numberOfGeneratedTx=10000)
//RAW STORAGE REQUIREMENTS: after: 5242896bytes, max: 5244300bytes
//PERSISTED TRANSACTION COUNT: after: 8836txs, max: 8836txs
//runDeterministicGenerationAndSquash_10000_deps2_b10_s10_true_PersistentStorage
//ended(numberOfSquashes=1001, numberOfBlocks=1000, numberOfGeneratedTx=10000)
//RAW STORAGE REQUIREMENTS: after: 5242896bytes, max: 5244300bytes
//PERSISTED TRANSACTION COUNT: after: 8836txs, max: 8836txs
//runDeterministicGenerationAndSquash_10000_deps2_b10_s100_true_PersistentStorage
//ended(numberOfSquashes=101, numberOfBlocks=1000, numberOfGeneratedTx=10000)
//RAW STORAGE REQUIREMENTS: after: 4194320bytes, max: 4195724bytes
//PERSISTED TRANSACTION COUNT: after: 8836txs, max: 8842txs
//runDeterministicGenerationAndSquash_10000_deps2_b10_s1000_true_PersistentStorage
//ended(numberOfSquashes=11, numberOfBlocks=1000, numberOfGeneratedTx=10000)
//RAW STORAGE REQUIREMENTS: after: 4194320bytes, max: 4195712bytes
//PERSISTED TRANSACTION COUNT: after: 8836txs, max: 8898txs
//runDeterministicGenerationAndSquash_10000_deps2_b10_s10000_true_PersistentStorage
//ended(numberOfSquashes=2, numberOfBlocks=1000, numberOfGeneratedTx=10000)
//RAW STORAGE REQUIREMENTS: after: 4194320bytes, max: 4194320bytes
//PERSISTED TRANSACTION COUNT: after: 8836txs, max: 9647txs
//runDeterministicGenerationAndSquash_100_deps4_b10_s1_true_PersistentStorage
//ended(numberOfSquashes=11, numberOfBlocks=10, numberOfGeneratedTx=100)
//RAW STORAGE REQUIREMENTS: after: 2097168bytes, max: 2098544bytes
//PERSISTED TRANSACTION COUNT: after: 91txs, max: 92txs
//runDeterministicGenerationAndSquash_100_deps4_b10_s10_true_PersistentStorage
//ended(numberOfSquashes=11, numberOfBlocks=10, numberOfGeneratedTx=100)
//RAW STORAGE REQUIREMENTS: after: 2097168bytes, max: 2098544bytes
//PERSISTED TRANSACTION COUNT: after: 91txs, max: 92txs
//runDeterministicGenerationAndSquash_100_deps4_b10_s100_true_PersistentStorage
//ended(numberOfSquashes=2, numberOfBlocks=10, numberOfGeneratedTx=100)
//RAW STORAGE REQUIREMENTS: after: 2097168bytes, max: 2098544bytes
//PERSISTED TRANSACTION COUNT: after: 91txs, max: 98txs
//runDeterministicGenerationAndSquash_100_deps4_b10_s1000_true_PersistentStorage
//ended(numberOfSquashes=1, numberOfBlocks=10, numberOfGeneratedTx=100)
//RAW STORAGE REQUIREMENTS: after: 2097168bytes, max: 2098544bytes
//PERSISTED TRANSACTION COUNT: after: 91txs, max: 98txs
//runDeterministicGenerationAndSquash_100_deps4_b10_s10000_true_PersistentStorage
//ended(numberOfSquashes=1, numberOfBlocks=10, numberOfGeneratedTx=100)
//RAW STORAGE REQUIREMENTS: after: 2097168bytes, max: 2098544bytes
//PERSISTED TRANSACTION COUNT: after: 91txs, max: 98txs
//runDeterministicGenerationAndSquash_1000_deps4_b10_s1_true_PersistentStorage
//ended(numberOfSquashes=101, numberOfBlocks=100, numberOfGeneratedTx=1000)
//RAW STORAGE REQUIREMENTS: after: 2097168bytes, max: 2098701bytes
//PERSISTED TRANSACTION COUNT: after: 887txs, max: 889txs
//runDeterministicGenerationAndSquash_1000_deps4_b10_s10_true_PersistentStorage
//ended(numberOfSquashes=101, numberOfBlocks=100, numberOfGeneratedTx=1000)
//RAW STORAGE REQUIREMENTS: after: 2097168bytes, max: 2098701bytes
//PERSISTED TRANSACTION COUNT: after: 887txs, max: 889txs
//runDeterministicGenerationAndSquash_1000_deps4_b10_s100_true_PersistentStorage
//ended(numberOfSquashes=11, numberOfBlocks=100, numberOfGeneratedTx=1000)
//RAW STORAGE REQUIREMENTS: after: 2097168bytes, max: 2098701bytes
//PERSISTED TRANSACTION COUNT: after: 887txs, max: 894txs
//runDeterministicGenerationAndSquash_1000_deps4_b10_s1000_true_PersistentStorage
//ended(numberOfSquashes=2, numberOfBlocks=100, numberOfGeneratedTx=1000)
//RAW STORAGE REQUIREMENTS: after: 2097168bytes, max: 2098701bytes
//PERSISTED TRANSACTION COUNT: after: 887txs, max: 967txs
//runDeterministicGenerationAndSquash_1000_deps4_b10_s10000_true_PersistentStorage
//ended(numberOfSquashes=1, numberOfBlocks=100, numberOfGeneratedTx=1000)
//RAW STORAGE REQUIREMENTS: after: 2097168bytes, max: 2098701bytes
//PERSISTED TRANSACTION COUNT: after: 887txs, max: 967txs
//=== Printing ------- Calls ===
//app verify - 7749730 - av: 0.0000005s - max: 0.6442054s - min: 0.0000003s
//persist new block - 7181100 - av: 0.0000206s - max: 0.6624506s - min: 0.0000033s
//squash verify - 7185645 - av: 0.0000694s - max: 0.6966120s - min: 0.0000022s
//runDeterministicGenerationAndSquash_10000_deps0_b10_s1000_true_PersistentStorage - only squash - 1111 - av: 0.0001429s - max: 0.0002758s - min: 0.0000527s
//runDeterministicGenerationAndSquash_10000_deps0_b10_s10_true_PersistentStorage - only squash - 101101 - av: 0.0001432s - max: 0.6462968s - min: 0.0000424s
//runDeterministicGenerationAndSquash_10000_deps0_b10_s1_true_PersistentStorage - only squash - 101101 - av: 0.0001457s - max: 0.6450779s - min: 0.0000423s
//runDeterministicGenerationAndSquash_10000_deps0_b10_s100_true_PersistentStorage - only squash - 10201 - av: 0.0001632s - max: 0.6415566s - min: 0.0000445s
//runDeterministicGenerationAndSquash_10000_deps2_b10_s1_true_PersistentStorage - only squash - 101101 - av: 0.0001667s - max: 0.6635854s - min: 0.0000465s
//runDeterministicGenerationAndSquash_10000_deps2_b10_s10_true_PersistentStorage - only squash - 101101 - av: 0.0001678s - max: 0.6595880s - min: 0.0000468s
//runDeterministicGenerationAndSquash_10000_deps0_b10_s10000_true_PersistentStorage - only squash - 202 - av: 0.0001745s - max: 0.0002868s - min: 0.0000556s
//runDeterministicGenerationAndSquash_1000_deps4_b10_s10_true_PersistentStorage - only squash - 10201 - av: 0.0001873s - max: 0.6577117s - min: 0.0000446s
//runDeterministicGenerationAndSquash_10000_deps4_b10_s10_true_PersistentStorage - only squash - 101101 - av: 0.0001887s - max: 0.1396501s - min: 0.0000453s
//runDeterministicGenerationAndSquash_10000_deps4_b10_s1_true_PersistentStorage - only squash - 101101 - av: 0.0001897s - max: 0.1428071s - min: 0.0000450s
//runDeterministicGenerationAndSquash_1000_deps4_b10_s1_true_PersistentStorage - only squash - 10201 - av: 0.0001912s - max: 0.6456970s - min: 0.0000442s
//runDeterministicGenerationAndSquash_100_deps4_b10_s1_true_PersistentStorage - only squash - 1111 - av: 0.0001953s - max: 0.0004364s - min: 0.0000453s
//runDeterministicGenerationAndSquash_100_deps4_b10_s10_true_PersistentStorage - only squash - 1111 - av: 0.0001954s - max: 0.0003564s - min: 0.0000453s
//runDeterministicGenerationAndSquash_10000_deps4_b1_s1_true_PersistentStorage - only squash - 1010101 - av: 0.0002320s - max: 0.4924628s - min: 0.0000149s
//runDeterministicGenerationAndSquash_10000_deps10_b10_s10_true_PersistentStorage - only squash - 101101 - av: 0.0002376s - max: 0.6386300s - min: 0.0000425s
//runDeterministicGenerationAndSquash_10000_deps10_b10_s1_true_PersistentStorage - only squash - 101101 - av: 0.0002382s - max: 0.5301182s - min: 0.0000429s
//runDeterministicGenerationAndSquash_100_deps4_b10_s100_true_PersistentStorage - only squash - 202 - av: 0.0002647s - max: 0.0003798s - min: 0.0002252s
//runDeterministicGenerationAndSquash_10000_deps4_b1_s10_true_PersistentStorage - only squash - 101101 - av: 0.0002952s - max: 0.3875494s - min: 0.0000157s
//runDeterministicGenerationAndSquash_10000_deps2_b10_s100_true_PersistentStorage - only squash - 10201 - av: 0.0003077s - max: 0.6581487s - min: 0.0002250s
//runDeterministicGenerationAndSquash_10000_deps4_b10_s100_true_PersistentStorage - only squash - 10201 - av: 0.0003321s - max: 0.2210754s - min: 0.0002242s
//runDeterministicGenerationAndSquash_1000_deps4_b10_s100_true_PersistentStorage - only squash - 1111 - av: 0.0003601s - max: 0.6460609s - min: 0.0002244s
//runDeterministicGenerationAndSquash_10000_deps10_b10_s100_true_PersistentStorage - only squash - 10201 - av: 0.0004036s - max: 0.6604319s - min: 0.0002258s
//runDeterministicGenerationAndSquash_100_deps4_b10_s1000_true_PersistentStorage - only squash - 101 - av: 0.0004796s - max: 0.0005845s - min: 0.0004673s
//runDeterministicGenerationAndSquash_100_deps4_b10_s10000_true_PersistentStorage - only squash - 101 - av: 0.0004891s - max: 0.0005535s - min: 0.0004692s
//runDeterministicGenerationAndSquash_10000_deps4_b1_s100_true_PersistentStorage - only squash - 10201 - av: 0.0006430s - max: 0.4450387s - min: 0.0002573s
//runDeterministicGenerationAndSquash_10000_deps4_b100_s10_true_PersistentStorage - only squash - 10201 - av: 0.0006739s - max: 0.0547501s - min: 0.0002299s
//runDeterministicGenerationAndSquash_10000_deps4_b100_s100_true_PersistentStorage - only squash - 10201 - av: 0.0006898s - max: 0.0532915s - min: 0.0002291s
//runDeterministicGenerationAndSquash_10000_deps4_b100_s1_true_PersistentStorage - only squash - 10201 - av: 0.0007068s - max: 0.0527651s - min: 0.0002294s
//runDeterministicGenerationAndSquash_1000_deps4_b10_s1000_true_PersistentStorage - only squash - 202 - av: 0.0009903s - max: 0.6434652s - min: 0.0002338s
//runDeterministicGenerationAndSquash_10000_deps4_b10_s1000_true_PersistentStorage - only squash - 1111 - av: 0.0013917s - max: 0.0083797s - min: 0.0002343s
//runDeterministicGenerationAndSquash_10000_deps10_b10_s1000_true_PersistentStorage - only squash - 1111 - av: 0.0015789s - max: 0.6730479s - min: 0.0002348s
//runDeterministicGenerationAndSquash_10000_deps2_b10_s1000_true_PersistentStorage - only squash - 1111 - av: 0.0017742s - max: 0.6533093s - min: 0.0002338s
//runDeterministicGenerationAndSquash_10000_deps4_b100_s1000_true_PersistentStorage - only squash - 1111 - av: 0.0019154s - max: 0.0652692s - min: 0.0002329s
//introduceChanges - 2038584 - av: 0.0020606s - max: 0.6917213s - min: 0.0000010s
//runDeterministicGenerationAndSquash_1000_deps4_b10_s10000_true_PersistentStorage - only squash - 101 - av: 0.0026700s - max: 0.6416345s - min: 0.0025730s
//runDeterministicGenerationAndSquash_10000_deps4_b1_s1000_true_PersistentStorage - only squash - 1111 - av: 0.0033300s - max: 0.6928639s - min: 0.0002425s
//runDeterministicGenerationAndSquash_10000_deps4_b1000_s1000_true_PersistentStorage - only squash - 1111 - av: 0.0054301s - max: 0.0393900s - min: 0.0002485s
//runDeterministicGenerationAndSquash_10000_deps4_b1000_s10_true_PersistentStorage - only squash - 1111 - av: 0.0054382s - max: 0.0337554s - min: 0.0002513s
//runDeterministicGenerationAndSquash_10000_deps4_b1000_s1_true_PersistentStorage - only squash - 1111 - av: 0.0055287s - max: 1.0380691s - min: 0.0002525s
//runDeterministicGenerationAndSquash_10000_deps4_b1000_s100_true_PersistentStorage - only squash - 1111 - av: 0.0057920s - max: 0.0280117s - min: 0.0002509s
//runDeterministicGenerationAndSquash_10000_deps4_b10_s10000_true_PersistentStorage - only squash - 202 - av: 0.0087212s - max: 0.0296630s - min: 0.0002466s
//runDeterministicGenerationAndSquash_10000_deps10_b10_s10000_true_PersistentStorage - only squash - 202 - av: 0.0088763s - max: 0.6829534s - min: 0.0002494s
//runDeterministicGenerationAndSquash_10000_deps2_b10_s10000_true_PersistentStorage - only squash - 202 - av: 0.0089419s - max: 0.6669614s - min: 0.0002474s
//runDeterministicGenerationAndSquash_10000_deps4_b100_s10000_true_PersistentStorage - only squash - 202 - av: 0.0097986s - max: 0.0302814s - min: 0.0002479s
//runDeterministicGenerationAndSquash_10000_deps4_b1000_s10000_true_PersistentStorage - only squash - 202 - av: 0.0168867s - max: 0.0723569s - min: 0.0002509s
//runDeterministicGenerationAndSquash_10000_deps4_b1_s10000_true_PersistentStorage - only squash - 202 - av: 0.0187817s - max: 0.0654917s - min: 0.0002491s
//runDeterministicGenerationAndSquash_100_deps4_b10_s1000_true_PersistentStorage - 101 - av: 0.0472200s - max: 0.6958923s - min: 0.0465119s
//runDeterministicGenerationAndSquash_100_deps4_b10_s100_true_PersistentStorage - 101 - av: 0.0472849s - max: 0.6965339s - min: 0.0465136s
//runDeterministicGenerationAndSquash_100_deps4_b10_s1_true_PersistentStorage - 101 - av: 0.0478319s - max: 0.7021539s - min: 0.0470375s
//runDeterministicGenerationAndSquash_100_deps4_b10_s10_true_PersistentStorage - 101 - av: 0.0480099s - max: 0.6963111s - min: 0.0471365s
//runDeterministicGenerationAndSquash_100_deps4_b10_s10000_true_PersistentStorage - 101 - av: 0.3713744s - max: 0.6966642s - min: 0.0466830s
//runDeterministicGenerationAndSquash_1000_deps4_b10_s10000_true_PersistentStorage - 101 - av: 0.4919165s - max: 1.1356031s - min: 0.4683867s
//runDeterministicGenerationAndSquash_1000_deps4_b10_s1000_true_PersistentStorage - 101 - av: 0.5132390s - max: 1.1400289s - min: 0.4693188s
//runDeterministicGenerationAndSquash_1000_deps4_b10_s100_true_PersistentStorage - 101 - av: 0.6331808s - max: 1.1266791s - min: 0.4683697s
//runDeterministicGenerationAndSquash_1000_deps4_b10_s10_true_PersistentStorage - 101 - av: 0.8029197s - max: 1.1428025s - min: 0.4716926s
//runDeterministicGenerationAndSquash_1000_deps4_b10_s1_true_PersistentStorage - 101 - av: 0.8061221s - max: 1.1413548s - min: 0.4722573s
//runDeterministicGenerationAndSquash_10000_deps4_b10_s100_true_PersistentStorage - 101 - av: 4.7710626s - max: 4.9744700s - min: 4.6927772s
//runDeterministicGenerationAndSquash_10000_deps4_b10_s1000_true_PersistentStorage - 101 - av: 4.8032222s - max: 5.0172924s - min: 4.7438599s
//runDeterministicGenerationAndSquash_10000_deps4_b100_s1000_true_PersistentStorage - 101 - av: 4.8802767s - max: 4.9951335s - min: 4.8359068s
//runDeterministicGenerationAndSquash_10000_deps4_b100_s10_true_PersistentStorage - 101 - av: 4.8903499s - max: 5.0106383s - min: 4.8310416s
//runDeterministicGenerationAndSquash_10000_deps4_b100_s100_true_PersistentStorage - 101 - av: 4.9061742s - max: 5.0218720s - min: 4.8435676s
//runDeterministicGenerationAndSquash_10000_deps4_b100_s1_true_PersistentStorage - 101 - av: 4.9067035s - max: 5.0214105s - min: 4.8366055s
//runDeterministicGenerationAndSquash_10000_deps4_b100_s10000_true_PersistentStorage - 101 - av: 4.9383904s - max: 5.0779885s - min: 4.8932670s
//runDeterministicGenerationAndSquash_10000_deps4_b10_s10_true_PersistentStorage - 101 - av: 5.0321779s - max: 5.2308848s - min: 4.9593340s
//runDeterministicGenerationAndSquash_10000_deps4_b10_s1_true_PersistentStorage - 101 - av: 5.0417721s - max: 5.3012782s - min: 4.9858973s
//runDeterministicGenerationAndSquash_10000_deps4_b10_s10000_true_PersistentStorage - 101 - av: 5.0531502s - max: 5.4077017s - min: 4.9822583s
//runDeterministicGenerationAndSquash_10000_deps0_b10_s1000_true_PersistentStorage - 101 - av: 5.1465172s - max: 5.3507605s - min: 4.6396261s
//runDeterministicGenerationAndSquash_10000_deps4_b1_s100_true_PersistentStorage - 101 - av: 5.2485061s - max: 5.6625145s - min: 5.1585597s
//runDeterministicGenerationAndSquash_10000_deps4_b1_s1000_true_PersistentStorage - 101 - av: 5.2809830s - max: 6.0046561s - min: 5.1964060s
//runDeterministicGenerationAndSquash_10000_deps0_b10_s10_true_PersistentStorage - 101 - av: 5.2851929s - max: 5.4121627s - min: 4.6376020s
//runDeterministicGenerationAndSquash_10000_deps0_b10_s100_true_PersistentStorage - 101 - av: 5.2876164s - max: 5.4254938s - min: 4.6355417s
//runDeterministicGenerationAndSquash_10000_deps0_b10_s1_true_PersistentStorage - 101 - av: 5.3001419s - max: 5.4644036s - min: 4.6323870s
//runDeterministicGenerationAndSquash_10000_deps0_b10_s10000_true_PersistentStorage - 101 - av: 5.3225724s - max: 5.4328701s - min: 4.6201163s
//runDeterministicGenerationAndSquash_10000_deps10_b10_s100_true_PersistentStorage - 101 - av: 5.4832297s - max: 6.3575479s - min: 5.3843942s
//runDeterministicGenerationAndSquash_10000_deps2_b10_s10_true_PersistentStorage - 101 - av: 5.5166542s - max: 6.2523237s - min: 5.3664927s
//runDeterministicGenerationAndSquash_10000_deps10_b10_s1000_true_PersistentStorage - 101 - av: 5.5236975s - max: 6.1379854s - min: 5.3285899s
//runDeterministicGenerationAndSquash_10000_deps10_b10_s1_true_PersistentStorage - 101 - av: 5.5833698s - max: 6.0987892s - min: 5.4749556s
//runDeterministicGenerationAndSquash_10000_deps2_b10_s100_true_PersistentStorage - 101 - av: 5.7294582s - max: 6.0952966s - min: 5.3576792s
//runDeterministicGenerationAndSquash_10000_deps2_b10_s1000_true_PersistentStorage - 101 - av: 5.7594225s - max: 6.1297877s - min: 5.3738274s
//runDeterministicGenerationAndSquash_10000_deps2_b10_s1_true_PersistentStorage - 101 - av: 5.7882063s - max: 6.1510279s - min: 5.3844354s
//runDeterministicGenerationAndSquash_10000_deps4_b1000_s100_true_PersistentStorage - 101 - av: 6.0697887s - max: 6.2143377s - min: 6.0303822s
//runDeterministicGenerationAndSquash_10000_deps4_b1000_s1000_true_PersistentStorage - 101 - av: 6.0973110s - max: 6.1787774s - min: 6.0246792s
//runDeterministicGenerationAndSquash_10000_deps4_b1000_s10_true_PersistentStorage - 101 - av: 6.1080773s - max: 6.2471616s - min: 6.0554096s
//runDeterministicGenerationAndSquash_10000_deps4_b1000_s10000_true_PersistentStorage - 101 - av: 6.1324314s - max: 6.2451730s - min: 6.0717987s
//runDeterministicGenerationAndSquash_10000_deps4_b1000_s1_true_PersistentStorage - 101 - av: 6.1470764s - max: 7.9612107s - min: 6.0163197s
//runDeterministicGenerationAndSquash_10000_deps4_b1_s10_true_PersistentStorage - 101 - av: 6.1973829s - max: 6.5886191s - min: 6.1077039s
//runDeterministicGenerationAndSquash_10000_deps2_b10_s10000_true_PersistentStorage - 101 - av: 6.3142828s - max: 6.4929234s - min: 5.6631350s
//runDeterministicGenerationAndSquash_10000_deps10_b10_s10000_true_PersistentStorage - 101 - av: 6.3356703s - max: 7.1777229s - min: 5.6894145s
//runDeterministicGenerationAndSquash_10000_deps4_b1_s1_true_PersistentStorage - 101 - av: 7.1685737s - max: 7.6272251s - min: 7.0748307s
//runDeterministicGenerationAndSquash_10000_deps10_b10_s10_true_PersistentStorage - 101 - av: 7.1815727s - max: 7.4662696s - min: 5.4546204s
//runDeterministicGenerationAndSquash_10000_deps4_b1_s10000_true_PersistentStorage - 101 - av: 8.2494731s - max: 8.7813878s - min: 7.9392016s
//=== End End EndEnd End End ===
//=== Printing ------- Calls ===
//runDeterministicGenerationAndSquash_10000_deps4_b1_s1_true_PersistentStorage(101)
//7.6272263 \\ 7.1708012 \\ 7.1186989 \\ 7.0842793 \\ 7.1508108 \\ 7.1625121 \\ 7.1371062 \\ 7.2665712 \\ 7.0936420 \\ 7.1798094 \\ 7.1227213 \\ 7.1177602 \\ 7.1892473 \\ 7.1253272 \\ 7.3399395 \\ 7.1460556 \\ 7.3582515 \\ 7.0935652 \\ 7.1219919 \\ 7.1499553 \\ 7.1045979 \\ 7.0824034 \\ 7.2463521 \\ 7.1945959 \\ 7.1838167 \\ 7.3329833 \\ 7.3046130 \\ 7.1275769 \\ 7.1059128 \\ 7.1781696 \\ 7.1883216 \\ 7.1261184 \\ 7.2081751 \\ 7.3515476 \\ 7.1585276 \\ 7.1301125 \\ 7.0748319 \\ 7.1438884 \\ 7.1171730 \\ 7.1498915 \\ 7.1633199 \\ 7.1975901 \\ 7.1405787 \\ 7.1194007 \\ 7.1592293 \\ 7.1029685 \\ 7.2208184 \\ 7.1807156 \\ 7.1937854 \\ 7.1311266 \\ 7.1651785 \\ 7.1808765 \\ 7.1253733 \\ 7.2767381 \\ 7.2215486 \\ 7.1220850 \\ 7.1546268 \\ 7.2469674 \\ 7.1950784 \\ 7.1163358 \\ 7.2645365 \\ 7.1513482 \\ 7.1544401 \\ 7.2316296 \\ 7.1215831 \\ 7.1155001 \\ 7.1170801 \\ 7.2415177 \\ 7.3450543 \\ 7.2162557 \\ 7.1883028 \\ 7.2835588 \\ 7.1813697 \\ 7.1576728 \\ 7.1265633 \\ 7.1518505 \\ 7.2957782 \\ 7.2378590 \\ 7.1212408 \\ 7.1480475 \\ 7.1706936 \\ 7.1851507 \\ 7.1687870 \\ 7.1265260 \\ 7.1199394 \\ 7.1194713 \\ 7.1961036 \\ 7.1959090 \\ 7.1524816 \\ 7.1444600 \\ 7.1733881 \\ 7.1071670 \\ 7.1712342 \\ 7.1169944 \\ 7.3211005 \\ 7.1231802 \\ 7.1438108 \\ 7.1675325 \\ 7.1429885 \\ 7.1838374 \\
//runDeterministicGenerationAndSquash_100_deps4_b10_s10000_true_PersistentStorage(101)
//0.0476302 \\ 0.0472904 \\ 0.0473650 \\ 0.0475486 \\ 0.0482269 \\ 0.0478536 \\ 0.0479692 \\ 0.0473475 \\ 0.0474399 \\ 0.0471765 \\ 0.0472526 \\ 0.0473489 \\ 0.0473766 \\ 0.0472204 \\ 0.0471250 \\ 0.0474596 \\ 0.0470707 \\ 0.0473642 \\ 0.0470400 \\ 0.6966654 \\ 0.0473403 \\ 0.0475736 \\ 0.0472544 \\ 0.0475586 \\ 0.0475605 \\ 0.0643251 \\ 0.0473718 \\ 0.0473116 \\ 0.0474948 \\ 0.0471610 \\ 0.0475805 \\ 0.0471503 \\ 0.0474015 \\ 0.0475378 \\ 0.0470702 \\ 0.0477144 \\ 0.0472453 \\ 0.0472223 \\ 0.0475449 \\ 0.0475285 \\ 0.0471678 \\ 0.0472063 \\ 0.0478188 \\ 0.0470919 \\ 0.0472947 \\ 0.0470828 \\ 0.0472047 \\ 0.0472522 \\ 0.0472460 \\ 0.0491036 \\ 0.0473019 \\ 0.0473335 \\ 0.0475889 \\ 0.0483480 \\ 0.0472508 \\ 0.0473527 \\ 0.0485619 \\ 0.0471872 \\ 0.0472331 \\ 0.0479055 \\ 0.0486588 \\ 0.0475211 \\ 0.0479319 \\ 0.0471785 \\ 0.0480270 \\ 0.0474445 \\ 0.0472032 \\ 0.0472188 \\ 0.0470608 \\ 0.0471403 \\ 0.0470881 \\ 0.0475711 \\ 0.0471974 \\ 0.0474431 \\ 0.0471816 \\ 0.0476235 \\ 0.0481591 \\ 0.0474506 \\ 0.0474987 \\ 0.0469603 \\ 0.0476575 \\ 0.0474273 \\ 0.0472453 \\ 0.0473112 \\ 0.0490516 \\ 0.0477078 \\ 0.0466841 \\ 0.0471784 \\ 0.0475946 \\ 0.0476748 \\ 0.0473964 \\ 0.0474469 \\ 0.0481283 \\ 0.0486423 \\ 0.0474317 \\ 0.0476223 \\ 0.0479948 \\ 0.0476652 \\ 0.0470701 \\ 0.0471265 \\ 0.6954799 \\
//runDeterministicGenerationAndSquash_10000_deps4_b1000_s1_true_PersistentStorage(101)
//7.9612299 \\ 6.4465256 \\ 6.2267108 \\ 6.2905225 \\ 6.1575685 \\ 6.1838220 \\ 6.1450164 \\ 6.1459972 \\ 6.1697968 \\ 6.1337288 \\ 6.0692762 \\ 6.1765439 \\ 6.0826065 \\ 6.0524551 \\ 6.0770694 \\ 6.0665056 \\ 6.1150018 \\ 6.0613172 \\ 6.0887320 \\ 6.1547954 \\ 6.1616019 \\ 6.0163220 \\ 6.0459200 \\ 6.0522928 \\ 6.0705012 \\ 6.1217316 \\ 6.0493026 \\ 6.0522426 \\ 6.0803688 \\ 6.1179175 \\ 6.0729862 \\ 6.0374574 \\ 6.0992509 \\ 6.1525852 \\ 6.0591112 \\ 6.0727555 \\ 6.0902129 \\ 6.1229889 \\ 6.1357240 \\ 6.0527935 \\ 6.1592501 \\ 6.1136908 \\ 6.1000330 \\ 6.1606329 \\ 6.1209601 \\ 6.1528328 \\ 6.0654960 \\ 6.0942910 \\ 6.0985323 \\ 6.1545366 \\ 6.0872438 \\ 6.1967189 \\ 6.1461350 \\ 6.1833099 \\ 6.1337820 \\ 6.2095830 \\ 6.1907366 \\ 6.1533328 \\ 6.1248174 \\ 6.1295279 \\ 6.1275371 \\ 6.1824187 \\ 6.1049813 \\ 6.1594296 \\ 6.1486428 \\ 6.0870233 \\ 6.0905991 \\ 6.1318063 \\ 6.1437457 \\ 6.1397769 \\ 6.1493489 \\ 6.2008439 \\ 6.1066427 \\ 6.0892092 \\ 6.1329986 \\ 6.1962885 \\ 6.1508872 \\ 6.0837044 \\ 6.2029896 \\ 6.1066300 \\ 6.1111268 \\ 6.1648755 \\ 6.1499963 \\ 6.1196851 \\ 6.1374516 \\ 6.1253089 \\ 6.0931255 \\ 6.1555801 \\ 6.1144314 \\ 6.1062355 \\ 6.1313959 \\ 6.2475742 \\ 6.1389593 \\ 6.1606923 \\ 6.1596389 \\ 6.1464302 \\ 6.1826094 \\ 6.1175199 \\ 6.2047474 \\ 6.1372207 \\ 6.1386655 \\
//runDeterministicGenerationAndSquash_10000_deps2_b10_s100_true_PersistentStorage(101)
//5.4199427 \\ 6.0178156 \\ 5.4041130 \\ 5.4113272 \\ 5.3691427 \\ 5.3917220 \\ 6.0345703 \\ 5.3898589 \\ 5.3871296 \\ 5.4062910 \\ 5.3865219 \\ 5.3877008 \\ 6.0142487 \\ 5.3720047 \\ 5.3607555 \\ 5.4073080 \\ 5.3576804 \\ 6.0126287 \\ 5.4217884 \\ 5.3716223 \\ 5.3868558 \\ 5.4137678 \\ 5.3792136 \\ 6.0279333 \\ 5.4053605 \\ 5.3961758 \\ 5.3937522 \\ 5.3978466 \\ 6.0417764 \\ 5.4428526 \\ 5.4362093 \\ 5.4200894 \\ 5.4242105 \\ 5.3610489 \\ 6.0575096 \\ 5.3866995 \\ 5.4213142 \\ 5.3729158 \\ 5.3892627 \\ 6.0707373 \\ 5.3945655 \\ 5.4054762 \\ 5.3988982 \\ 5.3792862 \\ 5.3875353 \\ 6.0605475 \\ 5.3763227 \\ 5.3967250 \\ 5.3781277 \\ 5.3894583 \\ 6.0498581 \\ 5.3960806 \\ 5.4101351 \\ 5.3609011 \\ 5.3624159 \\ 5.5529298 \\ 6.0135592 \\ 5.3990257 \\ 5.3667697 \\ 5.3861344 \\ 5.3852569 \\ 6.0137391 \\ 5.3967365 \\ 5.4225023 \\ 5.4024258 \\ 5.4227574 \\ 5.3735808 \\ 6.0716852 \\ 5.3724000 \\ 5.4186133 \\ 5.3962544 \\ 5.3828269 \\ 6.0275846 \\ 5.4123821 \\ 5.4102230 \\ 5.3948280 \\ 5.4307613 \\ 5.3721547 \\ 6.0952978 \\ 5.4225984 \\ 5.4206468 \\ 5.4218356 \\ 5.4198558 \\ 5.9918937 \\ 5.3853501 \\ 5.3908478 \\ 5.4369890 \\ 5.4071620 \\ 5.3600845 \\ 6.0169459 \\ 5.4992507 \\ 5.3686125 \\ 5.3660568 \\ 5.3800847 \\ 5.9815684 \\ 5.4340437 \\ 5.3797798 \\ 5.3870067 \\ 5.4329180 \\ 5.4021812 \\ 6.0422895 \\
//runDeterministicGenerationAndSquash_10000_deps0_b10_s10000_true_PersistentStorage(101)
//5.2833669 \\ 5.3038859 \\ 5.3288179 \\ 5.2968842 \\ 5.2786248 \\ 5.2805742 \\ 5.2981914 \\ 5.2885624 \\ 5.2952237 \\ 5.2848466 \\ 5.3258828 \\ 5.3929908 \\ 5.3521300 \\ 5.2881394 \\ 4.6718483 \\ 5.4328710 \\ 5.2930690 \\ 5.3051520 \\ 5.2750772 \\ 5.3080291 \\ 5.2737039 \\ 5.2776792 \\ 5.2916415 \\ 5.2867864 \\ 5.3264103 \\ 5.2612930 \\ 5.3129531 \\ 5.3403513 \\ 5.3016281 \\ 5.2879581 \\ 4.6401744 \\ 5.2884909 \\ 5.2782114 \\ 5.2818165 \\ 5.2862410 \\ 5.2967332 \\ 5.2933156 \\ 5.2881550 \\ 5.2872467 \\ 5.3836068 \\ 5.2892618 \\ 5.2799751 \\ 5.2837216 \\ 5.2835253 \\ 5.3151119 \\ 5.2965688 \\ 4.6201174 \\ 5.2875489 \\ 5.2716219 \\ 5.3178458 \\ 5.2896598 \\ 5.3193063 \\ 5.2706234 \\ 5.2757344 \\ 5.2872819 \\ 5.2866962 \\ 5.2912758 \\ 5.3075669 \\ 5.3341065 \\ 5.2835816 \\ 5.2908342 \\ 5.2912861 \\ 4.6346658 \\ 5.3619904 \\ 5.2856742 \\ 5.2701091 \\ 5.3013646 \\ 5.2795142 \\ 5.3250735 \\ 5.2599872 \\ 5.2858979 \\ 5.2920036 \\ 5.2915748 \\ 5.2996767 \\ 5.2920721 \\ 5.3067909 \\ 5.2921997 \\ 5.2837493 \\ 4.6573253 \\ 5.2936764 \\ 5.2889607 \\ 5.2876808 \\ 5.2959032 \\ 5.2933292 \\ 5.3296778 \\ 5.3552346 \\ 5.3034777 \\ 5.3528851 \\ 5.2984596 \\ 5.2886393 \\ 5.3089374 \\ 5.2731963 \\ 5.2733894 \\ 5.2977328 \\ 4.6212497 \\ 5.3046742 \\ 5.2898318 \\ 5.2741141 \\ 5.2710950 \\ 5.3952751 \\ 5.3192294 \\
//runDeterministicGenerationAndSquash_100_deps4_b10_s100_true_PersistentStorage(101)
//0.0476086 \\ 0.0474498 \\ 0.0472244 \\ 0.0479154 \\ 0.0480687 \\ 0.0485708 \\ 0.0475212 \\ 0.0638341 \\ 0.0476611 \\ 0.0477300 \\ 0.0477589 \\ 0.0477161 \\ 0.0475370 \\ 0.0481046 \\ 0.0475541 \\ 0.0469982 \\ 0.0478040 \\ 0.0474835 \\ 0.0477736 \\ 0.0475626 \\ 0.0474866 \\ 0.0471289 \\ 0.0475107 \\ 0.0625597 \\ 0.0471860 \\ 0.0475941 \\ 0.0471898 \\ 0.0477542 \\ 0.0470474 \\ 0.0472550 \\ 0.0467787 \\ 0.0477183 \\ 0.0488933 \\ 0.0486798 \\ 0.0474895 \\ 0.0474883 \\ 0.0474918 \\ 0.0475187 \\ 0.0473024 \\ 0.0489514 \\ 0.0473349 \\ 0.0471624 \\ 0.0471385 \\ 0.0472529 \\ 0.0472756 \\ 0.0474856 \\ 0.0473505 \\ 0.0473979 \\ 0.0475701 \\ 0.0474819 \\ 0.0475082 \\ 0.0473912 \\ 0.0475289 \\ 0.0474062 \\ 0.0476313 \\ 0.0477272 \\ 0.0475021 \\ 0.0475620 \\ 0.0478868 \\ 0.6965357 \\ 0.0474222 \\ 0.0472090 \\ 0.0473532 \\ 0.0479842 \\ 0.0472197 \\ 0.0479372 \\ 0.0479001 \\ 0.0470265 \\ 0.0474368 \\ 0.0468391 \\ 0.0474416 \\ 0.0472848 \\ 0.0473073 \\ 0.0479994 \\ 0.0475637 \\ 0.0474780 \\ 0.0471641 \\ 0.0471446 \\ 0.0471819 \\ 0.0473260 \\ 0.0477566 \\ 0.0470586 \\ 0.0478793 \\ 0.0477549 \\ 0.0476120 \\ 0.0468779 \\ 0.0469815 \\ 0.0472316 \\ 0.0468132 \\ 0.0474907 \\ 0.0475334 \\ 0.0476231 \\ 0.0473740 \\ 0.0472194 \\ 0.0474965 \\ 0.0465156 \\ 0.0470681 \\ 0.0483279 \\ 0.0473954 \\ 0.0471776 \\ 0.0472176 \\
//runDeterministicGenerationAndSquash_100_deps4_b10_s1_true_PersistentStorage(101)
//0.0479269 \\ 0.0473411 \\ 0.0478024 \\ 0.0476182 \\ 0.0494839 \\ 0.0487369 \\ 0.0470393 \\ 0.0472477 \\ 0.0474931 \\ 0.0475972 \\ 0.0474166 \\ 0.0475089 \\ 0.0478223 \\ 0.0475693 \\ 0.0476308 \\ 0.0476370 \\ 0.0474013 \\ 0.0474586 \\ 0.0473591 \\ 0.0485695 \\ 0.0476246 \\ 0.0474728 \\ 0.0476923 \\ 0.0473179 \\ 0.0473687 \\ 0.0475369 \\ 0.0474038 \\ 0.0475375 \\ 0.0475075 \\ 0.0478533 \\ 0.0476218 \\ 0.0483952 \\ 0.0639550 \\ 0.0477888 \\ 0.0477522 \\ 0.0476423 \\ 0.7021558 \\ 0.0479416 \\ 0.0477648 \\ 0.0478780 \\ 0.0475389 \\ 0.0475612 \\ 0.0475107 \\ 0.0473133 \\ 0.0474510 \\ 0.0474443 \\ 0.0478483 \\ 0.0475428 \\ 0.0480419 \\ 0.0477739 \\ 0.0474322 \\ 0.0474427 \\ 0.0479124 \\ 0.0473518 \\ 0.0475497 \\ 0.0475510 \\ 0.0475996 \\ 0.0474382 \\ 0.0474218 \\ 0.0477076 \\ 0.0476433 \\ 0.0476314 \\ 0.0475034 \\ 0.0475691 \\ 0.0478009 \\ 0.0476606 \\ 0.0474844 \\ 0.0478740 \\ 0.0477686 \\ 0.0479365 \\ 0.0475467 \\ 0.0487016 \\ 0.0476328 \\ 0.0476282 \\ 0.0478174 \\ 0.0475428 \\ 0.0481089 \\ 0.0476576 \\ 0.0478577 \\ 0.0478782 \\ 0.0474219 \\ 0.0476994 \\ 0.0478444 \\ 0.0478524 \\ 0.0477925 \\ 0.0475200 \\ 0.0486616 \\ 0.0478446 \\ 0.0478724 \\ 0.0474095 \\ 0.0486725 \\ 0.0487090 \\ 0.0475218 \\ 0.0479905 \\ 0.0473898 \\ 0.0479866 \\ 0.0475335 \\ 0.0473740 \\ 0.0478033 \\ 0.0476104 \\ 0.0480294 \\
//runDeterministicGenerationAndSquash_1000_deps4_b10_s10000_true_PersistentStorage(101)
//0.4958415 \\ 0.4730008 \\ 0.4705471 \\ 0.4703799 \\ 1.1149431 \\ 0.4730877 \\ 0.4737630 \\ 0.4765436 \\ 0.4741463 \\ 0.4747056 \\ 0.4750966 \\ 0.4711194 \\ 1.1133218 \\ 0.4757130 \\ 0.4719214 \\ 0.4810111 \\ 0.4740296 \\ 0.4706500 \\ 0.4751883 \\ 0.4699686 \\ 0.4745920 \\ 1.1124781 \\ 0.4703567 \\ 0.4703224 \\ 0.4724414 \\ 0.4762913 \\ 0.4706933 \\ 0.4683876 \\ 0.4740266 \\ 1.1193371 \\ 0.4706039 \\ 0.4715406 \\ 0.4747779 \\ 0.4745526 \\ 0.4720137 \\ 0.4736164 \\ 0.4737602 \\ 1.1179582 \\ 0.4740711 \\ 0.4729362 \\ 0.4808346 \\ 0.4729832 \\ 0.4698220 \\ 0.4697031 \\ 0.4710520 \\ 1.1126586 \\ 0.4732829 \\ 0.4766349 \\ 0.4735236 \\ 0.4727240 \\ 0.4733201 \\ 0.4733929 \\ 0.4715056 \\ 1.1088957 \\ 0.4725336 \\ 0.4710401 \\ 0.4715526 \\ 0.4773082 \\ 0.4772944 \\ 0.4982946 \\ 0.4742994 \\ 0.4734846 \\ 1.1148002 \\ 0.4706171 \\ 0.4751734 \\ 0.4743768 \\ 0.4729571 \\ 0.4733997 \\ 0.4719422 \\ 0.4764124 \\ 1.1155784 \\ 0.4738313 \\ 0.4778414 \\ 0.4717468 \\ 0.4877944 \\ 0.4838580 \\ 0.4747688 \\ 0.4743550 \\ 1.1356041 \\ 0.4738012 \\ 0.4750061 \\ 0.4745982 \\ 0.4730175 \\ 0.4697419 \\ 0.4762562 \\ 0.4743483 \\ 1.1168978 \\ 0.4743267 \\ 0.4721533 \\ 0.4842522 \\ 0.4763042 \\ 0.4729050 \\ 0.4712509 \\ 0.4798405 \\ 0.4705186 \\ 1.1185740 \\ 0.4708889 \\ 0.4736684 \\ 0.4748507 \\ 0.4991021 \\ 0.4771396 \\
//runDeterministicGenerationAndSquash_10000_deps4_b100_s100_true_PersistentStorage(101)
//4.9275959 \\ 4.8812108 \\ 4.8880128 \\ 4.8646030 \\ 4.8773244 \\ 4.9169479 \\ 4.8802868 \\ 4.8892388 \\ 4.8715965 \\ 4.9174970 \\ 4.8886451 \\ 4.8850352 \\ 4.9212862 \\ 4.8797957 \\ 4.9751488 \\ 4.8740134 \\ 4.9312525 \\ 4.9266822 \\ 4.9129429 \\ 4.9088005 \\ 4.8576365 \\ 4.9087632 \\ 4.8930601 \\ 4.9715930 \\ 4.8597675 \\ 4.8789148 \\ 4.8827753 \\ 4.9024648 \\ 4.9253332 \\ 4.9375914 \\ 4.8435692 \\ 4.8876377 \\ 4.9291251 \\ 4.8980898 \\ 4.8579954 \\ 4.8873480 \\ 4.8872458 \\ 4.9535535 \\ 4.8632924 \\ 4.8893507 \\ 4.8761615 \\ 4.8733106 \\ 5.0218737 \\ 4.9097260 \\ 4.8560163 \\ 4.8486075 \\ 4.9030647 \\ 4.8568294 \\ 4.8728833 \\ 4.9254445 \\ 4.8647842 \\ 4.9477947 \\ 4.8797110 \\ 4.8869817 \\ 4.8886413 \\ 4.9169439 \\ 4.9302119 \\ 4.8759334 \\ 4.9023526 \\ 4.8895360 \\ 4.9781499 \\ 4.8767971 \\ 4.9015221 \\ 4.8659816 \\ 4.8927533 \\ 4.9590838 \\ 4.8843989 \\ 4.8911876 \\ 4.8947051 \\ 4.9253553 \\ 4.8650547 \\ 4.9067580 \\ 4.9013260 \\ 4.8639649 \\ 4.9194070 \\ 4.8983862 \\ 4.8732212 \\ 4.9037444 \\ 4.9023301 \\ 4.9343796 \\ 4.8844667 \\ 4.9337312 \\ 4.9005057 \\ 4.9488331 \\ 4.8811944 \\ 4.8711626 \\ 4.8819996 \\ 4.8830216 \\ 4.9470787 \\ 4.8782973 \\ 4.8531461 \\ 4.9196249 \\ 4.9172639 \\ 4.8766027 \\ 4.8927946 \\ 4.8717320 \\ 4.8901330 \\ 4.9290122 \\ 4.8835993 \\ 4.8896332 \\ 4.9197469 \\
//runDeterministicGenerationAndSquash_10000_deps0_b10_s100_true_PersistentStorage(101)
//5.3264325 \\ 5.3102248 \\ 5.3050002 \\ 5.2928076 \\ 5.2938199 \\ 5.2910573 \\ 5.2660168 \\ 5.3342274 \\ 4.6355434 \\ 5.2951290 \\ 5.2796266 \\ 5.2140367 \\ 5.2785450 \\ 5.3010379 \\ 5.3344754 \\ 5.2668048 \\ 5.2739791 \\ 5.3018873 \\ 5.2800853 \\ 5.2593482 \\ 5.2832690 \\ 5.3082780 \\ 5.2974456 \\ 5.2834794 \\ 4.6578392 \\ 5.3195154 \\ 5.2692716 \\ 5.3098515 \\ 5.2483834 \\ 5.2745738 \\ 5.2640587 \\ 5.2612061 \\ 5.2691723 \\ 5.2689711 \\ 5.2636396 \\ 5.3116291 \\ 5.3153333 \\ 5.2510835 \\ 5.2679205 \\ 5.2896041 \\ 4.6631707 \\ 5.2727248 \\ 5.2675688 \\ 5.2753946 \\ 5.2951825 \\ 5.2466088 \\ 5.2635157 \\ 5.3000495 \\ 5.3111071 \\ 5.4254953 \\ 5.2937691 \\ 5.2741150 \\ 5.2860955 \\ 5.2931621 \\ 5.2712676 \\ 5.2763229 \\ 4.6457854 \\ 5.3240478 \\ 5.2728917 \\ 5.3220478 \\ 5.2675485 \\ 5.3133937 \\ 5.2967879 \\ 5.2552168 \\ 5.2661670 \\ 5.3369587 \\ 5.2855183 \\ 5.2857508 \\ 5.2933415 \\ 5.2795056 \\ 5.2884315 \\ 5.2924044 \\ 4.6551772 \\ 5.3250612 \\ 5.2872262 \\ 5.2925438 \\ 5.3207748 \\ 5.2931429 \\ 5.3019100 \\ 5.2572825 \\ 5.3087034 \\ 5.2678960 \\ 5.2747805 \\ 5.2918786 \\ 5.3112450 \\ 5.3136612 \\ 5.2722076 \\ 5.2899481 \\ 4.6632014 \\ 5.3318986 \\ 5.2763490 \\ 5.3304616 \\ 5.2351558 \\ 5.2749119 \\ 5.2981526 \\ 5.2818530 \\ 5.2836046 \\ 5.3091250 \\ 5.2993386 \\ 5.2854002 \\ 5.2837365 \\
//runDeterministicGenerationAndSquash_10000_deps4_b10_s1_true_PersistentStorage(101)
//5.1507919 \\ 5.1166738 \\ 5.0711921 \\ 5.0175062 \\ 5.0010292 \\ 5.0163444 \\ 5.0404382 \\ 5.0442039 \\ 5.0290074 \\ 5.0550356 \\ 5.0169929 \\ 5.0654144 \\ 5.0548960 \\ 5.0269831 \\ 5.0253469 \\ 5.0415120 \\ 5.0961892 \\ 5.0496507 \\ 5.0609480 \\ 5.0194291 \\ 5.2106992 \\ 5.0211305 \\ 5.0979454 \\ 5.0032967 \\ 4.9864402 \\ 5.0731811 \\ 4.9963795 \\ 5.0459416 \\ 5.0647337 \\ 5.0565141 \\ 5.0137404 \\ 5.0630820 \\ 5.0308369 \\ 5.0594855 \\ 5.0488688 \\ 5.0617295 \\ 5.0810485 \\ 5.0519087 \\ 5.0068464 \\ 5.0554585 \\ 5.0526332 \\ 5.0595648 \\ 5.0133799 \\ 5.0543672 \\ 5.1001988 \\ 5.1060679 \\ 5.0241431 \\ 5.1227348 \\ 5.0509509 \\ 5.0742690 \\ 5.0361547 \\ 5.0635182 \\ 5.0495137 \\ 5.0611958 \\ 5.0620273 \\ 5.1481782 \\ 5.0742106 \\ 5.0944662 \\ 5.0837197 \\ 5.0296852 \\ 5.0270725 \\ 5.0824961 \\ 5.0247551 \\ 5.1318835 \\ 5.0367462 \\ 5.2019539 \\ 5.0377739 \\ 5.0054150 \\ 5.0231613 \\ 5.0346401 \\ 5.1006971 \\ 5.0739300 \\ 5.0100025 \\ 5.0635351 \\ 5.0028270 \\ 5.0476177 \\ 5.0234024 \\ 5.0482833 \\ 4.9858986 \\ 5.0274101 \\ 5.1525719 \\ 5.0549201 \\ 5.0727823 \\ 5.0579034 \\ 5.0326542 \\ 5.0946702 \\ 4.9957276 \\ 5.0163962 \\ 5.0452680 \\ 5.0164235 \\ 4.9885265 \\ 5.0058592 \\ 5.0330109 \\ 5.0272935 \\ 5.0139191 \\ 5.0036361 \\ 5.0154753 \\ 5.0410531 \\ 5.3012794 \\ 5.0042417 \\ 4.9993087 \\
//runDeterministicGenerationAndSquash_10000_deps4_b1000_s1000_true_PersistentStorage(101)
//6.0553468 \\ 6.0746825 \\ 6.1272318 \\ 6.0848398 \\ 6.0556372 \\ 6.1003295 \\ 6.0864568 \\ 6.0371189 \\ 6.1079137 \\ 6.0510558 \\ 6.0963447 \\ 6.0700119 \\ 6.0837268 \\ 6.0958913 \\ 6.0629488 \\ 6.0861945 \\ 6.1201203 \\ 6.0569539 \\ 6.0853881 \\ 6.1117379 \\ 6.1179561 \\ 6.1244085 \\ 6.1442643 \\ 6.0898150 \\ 6.0859942 \\ 6.1549232 \\ 6.1052932 \\ 6.1238333 \\ 6.0841460 \\ 6.0497056 \\ 6.1309592 \\ 6.0443295 \\ 6.0927778 \\ 6.0895921 \\ 6.0622821 \\ 6.0665462 \\ 6.1787791 \\ 6.1316316 \\ 6.0460629 \\ 6.1208807 \\ 6.0909692 \\ 6.0552057 \\ 6.0990254 \\ 6.0614323 \\ 6.0720892 \\ 6.0339001 \\ 6.0488586 \\ 6.1275112 \\ 6.0246807 \\ 6.0857175 \\ 6.0992284 \\ 6.0949922 \\ 6.0578951 \\ 6.0857318 \\ 6.0405654 \\ 6.0858681 \\ 6.0715219 \\ 6.0498034 \\ 6.1003821 \\ 6.0692997 \\ 6.0772286 \\ 6.0966162 \\ 6.0467422 \\ 6.0636519 \\ 6.0804131 \\ 6.0917821 \\ 6.1133229 \\ 6.0660584 \\ 6.0509803 \\ 6.1129612 \\ 6.0671294 \\ 6.0837337 \\ 6.1161813 \\ 6.0672112 \\ 6.0363614 \\ 6.1542836 \\ 6.0375636 \\ 6.0671579 \\ 6.1092606 \\ 6.0728418 \\ 6.0598297 \\ 6.0850344 \\ 6.0541711 \\ 6.1036313 \\ 6.1676515 \\ 6.0584039 \\ 6.1118893 \\ 6.1039978 \\ 6.1034540 \\ 6.0800414 \\ 6.0512163 \\ 6.1140289 \\ 6.1654332 \\ 6.0999506 \\ 6.0798552 \\ 6.1302369 \\ 6.0792611 \\ 6.1021103 \\ 6.1247950 \\ 6.1050232 \\ 6.0860906 \\
//runDeterministicGenerationAndSquash_10000_deps4_b1000_s10_true_PersistentStorage(101)
//6.1385474 \\ 6.1705387 \\ 6.1045822 \\ 6.1643654 \\ 6.1827064 \\ 6.0934854 \\ 6.1038916 \\ 6.1306194 \\ 6.1107978 \\ 6.1886666 \\ 6.1377391 \\ 6.0997185 \\ 6.1691490 \\ 6.1140248 \\ 6.1103915 \\ 6.1231900 \\ 6.1373713 \\ 6.1152742 \\ 6.1429552 \\ 6.1163128 \\ 6.1314847 \\ 6.1345099 \\ 6.2063626 \\ 6.1147964 \\ 6.1271448 \\ 6.1103122 \\ 6.1476909 \\ 6.0884186 \\ 6.1003864 \\ 6.1588936 \\ 6.0643688 \\ 6.1856240 \\ 6.1673962 \\ 6.0937465 \\ 6.1065978 \\ 6.1154625 \\ 6.0927724 \\ 6.0868218 \\ 6.0988968 \\ 6.1652030 \\ 6.1346919 \\ 6.1010566 \\ 6.0890631 \\ 6.1482633 \\ 6.0681601 \\ 6.1076397 \\ 6.1103500 \\ 6.0745184 \\ 6.0760584 \\ 6.1290942 \\ 6.1074612 \\ 6.1021064 \\ 6.1382897 \\ 6.1279851 \\ 6.0554122 \\ 6.1146282 \\ 6.1134952 \\ 6.0681624 \\ 6.1257983 \\ 6.1137444 \\ 6.1575783 \\ 6.0675184 \\ 6.1038449 \\ 6.2471641 \\ 6.1273562 \\ 6.0783882 \\ 6.1568987 \\ 6.0934464 \\ 6.1047667 \\ 6.1074445 \\ 6.1486437 \\ 6.0983487 \\ 6.1320651 \\ 6.0828494 \\ 6.1581548 \\ 6.1341962 \\ 6.1050209 \\ 6.1291814 \\ 6.1381661 \\ 6.0877500 \\ 6.1436606 \\ 6.1129946 \\ 6.1101297 \\ 6.2001838 \\ 6.1324595 \\ 6.1195438 \\ 6.1317937 \\ 6.1262192 \\ 6.1509905 \\ 6.1098249 \\ 6.0870797 \\ 6.1571007 \\ 6.0925431 \\ 6.1425702 \\ 6.1315877 \\ 6.1112404 \\ 6.1001794 \\ 6.1124298 \\ 6.0841861 \\ 6.0701127 \\ 6.1322283 \\
//runDeterministicGenerationAndSquash_10000_deps10_b10_s10000_true_PersistentStorage(101)
//6.3549904 \\ 6.3123410 \\ 7.0266010 \\ 5.6985689 \\ 6.3504822 \\ 6.3350378 \\ 6.3480777 \\ 6.3291700 \\ 6.3363858 \\ 5.7920703 \\ 6.3210033 \\ 6.3131562 \\ 6.3255796 \\ 6.3513704 \\ 6.3606875 \\ 5.7308157 \\ 6.3260243 \\ 6.3371024 \\ 6.3361040 \\ 6.3515183 \\ 6.3302765 \\ 6.3358471 \\ 5.6966643 \\ 6.3062633 \\ 6.3427724 \\ 6.3099055 \\ 6.3241744 \\ 6.3428002 \\ 5.7209881 \\ 6.3516854 \\ 6.3706240 \\ 6.3299805 \\ 6.3243972 \\ 6.3508738 \\ 5.7058466 \\ 6.3461538 \\ 6.3203139 \\ 6.3264010 \\ 6.3122938 \\ 6.3733404 \\ 5.6937844 \\ 6.3175176 \\ 6.3259377 \\ 6.3768606 \\ 6.3190689 \\ 6.3319776 \\ 6.3865677 \\ 5.7433987 \\ 6.3279416 \\ 6.3441629 \\ 6.3475172 \\ 6.3659417 \\ 6.3414092 \\ 5.7275771 \\ 6.3108227 \\ 6.3441510 \\ 6.3239253 \\ 6.3440209 \\ 6.3450105 \\ 5.7410766 \\ 6.3173357 \\ 6.3245834 \\ 6.3701035 \\ 6.3312395 \\ 6.3362651 \\ 5.6894160 \\ 6.2877122 \\ 6.3078718 \\ 7.1777246 \\ 6.6433833 \\ 6.6251867 \\ 6.1346923 \\ 6.5133660 \\ 6.4343307 \\ 6.6248968 \\ 6.5516626 \\ 6.5563246 \\ 6.5289980 \\ 5.7659624 \\ 6.3141719 \\ 6.3356879 \\ 6.3510709 \\ 6.3650654 \\ 6.3379388 \\ 5.7412353 \\ 6.3821545 \\ 6.3750400 \\ 6.4078256 \\ 6.3744541 \\ 6.3817390 \\ 5.7439701 \\ 6.3371544 \\ 6.3365175 \\ 6.3058729 \\ 6.3240816 \\ 6.3599402 \\ 5.7078965 \\ 6.3565532 \\ 6.4041525 \\ 6.3147055 \\ 6.3658517 \\
//runDeterministicGenerationAndSquash_10000_deps4_b10_s1000_true_PersistentStorage(101)
//4.8115667 \\ 4.7930241 \\ 4.7942018 \\ 4.7938340 \\ 4.8171517 \\ 4.7862445 \\ 4.7738624 \\ 4.7595309 \\ 4.7756520 \\ 4.7869835 \\ 4.8130538 \\ 4.7748167 \\ 4.7902365 \\ 4.7858093 \\ 4.7826072 \\ 4.7594207 \\ 4.7937777 \\ 4.7504905 \\ 4.7983510 \\ 4.7885591 \\ 4.7626352 \\ 4.7688891 \\ 4.8065370 \\ 4.7734342 \\ 4.7783960 \\ 4.7856976 \\ 4.7902813 \\ 4.7776768 \\ 4.7880962 \\ 4.8166917 \\ 4.7679326 \\ 4.7966903 \\ 4.7838602 \\ 4.8660563 \\ 4.7773439 \\ 4.8316079 \\ 4.8339517 \\ 4.7960866 \\ 4.7739366 \\ 4.7698171 \\ 4.7438618 \\ 4.7483635 \\ 4.7972643 \\ 4.8121454 \\ 4.8442758 \\ 4.8493918 \\ 4.8699995 \\ 4.8465113 \\ 4.7766399 \\ 4.8026930 \\ 4.7901016 \\ 4.8120943 \\ 4.7783793 \\ 4.7945136 \\ 4.7951779 \\ 4.7609105 \\ 4.7635515 \\ 4.7703501 \\ 4.8387856 \\ 4.7457665 \\ 4.7805724 \\ 4.7938466 \\ 4.8699313 \\ 4.7799520 \\ 4.8351775 \\ 4.7996993 \\ 4.7911356 \\ 4.7632787 \\ 4.7747214 \\ 4.7814007 \\ 4.7790944 \\ 5.0163374 \\ 4.7939071 \\ 4.8158698 \\ 4.8445694 \\ 4.8374047 \\ 4.8070452 \\ 5.0172942 \\ 4.8325502 \\ 4.8752089 \\ 4.8421122 \\ 4.8418362 \\ 4.8441824 \\ 4.8562697 \\ 4.8130353 \\ 4.8302217 \\ 4.7980837 \\ 4.8234973 \\ 4.9267135 \\ 4.7786904 \\ 4.7582234 \\ 4.7704105 \\ 4.7517109 \\ 4.7549301 \\ 4.7524548 \\ 4.9006622 \\ 4.7858977 \\ 4.7754803 \\ 4.8296195 \\ 4.8015661 \\ 4.8004178 \\
//runDeterministicGenerationAndSquash_10000_deps2_b10_s1000_true_PersistentStorage(101)
//5.4532766 \\ 5.4184248 \\ 5.4128773 \\ 5.4369494 \\ 6.0554658 \\ 5.4115237 \\ 5.4018589 \\ 5.4054070 \\ 6.0433320 \\ 5.4044673 \\ 5.4431694 \\ 5.4442125 \\ 5.4480428 \\ 6.0984828 \\ 5.4951169 \\ 5.4081834 \\ 5.3844418 \\ 6.0599562 \\ 5.3988859 \\ 5.3868417 \\ 5.4526471 \\ 5.3805320 \\ 6.0640953 \\ 5.4722211 \\ 5.3738295 \\ 5.4379671 \\ 5.4015812 \\ 6.0290715 \\ 5.4403633 \\ 5.4253061 \\ 5.4114237 \\ 6.0471853 \\ 5.4053706 \\ 5.4007916 \\ 5.4380621 \\ 5.4003735 \\ 6.0297957 \\ 5.4249173 \\ 5.3867396 \\ 5.4045903 \\ 6.0037381 \\ 5.4008046 \\ 5.4746970 \\ 5.4270924 \\ 5.4183504 \\ 6.0505065 \\ 5.4344525 \\ 5.4103865 \\ 5.4209776 \\ 5.4228532 \\ 6.0383809 \\ 5.4176114 \\ 5.4240057 \\ 5.4156093 \\ 6.0790381 \\ 5.4269311 \\ 5.4271368 \\ 5.3987237 \\ 5.4372011 \\ 6.0438969 \\ 5.4226058 \\ 5.4153645 \\ 5.4023328 \\ 6.1150824 \\ 5.4057722 \\ 5.4294076 \\ 5.4676895 \\ 5.4140596 \\ 6.1297900 \\ 5.4056195 \\ 5.4520101 \\ 5.3998500 \\ 5.3890489 \\ 6.0528531 \\ 5.4413454 \\ 5.4280938 \\ 5.4466467 \\ 6.0407316 \\ 5.4177970 \\ 5.4527877 \\ 5.3925272 \\ 5.3964829 \\ 6.0190335 \\ 5.4088180 \\ 5.3930956 \\ 5.4698659 \\ 6.0348405 \\ 5.4305013 \\ 5.4042087 \\ 5.4327131 \\ 5.4288080 \\ 6.0567136 \\ 5.4415196 \\ 5.4059422 \\ 5.4486619 \\ 5.3986917 \\ 6.0786651 \\ 5.4020858 \\ 5.4175157 \\ 5.3925073 \\ 6.0732367 \\
//runDeterministicGenerationAndSquash_10000_deps4_b1_s10000_true_PersistentStorage(101)
//8.1581157 \\ 8.1793604 \\ 8.1612028 \\ 8.1961484 \\ 8.1401360 \\ 8.1361580 \\ 8.1145904 \\ 8.0664166 \\ 8.1217197 \\ 8.1670277 \\ 8.0390212 \\ 8.1201224 \\ 8.1293450 \\ 8.0740836 \\ 8.0562985 \\ 8.0766293 \\ 8.0623233 \\ 8.0252173 \\ 8.0393306 \\ 8.0638830 \\ 8.1415853 \\ 8.0490639 \\ 8.0687584 \\ 8.1095670 \\ 8.1096943 \\ 8.1603984 \\ 8.0721076 \\ 8.0033910 \\ 7.9980643 \\ 8.1239786 \\ 8.0521395 \\ 8.0485653 \\ 8.0177906 \\ 8.1162280 \\ 8.0678722 \\ 8.0307196 \\ 8.0155337 \\ 8.0630163 \\ 8.1387253 \\ 8.0305743 \\ 8.1372873 \\ 8.1515945 \\ 8.0677962 \\ 8.0766469 \\ 8.0819136 \\ 8.0007553 \\ 8.0671496 \\ 8.0657291 \\ 7.9843623 \\ 8.0249612 \\ 7.9392110 \\ 8.0246559 \\ 8.1090057 \\ 8.0875531 \\ 8.0029855 \\ 8.0474801 \\ 8.0677835 \\ 8.0347849 \\ 8.1417845 \\ 8.0300140 \\ 8.1061035 \\ 8.1113396 \\ 8.0439021 \\ 8.0561336 \\ 8.0936266 \\ 8.0606731 \\ 8.0283494 \\ 8.0094431 \\ 8.1571587 \\ 8.1140395 \\ 8.0618617 \\ 8.1485934 \\ 8.1034774 \\ 8.0856427 \\ 8.0893698 \\ 8.1029952 \\ 8.1304237 \\ 8.1888243 \\ 8.1499851 \\ 8.1833502 \\ 8.0981588 \\ 8.0816681 \\ 8.1031187 \\ 8.0989100 \\ 8.1942135 \\ 8.2123754 \\ 8.6833112 \\ 8.0987019 \\ 8.1254812 \\ 8.1282947 \\ 8.0798237 \\ 8.1155156 \\ 8.1291581 \\ 8.0631225 \\ 8.0533672 \\ 8.0952769 \\ 7.9918925 \\ 8.1160919 \\ 8.0580285 \\ 8.7813896 \\ 8.0744725 \\
//runDeterministicGenerationAndSquash_10000_deps4_b100_s1_true_PersistentStorage(101)
//4.8821541 \\ 4.8781363 \\ 4.8718461 \\ 4.9435017 \\ 4.8968144 \\ 4.8932527 \\ 4.8799442 \\ 4.9044718 \\ 4.9199629 \\ 4.8796177 \\ 4.8988058 \\ 4.8674699 \\ 4.8967361 \\ 4.9876545 \\ 4.8512410 \\ 4.8890470 \\ 4.8678978 \\ 4.9126289 \\ 4.8691732 \\ 4.8667811 \\ 4.8685023 \\ 4.8567878 \\ 4.9705550 \\ 4.8584805 \\ 4.8669546 \\ 4.8667673 \\ 4.9906501 \\ 4.9254364 \\ 4.8735691 \\ 4.9099916 \\ 4.8863673 \\ 4.9181642 \\ 4.8694697 \\ 4.8996300 \\ 4.8903896 \\ 4.8369888 \\ 4.8931142 \\ 4.8827657 \\ 4.8951257 \\ 4.9229200 \\ 4.9399950 \\ 4.8830136 \\ 4.9127584 \\ 4.8896613 \\ 4.8572787 \\ 4.9117430 \\ 4.9361175 \\ 4.8765244 \\ 4.8820174 \\ 4.8885852 \\ 4.9290914 \\ 4.9316186 \\ 4.8692660 \\ 4.9031688 \\ 4.9560419 \\ 4.9086672 \\ 4.9125818 \\ 4.8943513 \\ 4.8961631 \\ 4.9696407 \\ 4.9236790 \\ 4.8548114 \\ 4.8856257 \\ 4.9682175 \\ 4.9323438 \\ 4.8812055 \\ 4.9011801 \\ 4.8846817 \\ 4.9144670 \\ 4.8643146 \\ 4.8723729 \\ 4.8995170 \\ 4.9068023 \\ 4.9558825 \\ 4.8992608 \\ 4.8903082 \\ 4.8886523 \\ 5.0214118 \\ 4.9380878 \\ 4.8762420 \\ 4.8768125 \\ 4.9193987 \\ 4.9317471 \\ 4.9061221 \\ 4.8552216 \\ 4.9038097 \\ 4.9144166 \\ 4.9120962 \\ 4.8366069 \\ 4.8723669 \\ 4.9282513 \\ 4.9587548 \\ 4.8711155 \\ 4.8871276 \\ 4.8642132 \\ 4.8608624 \\ 4.9200618 \\ 4.8652158 \\ 4.8612247 \\ 4.8724969 \\ 4.9418296 \\
//runDeterministicGenerationAndSquash_1000_deps4_b10_s10_true_PersistentStorage(101)
//0.4731545 \\ 0.4742386 \\ 0.4726875 \\ 0.4757077 \\ 0.4716941 \\ 0.4757400 \\ 1.1207277 \\ 0.4763293 \\ 0.4758699 \\ 0.4775950 \\ 0.4769323 \\ 0.4728037 \\ 0.4776210 \\ 1.1199613 \\ 0.4766887 \\ 0.4777878 \\ 0.4780737 \\ 0.4850509 \\ 0.4764293 \\ 0.4781173 \\ 1.1218122 \\ 0.4726605 \\ 0.4747577 \\ 0.4742097 \\ 0.4765293 \\ 0.4768195 \\ 1.1134232 \\ 0.4779176 \\ 0.4788747 \\ 0.4752477 \\ 0.4751278 \\ 0.4798869 \\ 0.4804743 \\ 1.1251416 \\ 0.4785462 \\ 0.4792932 \\ 0.4787108 \\ 0.4760626 \\ 0.4836846 \\ 0.4936119 \\ 1.1317618 \\ 0.4825212 \\ 0.4768104 \\ 0.4805002 \\ 0.4812157 \\ 0.4800332 \\ 1.1244073 \\ 0.4806974 \\ 0.4929187 \\ 0.4767276 \\ 0.4834658 \\ 0.4832503 \\ 0.4813705 \\ 1.1428036 \\ 0.4911787 \\ 0.5035852 \\ 0.4805659 \\ 0.4834900 \\ 0.4804748 \\ 0.4787436 \\ 1.1225355 \\ 0.4764020 \\ 0.4755215 \\ 0.4887163 \\ 0.4985133 \\ 0.4759670 \\ 0.4739882 \\ 1.1330521 \\ 0.4733246 \\ 0.4859567 \\ 0.4760027 \\ 0.4725832 \\ 0.4733779 \\ 1.1207676 \\ 0.4742960 \\ 0.4723615 \\ 0.4737149 \\ 0.4825601 \\ 0.4932351 \\ 0.4738001 \\ 1.1363323 \\ 0.4786925 \\ 0.4746232 \\ 0.4800859 \\ 0.4750619 \\ 0.4771265 \\ 0.4766433 \\ 1.1198204 \\ 0.4751325 \\ 0.4746028 \\ 0.4759364 \\ 0.4759878 \\ 0.4786302 \\ 1.1198033 \\ 0.4761383 \\ 0.4777430 \\ 0.4772404 \\ 0.4818588 \\ 0.4754608 \\ 0.4766540 \\ 1.1236649 \\
//runDeterministicGenerationAndSquash_10000_deps2_b10_s10_true_PersistentStorage(101)
//5.4167855 \\ 5.4157473 \\ 6.0551260 \\ 5.4114191 \\ 5.4047162 \\ 6.0379441 \\ 5.4175337 \\ 5.4086350 \\ 6.0298482 \\ 5.4707169 \\ 5.4245664 \\ 6.0436905 \\ 5.4156845 \\ 5.4316485 \\ 6.0581395 \\ 5.4206434 \\ 5.4487321 \\ 6.0457091 \\ 5.4079200 \\ 5.4190965 \\ 6.0625614 \\ 5.4007347 \\ 5.4534738 \\ 6.0408973 \\ 5.4254438 \\ 5.4064892 \\ 6.0384414 \\ 5.4209795 \\ 5.4055264 \\ 6.0458225 \\ 5.4907959 \\ 5.4112893 \\ 6.0774590 \\ 5.4160509 \\ 5.4520983 \\ 6.0423283 \\ 5.4813413 \\ 5.4006706 \\ 6.0469532 \\ 5.4615489 \\ 5.4281488 \\ 6.0661536 \\ 5.4423339 \\ 5.5379590 \\ 6.0546106 \\ 5.4063035 \\ 5.3889370 \\ 6.0826481 \\ 5.4254119 \\ 5.4253521 \\ 6.0764414 \\ 5.4442425 \\ 5.4012549 \\ 6.0357725 \\ 5.4307458 \\ 5.5002771 \\ 6.0464010 \\ 5.4235662 \\ 5.4475473 \\ 6.0630261 \\ 5.4106810 \\ 5.4300140 \\ 6.0422133 \\ 5.4727880 \\ 5.4257830 \\ 6.0725071 \\ 5.4275285 \\ 5.4441940 \\ 6.0695725 \\ 5.4080177 \\ 5.3664947 \\ 6.0320342 \\ 5.3851050 \\ 5.3988514 \\ 6.1941430 \\ 5.6905848 \\ 5.7320520 \\ 6.2523255 \\ 5.4161495 \\ 5.4392208 \\ 6.0565884 \\ 5.4162243 \\ 5.4524586 \\ 6.0581531 \\ 5.4341255 \\ 5.4070199 \\ 6.0435769 \\ 5.4462415 \\ 5.4359367 \\ 6.0616284 \\ 5.4079033 \\ 5.3912912 \\ 6.0756533 \\ 5.4453619 \\ 5.4182755 \\ 6.0439068 \\ 5.4364284 \\ 5.4147560 \\ 6.0722927 \\ 5.4314303 \\ 5.4216271 \\
//runDeterministicGenerationAndSquash_10000_deps0_b10_s1000_true_PersistentStorage(101)
//5.2801619 \\ 5.2937751 \\ 5.3037302 \\ 4.6862359 \\ 5.3340989 \\ 5.2998112 \\ 5.2985116 \\ 5.3080656 \\ 5.2882853 \\ 5.3400910 \\ 5.3024915 \\ 5.2628342 \\ 5.3014574 \\ 5.3082952 \\ 5.2942513 \\ 5.2857765 \\ 5.3104780 \\ 5.3142868 \\ 5.2854693 \\ 4.6457516 \\ 5.3416932 \\ 5.2753156 \\ 5.2868243 \\ 5.2615577 \\ 5.2562940 \\ 5.2656697 \\ 5.2720901 \\ 5.2566913 \\ 5.2530832 \\ 5.2640131 \\ 5.2963286 \\ 5.2981377 \\ 5.2974237 \\ 5.2909583 \\ 5.2895573 \\ 4.6756030 \\ 5.3465302 \\ 5.2781788 \\ 5.2532455 \\ 5.2785775 \\ 5.2649693 \\ 5.2854956 \\ 5.2665570 \\ 5.3099782 \\ 5.2746189 \\ 5.2759376 \\ 5.2856037 \\ 5.2705332 \\ 5.2776246 \\ 5.3057776 \\ 5.3311221 \\ 4.6608672 \\ 5.3106636 \\ 5.2979818 \\ 5.2944265 \\ 5.2777098 \\ 5.2924861 \\ 5.2779296 \\ 5.3228541 \\ 5.2991727 \\ 5.3336327 \\ 5.3035260 \\ 5.2909720 \\ 5.2549055 \\ 5.2596167 \\ 5.2928483 \\ 5.3183795 \\ 4.6740326 \\ 5.3214480 \\ 5.2646570 \\ 5.2907424 \\ 5.2761781 \\ 5.3004252 \\ 5.3006829 \\ 5.2751008 \\ 5.2888197 \\ 5.2896733 \\ 5.2705535 \\ 5.2861389 \\ 5.2800393 \\ 5.2915667 \\ 5.2809160 \\ 5.3507621 \\ 4.6396276 \\ 5.2969016 \\ 5.3379486 \\ 5.2781122 \\ 5.3116252 \\ 5.2765193 \\ 5.3063489 \\ 5.2881044 \\ 5.2972153 \\ 5.3094266 \\ 5.3132653 \\ 5.3323785 \\ 5.2906711 \\ 5.2853785 \\ 5.2684253 \\ 5.2748666 \\ 4.6664652 \\ 5.3206175 \\
//runDeterministicGenerationAndSquash_10000_deps4_b100_s10000_true_PersistentStorage(101)
//4.9679171 \\ 4.9398359 \\ 4.9490935 \\ 4.9557155 \\ 4.9620670 \\ 4.9137201 \\ 4.9026361 \\ 4.9250573 \\ 5.0779897 \\ 4.9807556 \\ 4.9222229 \\ 4.9450780 \\ 4.9768136 \\ 4.9939117 \\ 4.9060779 \\ 4.8946832 \\ 4.9292797 \\ 4.9828148 \\ 4.9606277 \\ 4.9669260 \\ 4.9567995 \\ 5.0087680 \\ 4.9157758 \\ 4.9102477 \\ 4.9863833 \\ 5.0386972 \\ 5.0079242 \\ 4.9310833 \\ 4.9417567 \\ 4.9857435 \\ 4.9678324 \\ 4.9834633 \\ 4.9502697 \\ 4.9892204 \\ 4.9373862 \\ 4.9557004 \\ 4.9319251 \\ 4.9914525 \\ 4.9185339 \\ 4.9462854 \\ 4.9227900 \\ 5.0241444 \\ 4.9886460 \\ 4.9866668 \\ 4.9538399 \\ 5.0046389 \\ 4.9176241 \\ 4.9545783 \\ 5.0233614 \\ 4.9484991 \\ 4.9925594 \\ 4.9607099 \\ 4.9241665 \\ 4.9815410 \\ 4.9907480 \\ 4.9433268 \\ 5.0017896 \\ 4.9590854 \\ 5.0014754 \\ 4.9301342 \\ 4.9617953 \\ 4.9561313 \\ 4.9843615 \\ 4.9475100 \\ 4.9484744 \\ 4.9829031 \\ 5.0008655 \\ 4.9413891 \\ 4.9609371 \\ 4.9822260 \\ 4.9414438 \\ 5.0098957 \\ 4.9249151 \\ 4.9204133 \\ 4.9342660 \\ 5.0202546 \\ 4.9297579 \\ 4.9428018 \\ 4.9247192 \\ 5.0011777 \\ 4.9859680 \\ 4.9540010 \\ 4.9657789 \\ 4.9699477 \\ 4.9617071 \\ 4.9473623 \\ 4.9227665 \\ 4.9883545 \\ 4.8932759 \\ 4.9292343 \\ 4.9888605 \\ 5.0335382 \\ 4.9243222 \\ 4.9322292 \\ 4.9769604 \\ 5.0213378 \\ 4.9159701 \\ 4.9441162 \\ 4.9020394 \\ 4.9838631 \\ 4.9221128 \\
//runDeterministicGenerationAndSquash_10000_deps4_b100_s1000_true_PersistentStorage(101)
//4.8785099 \\ 4.9233452 \\ 4.8801968 \\ 4.9033346 \\ 4.8925277 \\ 4.9412412 \\ 4.9499498 \\ 4.9029445 \\ 4.8772198 \\ 4.9221660 \\ 4.9360110 \\ 4.8789337 \\ 4.8736877 \\ 4.8550161 \\ 4.9397133 \\ 4.8580435 \\ 4.8757959 \\ 4.8537038 \\ 4.9162241 \\ 4.9152740 \\ 4.8899021 \\ 4.8983581 \\ 4.8998090 \\ 4.9256373 \\ 4.8630422 \\ 4.9088455 \\ 4.8855400 \\ 4.9444425 \\ 4.8789183 \\ 4.8707278 \\ 4.8547304 \\ 4.9471017 \\ 4.9951421 \\ 4.9006179 \\ 4.8961801 \\ 4.8883870 \\ 4.9270507 \\ 4.8789377 \\ 4.8493667 \\ 4.8746346 \\ 4.9426051 \\ 4.8586085 \\ 4.8924896 \\ 4.8736078 \\ 4.9463985 \\ 4.8864925 \\ 4.8852739 \\ 4.8808494 \\ 4.8851029 \\ 4.9720439 \\ 4.8651509 \\ 4.8619003 \\ 4.8882843 \\ 4.8877445 \\ 4.8953736 \\ 4.8821459 \\ 4.8964714 \\ 4.9314133 \\ 4.8913748 \\ 4.8775262 \\ 4.8723337 \\ 4.8948427 \\ 4.9260682 \\ 4.8516194 \\ 4.9165068 \\ 4.9131425 \\ 4.9588273 \\ 4.8825749 \\ 4.8502029 \\ 4.8925806 \\ 4.8986681 \\ 4.9461894 \\ 4.8552085 \\ 4.8789396 \\ 4.8743732 \\ 4.9198031 \\ 4.8439777 \\ 4.8852352 \\ 4.8578019 \\ 4.9512538 \\ 4.8565524 \\ 4.8636234 \\ 4.9016263 \\ 4.8826857 \\ 4.9384587 \\ 4.8853995 \\ 4.8867573 \\ 4.8666083 \\ 4.9436482 \\ 4.8359079 \\ 4.8915094 \\ 4.8747544 \\ 4.8603471 \\ 4.8992468 \\ 4.9042032 \\ 4.8813550 \\ 4.9030470 \\ 4.9389317 \\ 4.8826444 \\ 4.8867797 \\ 4.8672091 \\
//runDeterministicGenerationAndSquash_1000_deps4_b10_s1_true_PersistentStorage(101)
//0.4773714 \\ 0.4820397 \\ 0.4772321 \\ 0.4788726 \\ 0.5051517 \\ 0.4738689 \\ 1.1217395 \\ 0.4765575 \\ 0.4743722 \\ 0.4765232 \\ 0.4815726 \\ 0.4740967 \\ 0.4772905 \\ 1.1226297 \\ 0.4722585 \\ 0.4759283 \\ 0.4764554 \\ 0.4797211 \\ 0.4790965 \\ 0.4778862 \\ 1.1198043 \\ 0.4773655 \\ 0.4758160 \\ 0.4773280 \\ 0.4783882 \\ 0.4783684 \\ 1.1199491 \\ 0.4814599 \\ 0.4741789 \\ 0.4772771 \\ 0.4780720 \\ 0.4792845 \\ 0.4799143 \\ 1.1246800 \\ 0.4746346 \\ 0.4790079 \\ 0.4861318 \\ 0.4901628 \\ 0.4886202 \\ 0.4771381 \\ 1.1186881 \\ 0.4743913 \\ 0.4812238 \\ 0.4800618 \\ 0.4778125 \\ 0.4755239 \\ 0.4797327 \\ 1.1232802 \\ 0.4785767 \\ 0.4756820 \\ 0.4775784 \\ 0.4791229 \\ 0.4950875 \\ 1.1237641 \\ 0.4801704 \\ 0.4805592 \\ 0.4860989 \\ 0.4779190 \\ 0.4794685 \\ 0.4791220 \\ 1.1413560 \\ 0.4786514 \\ 0.4747980 \\ 0.4954807 \\ 0.4741650 \\ 0.4791714 \\ 0.4769724 \\ 1.1216885 \\ 0.4785229 \\ 0.4748404 \\ 0.4818034 \\ 0.4796862 \\ 0.4927010 \\ 1.1247760 \\ 0.4795793 \\ 0.4778867 \\ 0.4766381 \\ 0.4830000 \\ 0.4770424 \\ 0.4782229 \\ 1.1237090 \\ 0.4843769 \\ 0.4767946 \\ 0.4797801 \\ 0.4770779 \\ 0.4756198 \\ 0.4783928 \\ 1.1239495 \\ 0.4760703 \\ 0.4777472 \\ 0.4761680 \\ 0.4817406 \\ 0.4775197 \\ 0.4772117 \\ 1.1169473 \\ 0.4763426 \\ 0.4761559 \\ 0.4837864 \\ 0.5043260 \\ 0.4732863 \\ 1.1194056 \\
//runDeterministicGenerationAndSquash_10000_deps4_b1_s10_true_PersistentStorage(101)
//6.1157249 \\ 6.1761866 \\ 6.3494451 \\ 6.1077063 \\ 6.2137447 \\ 6.2042757 \\ 6.1535545 \\ 6.1296905 \\ 6.1539787 \\ 6.2567096 \\ 6.1878354 \\ 6.1649100 \\ 6.1451161 \\ 6.1340809 \\ 6.1643118 \\ 6.2512060 \\ 6.1332654 \\ 6.2091624 \\ 6.1624044 \\ 6.1846376 \\ 6.1684096 \\ 6.1922192 \\ 6.1640047 \\ 6.1349875 \\ 6.2110213 \\ 6.2308835 \\ 6.1493437 \\ 6.1984728 \\ 6.2782580 \\ 6.2077963 \\ 6.2304187 \\ 6.1485238 \\ 6.1796939 \\ 6.1591570 \\ 6.1435709 \\ 6.5886211 \\ 6.1769148 \\ 6.1625082 \\ 6.2335081 \\ 6.1527982 \\ 6.1705251 \\ 6.1600409 \\ 6.2031007 \\ 6.1555776 \\ 6.2145822 \\ 6.1246508 \\ 6.2345090 \\ 6.1651078 \\ 6.1403891 \\ 6.1604618 \\ 6.1967071 \\ 6.3090051 \\ 6.1782277 \\ 6.2014894 \\ 6.1279885 \\ 6.1777657 \\ 6.3434479 \\ 6.1712614 \\ 6.1267757 \\ 6.1886111 \\ 6.1490521 \\ 6.2350747 \\ 6.1409327 \\ 6.2119424 \\ 6.4440967 \\ 6.2046416 \\ 6.1278148 \\ 6.1856179 \\ 6.3004512 \\ 6.1834511 \\ 6.1469441 \\ 6.1622940 \\ 6.2211290 \\ 6.1717423 \\ 6.1852757 \\ 6.1914456 \\ 6.2698816 \\ 6.3530195 \\ 6.1906109 \\ 6.1657192 \\ 6.1912019 \\ 6.1631774 \\ 6.2528752 \\ 6.5153437 \\ 6.2059148 \\ 6.2503668 \\ 6.2113258 \\ 6.2236746 \\ 6.2608632 \\ 6.2153478 \\ 6.1622107 \\ 6.2042067 \\ 6.2351250 \\ 6.2183992 \\ 6.2266042 \\ 6.2309056 \\ 6.3443240 \\ 6.2717694 \\ 6.1926973 \\ 6.1569100 \\ 6.1984856 \\
//runDeterministicGenerationAndSquash_10000_deps4_b10_s100_true_PersistentStorage(101)
//4.7794776 \\ 4.9496020 \\ 4.7643450 \\ 4.7853861 \\ 4.7692416 \\ 4.7681378 \\ 4.7707397 \\ 4.7772588 \\ 4.8179695 \\ 4.9744713 \\ 4.8137897 \\ 4.7815119 \\ 4.7693253 \\ 4.8162648 \\ 4.8140769 \\ 4.9552168 \\ 4.8182260 \\ 4.7638643 \\ 4.7559307 \\ 4.7945554 \\ 4.7474119 \\ 4.7966314 \\ 4.8240551 \\ 4.7452692 \\ 4.7954789 \\ 4.8330003 \\ 4.7764791 \\ 4.8284733 \\ 4.8075651 \\ 4.7591882 \\ 4.7633596 \\ 4.7596845 \\ 4.7972694 \\ 4.7562602 \\ 4.7596467 \\ 4.7719002 \\ 4.7751712 \\ 4.7917799 \\ 4.7357051 \\ 4.7871818 \\ 4.7741635 \\ 4.7703103 \\ 4.7938703 \\ 4.7550993 \\ 4.7691655 \\ 4.7796265 \\ 4.7772545 \\ 4.7815914 \\ 4.7654746 \\ 4.8125707 \\ 4.7598136 \\ 4.7428886 \\ 4.7338888 \\ 4.7214443 \\ 4.7263506 \\ 4.7499887 \\ 4.7746954 \\ 4.7759542 \\ 4.7823205 \\ 4.7749505 \\ 4.7641575 \\ 4.7438201 \\ 4.7578759 \\ 4.7383891 \\ 4.7581519 \\ 4.7954275 \\ 4.7460668 \\ 4.7448874 \\ 4.7441881 \\ 4.7404735 \\ 4.7596753 \\ 4.7829273 \\ 4.7731312 \\ 4.7668561 \\ 4.7713035 \\ 4.7435765 \\ 4.8120622 \\ 4.7579590 \\ 4.7781872 \\ 4.6989423 \\ 4.7216297 \\ 4.7353678 \\ 4.7235909 \\ 4.7155677 \\ 4.7254249 \\ 4.7608223 \\ 4.7494880 \\ 4.7355944 \\ 4.6927782 \\ 4.7651675 \\ 4.7407183 \\ 4.7541493 \\ 4.7946042 \\ 4.7812911 \\ 4.7672923 \\ 4.7163608 \\ 4.8078289 \\ 4.7666858 \\ 4.7757770 \\ 4.7889303 \\ 4.7608903 \\
//runDeterministicGenerationAndSquash_1000_deps4_b10_s1000_true_PersistentStorage(101)
//0.4788138 \\ 0.4742553 \\ 0.4723440 \\ 0.4728248 \\ 0.4729204 \\ 0.4713252 \\ 1.1206869 \\ 0.4725866 \\ 0.4811682 \\ 0.4817414 \\ 0.4732144 \\ 0.4708357 \\ 0.4788681 \\ 0.4713700 \\ 0.4794093 \\ 1.1213476 \\ 0.4737476 \\ 0.4738261 \\ 0.4725631 \\ 0.4720877 \\ 0.4726241 \\ 0.4714881 \\ 0.4726211 \\ 1.1180215 \\ 0.4732767 \\ 0.4722031 \\ 0.4734570 \\ 0.4723308 \\ 0.4730527 \\ 0.4716391 \\ 0.4702956 \\ 1.1116217 \\ 0.4776101 \\ 0.4735955 \\ 0.4773382 \\ 0.5033985 \\ 0.4767102 \\ 0.4764873 \\ 0.4937428 \\ 1.1137672 \\ 0.4736973 \\ 0.4744038 \\ 0.4761304 \\ 0.4773240 \\ 0.4712432 \\ 0.4737986 \\ 0.4750621 \\ 0.4731941 \\ 1.1118954 \\ 0.4703555 \\ 0.4712146 \\ 0.4755340 \\ 0.4758157 \\ 0.4745899 \\ 0.4736210 \\ 0.4833257 \\ 1.1345753 \\ 0.4712760 \\ 0.4696081 \\ 0.4735265 \\ 0.4720218 \\ 0.4712471 \\ 0.4719798 \\ 0.4788132 \\ 1.1169771 \\ 0.4805059 \\ 0.4701393 \\ 0.4693209 \\ 0.4758527 \\ 0.4743003 \\ 0.4734511 \\ 0.4749003 \\ 1.1121643 \\ 0.4708155 \\ 0.4728108 \\ 0.4706688 \\ 0.4715034 \\ 0.4749844 \\ 0.4699683 \\ 0.4806575 \\ 1.1173888 \\ 0.4707086 \\ 0.4750962 \\ 0.4720155 \\ 0.4718700 \\ 0.4754597 \\ 0.4718695 \\ 0.4732411 \\ 0.4738877 \\ 1.1400311 \\ 0.4702898 \\ 0.4738549 \\ 0.4734814 \\ 0.4697928 \\ 0.4724817 \\ 0.4719113 \\ 0.4732227 \\ 1.1159411 \\ 0.4735521 \\ 0.4706100 \\ 0.4738997 \\
//runDeterministicGenerationAndSquash_10000_deps10_b10_s1000_true_PersistentStorage(101)
//5.4332561 \\ 6.0401860 \\ 5.4035950 \\ 5.4136225 \\ 5.3635600 \\ 6.0273239 \\ 5.3730774 \\ 5.4066564 \\ 5.4036836 \\ 6.1379870 \\ 5.4365651 \\ 5.4610114 \\ 5.4222812 \\ 5.3951323 \\ 6.0546704 \\ 5.4157108 \\ 5.4217964 \\ 5.4066063 \\ 6.0330558 \\ 5.4210327 \\ 5.4127852 \\ 5.4153536 \\ 5.3987796 \\ 6.0421295 \\ 5.3558489 \\ 5.3847815 \\ 5.4185724 \\ 6.0138384 \\ 5.4014710 \\ 5.4114583 \\ 5.4092827 \\ 5.4003670 \\ 6.0731777 \\ 5.3782850 \\ 5.4449786 \\ 5.4143282 \\ 6.0490065 \\ 5.4284024 \\ 5.4341760 \\ 5.4071605 \\ 6.0532936 \\ 5.4220426 \\ 5.4325108 \\ 5.4294761 \\ 5.4126277 \\ 6.0231767 \\ 5.3973463 \\ 5.4101400 \\ 5.4564021 \\ 6.0136512 \\ 5.4737772 \\ 5.4463527 \\ 5.4305120 \\ 5.4180378 \\ 6.0319714 \\ 5.3956586 \\ 5.4706883 \\ 5.4360568 \\ 6.0498287 \\ 5.4158433 \\ 5.3895358 \\ 5.3285917 \\ 5.4475824 \\ 6.0472787 \\ 5.4390015 \\ 5.4199954 \\ 5.5083721 \\ 6.0178402 \\ 5.4225955 \\ 5.4290115 \\ 5.4422277 \\ 6.0395796 \\ 5.4053080 \\ 5.3403246 \\ 5.4048394 \\ 5.4345618 \\ 6.0221126 \\ 5.3868161 \\ 5.3776121 \\ 5.4323197 \\ 6.0582652 \\ 5.4269711 \\ 5.3964884 \\ 5.4038220 \\ 5.4064265 \\ 6.0689734 \\ 5.4681932 \\ 5.4084979 \\ 5.3604051 \\ 6.0684640 \\ 5.4138217 \\ 5.4117732 \\ 5.4188310 \\ 5.4151880 \\ 6.0544566 \\ 5.4287620 \\ 5.5265750 \\ 5.4340946 \\ 6.0392859 \\ 5.4410816 \\ 5.4431679 \\
//runDeterministicGenerationAndSquash_10000_deps10_b10_s1_true_PersistentStorage(101)
//5.6713311 \\ 5.6119143 \\ 5.5072943 \\ 5.5275095 \\ 5.5398221 \\ 5.5353824 \\ 5.5236386 \\ 5.5438190 \\ 5.5550782 \\ 5.5697486 \\ 5.5331766 \\ 5.5257633 \\ 5.6532104 \\ 5.5447504 \\ 5.5760623 \\ 5.6370203 \\ 5.5482330 \\ 5.5461274 \\ 5.5608562 \\ 5.5251415 \\ 5.5083435 \\ 5.5643543 \\ 5.5562438 \\ 5.5088240 \\ 5.5684306 \\ 6.0639786 \\ 5.5010846 \\ 5.5161029 \\ 5.5132486 \\ 5.5886817 \\ 5.5220869 \\ 5.5314735 \\ 5.6369821 \\ 5.5449871 \\ 5.5682980 \\ 5.5240268 \\ 5.5448096 \\ 5.9916890 \\ 5.5352245 \\ 5.5403633 \\ 5.5165264 \\ 5.5543016 \\ 5.5179120 \\ 5.5654650 \\ 5.6035328 \\ 5.5189976 \\ 5.5058237 \\ 5.5130156 \\ 5.5861129 \\ 5.5194715 \\ 5.5033319 \\ 5.5163129 \\ 5.5655437 \\ 5.5378671 \\ 5.5067671 \\ 5.5337975 \\ 5.5159686 \\ 5.5183684 \\ 5.5509696 \\ 5.5386807 \\ 5.5691286 \\ 5.5345556 \\ 5.5069912 \\ 5.5300651 \\ 5.5450510 \\ 5.5316194 \\ 6.0987911 \\ 5.5120978 \\ 5.5606093 \\ 5.5197903 \\ 5.5940041 \\ 5.5619986 \\ 5.5611701 \\ 5.5226775 \\ 5.5550511 \\ 5.5404950 \\ 5.5626142 \\ 5.6080552 \\ 5.6401667 \\ 5.5647598 \\ 5.5474447 \\ 5.5339902 \\ 6.0700161 \\ 5.5615032 \\ 5.7565107 \\ 5.5699735 \\ 5.5125854 \\ 5.5793285 \\ 6.0701552 \\ 5.5163560 \\ 5.5042761 \\ 5.4749572 \\ 5.5553550 \\ 5.5454664 \\ 5.5178149 \\ 5.4992774 \\ 5.5752832 \\ 5.4752598 \\ 5.5180351 \\ 5.5492936 \\ 5.6350256 \\
//runDeterministicGenerationAndSquash_10000_deps0_b10_s1_true_PersistentStorage(101)
//5.4644043 \\ 5.3210143 \\ 5.3072789 \\ 5.3158789 \\ 5.2912949 \\ 4.6542081 \\ 5.2828412 \\ 5.3527803 \\ 5.2774558 \\ 5.3174090 \\ 5.3144332 \\ 5.3196806 \\ 5.3451555 \\ 5.2962277 \\ 5.2909126 \\ 5.3126941 \\ 5.2600532 \\ 5.3048147 \\ 5.2876494 \\ 5.2997870 \\ 5.2954229 \\ 5.3040060 \\ 4.6601552 \\ 5.3190679 \\ 5.2524499 \\ 5.3166322 \\ 5.3101363 \\ 5.2660312 \\ 5.2920415 \\ 5.3232194 \\ 5.2712542 \\ 5.2810868 \\ 5.2915869 \\ 5.2863707 \\ 5.3123842 \\ 5.2800989 \\ 5.3000086 \\ 5.2905556 \\ 5.2615959 \\ 4.6481553 \\ 5.3447580 \\ 5.2947573 \\ 5.3080806 \\ 5.3280644 \\ 5.3139039 \\ 5.2987072 \\ 5.2781447 \\ 5.3034065 \\ 5.3175726 \\ 5.2831362 \\ 5.3332381 \\ 5.3341634 \\ 5.2947685 \\ 5.2710981 \\ 5.3163017 \\ 5.2820330 \\ 4.6323882 \\ 5.3088807 \\ 5.3089153 \\ 5.3037137 \\ 5.3036458 \\ 5.2511293 \\ 5.3597287 \\ 5.3279391 \\ 5.2904802 \\ 5.3055021 \\ 5.3028149 \\ 5.3244913 \\ 5.2979845 \\ 5.3446622 \\ 5.3217346 \\ 5.3186825 \\ 5.3012937 \\ 4.6744969 \\ 5.3251918 \\ 5.3046501 \\ 5.2688795 \\ 5.3031369 \\ 5.2917036 \\ 5.3067467 \\ 5.3224420 \\ 5.3100680 \\ 5.3024688 \\ 5.3599670 \\ 5.2749144 \\ 5.2634905 \\ 5.2931740 \\ 5.3087578 \\ 5.2767242 \\ 5.2912494 \\ 4.6505847 \\ 4.6324860 \\ 5.2987029 \\ 5.3258424 \\ 5.3135625 \\ 5.3056275 \\ 5.2904095 \\ 5.3192279 \\ 5.2867981 \\ 5.2875295 \\ 5.3093815 \\
//runDeterministicGenerationAndSquash_10000_deps4_b100_s10_true_PersistentStorage(101)
//4.8888828 \\ 4.8972099 \\ 4.9564621 \\ 4.8932950 \\ 4.9552621 \\ 4.8921730 \\ 4.9130432 \\ 4.9085990 \\ 4.9291593 \\ 4.9238587 \\ 4.8827848 \\ 4.8712289 \\ 4.8839289 \\ 4.9690737 \\ 4.8729911 \\ 4.9467867 \\ 4.8689350 \\ 4.8490353 \\ 4.9274158 \\ 4.8831436 \\ 4.8570194 \\ 4.8889737 \\ 4.9411060 \\ 4.9205242 \\ 4.9106697 \\ 4.8594844 \\ 4.8747336 \\ 4.9550018 \\ 4.9383878 \\ 4.9134587 \\ 4.8724101 \\ 4.9578066 \\ 4.8778659 \\ 4.8939025 \\ 4.8633262 \\ 4.9053251 \\ 4.9597812 \\ 4.8922144 \\ 4.8999297 \\ 4.9038979 \\ 4.8691280 \\ 4.9835139 \\ 4.9196218 \\ 4.8933041 \\ 4.9768782 \\ 4.9351918 \\ 4.8892022 \\ 4.8823993 \\ 4.8833337 \\ 4.8914125 \\ 4.9299585 \\ 4.9291845 \\ 4.8998239 \\ 4.9200243 \\ 5.0106394 \\ 4.9407632 \\ 4.8903524 \\ 4.8842890 \\ 4.8958236 \\ 4.9445099 \\ 4.8668190 \\ 4.9128495 \\ 4.8960327 \\ 4.9214193 \\ 4.9678940 \\ 4.8849190 \\ 4.9034327 \\ 4.9076904 \\ 4.9563860 \\ 4.8614240 \\ 4.8637571 \\ 4.8375994 \\ 4.8528414 \\ 4.9454008 \\ 4.8880311 \\ 4.8980645 \\ 4.8875255 \\ 4.8923886 \\ 4.9241012 \\ 4.8839726 \\ 4.8916777 \\ 4.8363831 \\ 4.9286154 \\ 4.8646169 \\ 4.8310427 \\ 4.8584865 \\ 4.8760960 \\ 4.9186943 \\ 4.8798617 \\ 4.8715411 \\ 4.8637793 \\ 4.8483783 \\ 4.9263152 \\ 4.8909016 \\ 4.8650248 \\ 4.8847652 \\ 4.9134331 \\ 4.9206134 \\ 4.8847987 \\ 4.8890545 \\ 4.8877065 \\
//runDeterministicGenerationAndSquash_10000_deps4_b1_s1000_true_PersistentStorage(101)
//5.2651545 \\ 5.7092183 \\ 5.3225125 \\ 5.2318953 \\ 5.2342019 \\ 5.2577261 \\ 5.2558513 \\ 5.2403287 \\ 5.2850439 \\ 5.2624060 \\ 5.2253563 \\ 5.2526535 \\ 5.2475953 \\ 5.2381614 \\ 5.3201989 \\ 5.2615658 \\ 5.2613728 \\ 5.2457089 \\ 5.2326538 \\ 5.2575422 \\ 5.1988661 \\ 5.2147332 \\ 5.2621259 \\ 5.2320825 \\ 5.2431230 \\ 5.2584463 \\ 5.2215798 \\ 5.2523657 \\ 5.2517642 \\ 5.2594422 \\ 5.1981363 \\ 5.2683702 \\ 5.2685128 \\ 5.2395600 \\ 5.2651249 \\ 5.2431491 \\ 5.2303995 \\ 5.1964073 \\ 5.2056715 \\ 5.2378554 \\ 5.2216004 \\ 5.2502255 \\ 5.2333004 \\ 5.2448067 \\ 5.2440896 \\ 5.2929402 \\ 5.2567328 \\ 5.3593553 \\ 5.3154313 \\ 5.3151033 \\ 5.2864819 \\ 5.3538160 \\ 5.2677961 \\ 5.2223336 \\ 5.2256483 \\ 5.2744704 \\ 5.2387005 \\ 5.2764249 \\ 5.2626852 \\ 5.2879723 \\ 5.2471260 \\ 5.2863422 \\ 5.2940389 \\ 6.0046579 \\ 5.2556274 \\ 5.3056920 \\ 5.3030242 \\ 5.3068026 \\ 5.2672754 \\ 5.3119758 \\ 5.2981475 \\ 5.3100104 \\ 5.2670436 \\ 5.2551990 \\ 5.9353981 \\ 5.2519044 \\ 5.2826048 \\ 5.2604544 \\ 5.2421749 \\ 5.2722731 \\ 5.2563035 \\ 5.2383436 \\ 5.2566427 \\ 5.2594061 \\ 5.2574676 \\ 5.2454510 \\ 5.2526883 \\ 5.2474453 \\ 5.2586008 \\ 5.2647379 \\ 5.2685424 \\ 5.2119920 \\ 5.2455272 \\ 5.2739116 \\ 5.2698898 \\ 5.3028262 \\ 5.2840064 \\ 5.2184811 \\ 5.2602494 \\ 5.3328389 \\ 5.2677171 \\
//runDeterministicGenerationAndSquash_100_deps4_b10_s1000_true_PersistentStorage(101)
//0.0473648 \\ 0.0476842 \\ 0.0470710 \\ 0.0475070 \\ 0.0476905 \\ 0.0687611 \\ 0.0472946 \\ 0.0474021 \\ 0.0477994 \\ 0.0473049 \\ 0.0473916 \\ 0.0473958 \\ 0.0473952 \\ 0.0470713 \\ 0.0478056 \\ 0.0468071 \\ 0.0476729 \\ 0.0477364 \\ 0.0488980 \\ 0.0479002 \\ 0.0471548 \\ 0.0477180 \\ 0.0474753 \\ 0.0475607 \\ 0.0475730 \\ 0.0465138 \\ 0.0468907 \\ 0.0477309 \\ 0.0472119 \\ 0.0471797 \\ 0.0479280 \\ 0.0473128 \\ 0.0478990 \\ 0.0479345 \\ 0.0478893 \\ 0.0473544 \\ 0.0473628 \\ 0.0476222 \\ 0.0488139 \\ 0.6958943 \\ 0.0471316 \\ 0.0474540 \\ 0.0473215 \\ 0.0472101 \\ 0.0484025 \\ 0.0471634 \\ 0.0476034 \\ 0.0491966 \\ 0.0512191 \\ 0.0487849 \\ 0.0507401 \\ 0.0506679 \\ 0.0479460 \\ 0.0474065 \\ 0.0475409 \\ 0.0482657 \\ 0.0475303 \\ 0.0470121 \\ 0.0476548 \\ 0.0472359 \\ 0.0470559 \\ 0.0471313 \\ 0.0468720 \\ 0.0472405 \\ 0.0472360 \\ 0.0472265 \\ 0.0474422 \\ 0.0474823 \\ 0.0473859 \\ 0.0480067 \\ 0.0483870 \\ 0.0471282 \\ 0.0472862 \\ 0.0476277 \\ 0.0476006 \\ 0.0474335 \\ 0.0469484 \\ 0.0471738 \\ 0.0471547 \\ 0.0473105 \\ 0.0478188 \\ 0.0470352 \\ 0.0476882 \\ 0.0474004 \\ 0.0480195 \\ 0.0476601 \\ 0.0481925 \\ 0.0476977 \\ 0.0470959 \\ 0.0469515 \\ 0.0478636 \\ 0.0476285 \\ 0.0476124 \\ 0.0470811 \\ 0.0476351 \\ 0.0465854 \\ 0.0474046 \\ 0.0476354 \\ 0.0475164 \\ 0.0474173 \\ 0.0469990 \\
//runDeterministicGenerationAndSquash_10000_deps4_b1_s100_true_PersistentStorage(101)
//5.2110438 \\ 5.5347236 \\ 5.1694052 \\ 5.1585609 \\ 5.1676983 \\ 5.2113312 \\ 5.2371661 \\ 5.3150611 \\ 5.2579336 \\ 5.2484140 \\ 5.2405349 \\ 5.2172431 \\ 5.2708322 \\ 5.2527286 \\ 5.2355121 \\ 5.2212206 \\ 5.1824685 \\ 5.2463757 \\ 5.2347992 \\ 5.3550211 \\ 5.2791877 \\ 5.2054432 \\ 5.2222013 \\ 5.1879815 \\ 5.2213276 \\ 5.2238572 \\ 5.3174252 \\ 5.3088113 \\ 5.5691384 \\ 5.2221310 \\ 5.1977491 \\ 5.2180754 \\ 5.3045167 \\ 5.2311097 \\ 5.2035406 \\ 5.1860100 \\ 5.1801462 \\ 5.5928773 \\ 5.2287029 \\ 5.2147070 \\ 5.2102350 \\ 5.2128337 \\ 5.2213994 \\ 5.2244462 \\ 5.5745138 \\ 5.2039652 \\ 5.2603518 \\ 5.2166417 \\ 5.2215604 \\ 5.2702693 \\ 5.2117726 \\ 5.2585362 \\ 5.2573355 \\ 5.2327016 \\ 5.2265276 \\ 5.2321592 \\ 5.2279033 \\ 5.1952569 \\ 5.1761618 \\ 5.2358447 \\ 5.2169222 \\ 5.2704303 \\ 5.2450015 \\ 5.2255263 \\ 5.2253856 \\ 5.2377980 \\ 5.3199491 \\ 5.3249809 \\ 5.6625159 \\ 5.2115948 \\ 5.2611086 \\ 5.2074390 \\ 5.1942793 \\ 5.2554562 \\ 5.2116927 \\ 5.2225499 \\ 5.2214777 \\ 5.2217575 \\ 5.2645311 \\ 5.2538011 \\ 5.2320768 \\ 5.2327430 \\ 5.2253425 \\ 5.2143966 \\ 5.2195367 \\ 5.1856703 \\ 5.2138939 \\ 5.2113514 \\ 5.2411546 \\ 5.2037430 \\ 5.2067221 \\ 5.2394294 \\ 5.2638879 \\ 5.2837940 \\ 5.2223362 \\ 5.2132849 \\ 5.2024455 \\ 5.2484285 \\ 5.2004393 \\ 5.2569298 \\ 5.2604672 \\
//runDeterministicGenerationAndSquash_1000_deps4_b10_s100_true_PersistentStorage(101)
//0.4720945 \\ 0.4745216 \\ 0.4862996 \\ 0.4761422 \\ 0.4702511 \\ 0.4700870 \\ 0.4751501 \\ 1.1124436 \\ 0.4744926 \\ 0.4726212 \\ 0.4749095 \\ 0.4730247 \\ 0.4748941 \\ 0.4706569 \\ 0.4704195 \\ 0.4692162 \\ 1.1197983 \\ 0.4710719 \\ 0.4701989 \\ 0.4683710 \\ 0.4696307 \\ 0.4726759 \\ 0.4884983 \\ 0.4685969 \\ 1.1099368 \\ 0.4687296 \\ 0.4693663 \\ 0.4719100 \\ 0.4723977 \\ 0.4717909 \\ 0.4701473 \\ 0.4730843 \\ 1.1160346 \\ 0.4684445 \\ 0.4699585 \\ 0.4690493 \\ 0.4707476 \\ 0.4702455 \\ 0.4710054 \\ 0.4701159 \\ 0.4731242 \\ 1.1180438 \\ 0.4712064 \\ 0.4721809 \\ 0.4702011 \\ 0.4700465 \\ 0.4848936 \\ 0.4786033 \\ 0.4807880 \\ 1.1178062 \\ 0.4721623 \\ 0.4694740 \\ 0.4684426 \\ 0.4696191 \\ 0.4703984 \\ 0.4722992 \\ 0.4684476 \\ 1.1168146 \\ 0.4792359 \\ 0.4750338 \\ 0.4717882 \\ 0.4920202 \\ 0.4703548 \\ 0.4721594 \\ 0.4714460 \\ 0.4708875 \\ 1.1193260 \\ 0.4744739 \\ 0.4700091 \\ 0.4693345 \\ 0.4804778 \\ 0.4733665 \\ 0.4748438 \\ 0.4712539 \\ 1.1266805 \\ 0.4716018 \\ 0.4704825 \\ 0.4732833 \\ 0.4729680 \\ 0.4705670 \\ 0.4737648 \\ 0.4723900 \\ 1.1177160 \\ 0.4738803 \\ 0.4710488 \\ 0.4697196 \\ 0.4779675 \\ 0.4733079 \\ 0.4687961 \\ 0.4723154 \\ 0.4702316 \\ 1.1222238 \\ 0.4726241 \\ 0.4841427 \\ 0.4684862 \\ 0.4720111 \\ 0.4713678 \\ 0.4728846 \\ 0.4747728 \\ 1.1165230 \\ 0.4700253 \\
//runDeterministicGenerationAndSquash_10000_deps4_b10_s10000_true_PersistentStorage(101)
//5.4077025 \\ 5.0738828 \\ 5.0329706 \\ 5.0419902 \\ 5.0579353 \\ 5.0367235 \\ 5.0720762 \\ 5.2664196 \\ 5.0380803 \\ 5.0294032 \\ 5.0176152 \\ 5.0675845 \\ 5.0208934 \\ 5.0491411 \\ 5.2556771 \\ 5.0377771 \\ 5.0311685 \\ 5.0531664 \\ 5.0808430 \\ 5.0709343 \\ 5.0064085 \\ 5.0437912 \\ 5.0846946 \\ 5.0399222 \\ 5.0664835 \\ 5.1139692 \\ 5.0750380 \\ 5.0451146 \\ 5.0299180 \\ 5.0944989 \\ 5.0553951 \\ 5.0341849 \\ 5.0073247 \\ 5.0554497 \\ 5.0211409 \\ 5.0140108 \\ 5.0203001 \\ 5.0267710 \\ 5.0840950 \\ 5.0387936 \\ 5.0339180 \\ 5.0746137 \\ 5.0578137 \\ 5.0345848 \\ 5.0228966 \\ 5.0463925 \\ 5.0419299 \\ 5.0933148 \\ 5.0700128 \\ 5.0442571 \\ 5.0838303 \\ 5.0843742 \\ 5.0478539 \\ 5.0085106 \\ 5.0542244 \\ 5.0143258 \\ 5.0688915 \\ 5.0688783 \\ 5.0069385 \\ 5.0761244 \\ 5.0810541 \\ 5.0411880 \\ 5.0560281 \\ 5.0869113 \\ 5.0521927 \\ 5.0398647 \\ 5.0785399 \\ 5.0563854 \\ 5.0729161 \\ 5.1255728 \\ 5.0547002 \\ 5.0138520 \\ 5.0121260 \\ 5.0071922 \\ 5.0759121 \\ 5.1567238 \\ 5.1639055 \\ 5.0809649 \\ 5.0942642 \\ 5.0301253 \\ 4.9822668 \\ 5.0345626 \\ 4.9891815 \\ 5.0504549 \\ 5.0622436 \\ 5.0235566 \\ 5.0064971 \\ 5.0892374 \\ 5.1973278 \\ 5.1151823 \\ 5.0545232 \\ 5.0197289 \\ 5.0337691 \\ 5.0871079 \\ 5.0356800 \\ 5.0586347 \\ 5.0153987 \\ 5.0304970 \\ 5.0118896 \\ 5.0598919 \\ 5.0651978 \\
//runDeterministicGenerationAndSquash_10000_deps10_b10_s100_true_PersistentStorage(101)
//6.3575488 \\ 5.4102498 \\ 6.0696311 \\ 5.3933593 \\ 5.4777907 \\ 5.3843953 \\ 6.0369215 \\ 5.4465276 \\ 5.3941131 \\ 6.0795766 \\ 5.4264965 \\ 5.4498806 \\ 5.4213934 \\ 6.0852800 \\ 5.4288026 \\ 5.4431418 \\ 6.0570606 \\ 5.4510560 \\ 5.4349966 \\ 5.4340238 \\ 6.0821156 \\ 5.4564272 \\ 5.4119441 \\ 6.0694616 \\ 5.4296728 \\ 5.4246820 \\ 5.4434405 \\ 6.0559806 \\ 5.4286760 \\ 5.4218928 \\ 5.4202149 \\ 6.0599721 \\ 5.4904111 \\ 5.4026791 \\ 6.0691901 \\ 5.4285781 \\ 5.4349049 \\ 5.4308867 \\ 6.0791579 \\ 5.4611200 \\ 5.4394456 \\ 6.0700607 \\ 5.4697479 \\ 5.5042603 \\ 5.4339235 \\ 6.0597861 \\ 5.4426934 \\ 5.3949977 \\ 6.0720266 \\ 5.4549382 \\ 5.4362095 \\ 5.4594831 \\ 6.0570907 \\ 5.3949592 \\ 5.4535104 \\ 6.0511374 \\ 5.4017807 \\ 5.4234876 \\ 5.4846144 \\ 6.0335762 \\ 5.4142375 \\ 5.4238282 \\ 6.0355533 \\ 5.4180924 \\ 5.3846613 \\ 5.6004146 \\ 6.0486640 \\ 5.4298537 \\ 5.4069053 \\ 6.0449636 \\ 5.4021248 \\ 5.4135623 \\ 5.4319544 \\ 6.0561623 \\ 5.3968222 \\ 5.3885113 \\ 5.4188415 \\ 6.0502496 \\ 5.4482890 \\ 5.4185799 \\ 6.0527411 \\ 5.4198592 \\ 5.4229795 \\ 5.4488678 \\ 6.0576079 \\ 5.4025989 \\ 5.4342439 \\ 6.0655663 \\ 5.4277371 \\ 5.4358903 \\ 5.4170750 \\ 6.0660186 \\ 5.4451510 \\ 5.4051513 \\ 6.0404343 \\ 5.4092086 \\ 5.4349879 \\ 5.4236961 \\ 6.0244954 \\ 5.3914929 \\ 5.3974952 \\
//runDeterministicGenerationAndSquash_10000_deps2_b10_s10000_true_PersistentStorage(101)
//5.7060752 \\ 6.3343492 \\ 6.3592242 \\ 6.3276941 \\ 6.3303437 \\ 6.3350480 \\ 5.6928784 \\ 6.3038609 \\ 6.3407049 \\ 6.3366750 \\ 6.3288436 \\ 6.3654599 \\ 6.3402107 \\ 5.6961484 \\ 6.3277017 \\ 6.3130915 \\ 6.2929687 \\ 6.3802274 \\ 6.3297192 \\ 5.6966463 \\ 6.3454452 \\ 6.3547443 \\ 6.3509573 \\ 6.3240187 \\ 6.3595962 \\ 6.3507345 \\ 5.6885508 \\ 6.3012976 \\ 6.3512467 \\ 6.3335734 \\ 6.3459411 \\ 6.3539914 \\ 5.7202864 \\ 6.2961798 \\ 6.3219487 \\ 6.3206248 \\ 6.3266176 \\ 6.3109577 \\ 6.3854245 \\ 5.6631371 \\ 6.2911050 \\ 6.3567619 \\ 6.3573472 \\ 6.3850016 \\ 6.3683489 \\ 5.7142042 \\ 6.3143158 \\ 6.3421709 \\ 6.3512950 \\ 6.3532739 \\ 6.3823071 \\ 6.4929252 \\ 5.7128621 \\ 6.3159849 \\ 6.3395207 \\ 6.3237437 \\ 6.3563555 \\ 6.3141895 \\ 5.7394067 \\ 6.3433315 \\ 6.2999513 \\ 6.2825455 \\ 6.3235918 \\ 6.3600133 \\ 6.3379729 \\ 5.6984431 \\ 6.3179864 \\ 6.3299737 \\ 6.3602223 \\ 6.3204303 \\ 6.3589684 \\ 5.7450233 \\ 6.3739440 \\ 6.3622360 \\ 6.4423689 \\ 6.3436959 \\ 6.3437524 \\ 6.3246494 \\ 5.7434351 \\ 6.3077751 \\ 6.3486256 \\ 6.3715404 \\ 6.3563318 \\ 6.3699468 \\ 5.7000254 \\ 6.3057673 \\ 6.3195653 \\ 6.3101436 \\ 6.3314422 \\ 6.3662574 \\ 6.3503389 \\ 5.6964045 \\ 6.3470147 \\ 6.2912426 \\ 6.3561037 \\ 6.3334482 \\ 6.3487749 \\ 5.7308059 \\ 6.3457772 \\ 6.3600808 \\ 6.3542476 \\
//runDeterministicGenerationAndSquash_10000_deps2_b10_s1_true_PersistentStorage(101)
//5.5179503 \\ 6.0449763 \\ 5.4392028 \\ 5.4309563 \\ 6.1447529 \\ 5.4506535 \\ 5.4285116 \\ 6.0766796 \\ 5.4063383 \\ 5.4269093 \\ 6.1193520 \\ 5.4172933 \\ 5.4259596 \\ 6.0550006 \\ 5.4050064 \\ 5.4084269 \\ 6.0654109 \\ 5.4742303 \\ 5.4374483 \\ 6.0513244 \\ 5.4261657 \\ 5.4280482 \\ 6.0513305 \\ 5.4035306 \\ 5.4236297 \\ 6.0345798 \\ 5.4075078 \\ 5.4343290 \\ 6.0624443 \\ 5.4546862 \\ 5.4333096 \\ 6.0267060 \\ 5.4450809 \\ 5.3909780 \\ 6.0351653 \\ 5.3899817 \\ 5.4029969 \\ 6.0781364 \\ 5.4422674 \\ 5.4592315 \\ 6.1481451 \\ 5.6295875 \\ 5.5759567 \\ 6.1510290 \\ 5.4291073 \\ 5.4227619 \\ 6.0005540 \\ 5.3997203 \\ 5.3896140 \\ 6.0457379 \\ 5.4055939 \\ 5.4282268 \\ 6.0412898 \\ 5.4157915 \\ 5.4608214 \\ 6.0671089 \\ 5.4493656 \\ 5.4371431 \\ 6.0057271 \\ 5.3844366 \\ 5.4215273 \\ 6.0585753 \\ 5.3988486 \\ 5.4111144 \\ 6.0292475 \\ 5.4156047 \\ 5.4670784 \\ 6.0364659 \\ 5.3982004 \\ 5.3969492 \\ 6.0406382 \\ 5.4248411 \\ 5.4009136 \\ 6.0724246 \\ 5.4153175 \\ 5.3888150 \\ 6.0698491 \\ 5.4018681 \\ 5.4168490 \\ 6.0687241 \\ 5.4211633 \\ 5.4096621 \\ 6.0536658 \\ 5.4129006 \\ 5.4051982 \\ 6.0264039 \\ 5.4272475 \\ 5.4204198 \\ 6.1028889 \\ 5.4007830 \\ 5.4402697 \\ 6.0845525 \\ 5.4021013 \\ 5.4095912 \\ 6.0252987 \\ 5.4103538 \\ 5.4175189 \\ 6.0196423 \\ 5.4205592 \\ 5.4951914 \\ 6.0333774 \\
//runDeterministicGenerationAndSquash_10000_deps4_b1000_s100_true_PersistentStorage(101)
//6.0688922 \\ 6.0526222 \\ 6.1162107 \\ 6.1140724 \\ 6.1771445 \\ 6.1068602 \\ 6.1593180 \\ 6.1384425 \\ 6.1018257 \\ 6.0879941 \\ 6.1753722 \\ 6.0737471 \\ 6.0959608 \\ 6.2143406 \\ 6.1043839 \\ 6.1511540 \\ 6.1417978 \\ 6.1032069 \\ 6.1731206 \\ 6.1349513 \\ 6.1045300 \\ 6.1441341 \\ 6.1274055 \\ 6.1220629 \\ 6.1959032 \\ 6.1590139 \\ 6.0978100 \\ 6.1505311 \\ 6.0837718 \\ 6.1463575 \\ 6.0879252 \\ 6.0999979 \\ 6.1211865 \\ 6.0960458 \\ 6.1129059 \\ 6.1394765 \\ 6.1129300 \\ 6.0871114 \\ 6.1252326 \\ 6.0947545 \\ 6.1170975 \\ 6.1007788 \\ 6.0854051 \\ 6.1212085 \\ 6.1555562 \\ 6.1346247 \\ 6.1527360 \\ 6.0911601 \\ 6.1008762 \\ 6.1222415 \\ 6.0962905 \\ 6.1372704 \\ 6.1233021 \\ 6.0468505 \\ 6.0874792 \\ 6.0750169 \\ 6.0839530 \\ 6.0905159 \\ 6.0655320 \\ 6.0436200 \\ 6.1654581 \\ 6.0454643 \\ 6.0355206 \\ 6.0788022 \\ 6.0315921 \\ 6.0994098 \\ 6.0787138 \\ 6.0617149 \\ 6.0675179 \\ 6.1415728 \\ 6.0382751 \\ 6.0563810 \\ 6.1044482 \\ 6.0665421 \\ 6.0342071 \\ 6.1347811 \\ 6.0898622 \\ 6.1734178 \\ 6.1044818 \\ 6.1132329 \\ 6.1267638 \\ 6.0907409 \\ 6.0712367 \\ 6.1417531 \\ 6.0703731 \\ 6.0897409 \\ 6.1496882 \\ 6.0934251 \\ 6.1114115 \\ 6.0811561 \\ 6.0700674 \\ 6.0932414 \\ 6.0303840 \\ 6.0961206 \\ 6.1368015 \\ 6.0694222 \\ 6.1183960 \\ 6.1062881 \\ 6.0754458 \\ 6.0455304 \\ 6.0717476 \\
//runDeterministicGenerationAndSquash_100_deps4_b10_s10_true_PersistentStorage(101)
//0.0485376 \\ 0.0478045 \\ 0.0490177 \\ 0.0475788 \\ 0.0488849 \\ 0.0481297 \\ 0.0481042 \\ 0.0479263 \\ 0.0477035 \\ 0.0481314 \\ 0.0481593 \\ 0.0645312 \\ 0.0473299 \\ 0.0475218 \\ 0.0473837 \\ 0.0479414 \\ 0.0476438 \\ 0.0475604 \\ 0.0476807 \\ 0.0478610 \\ 0.0475534 \\ 0.0475736 \\ 0.0481127 \\ 0.0486767 \\ 0.0476686 \\ 0.0474498 \\ 0.0481466 \\ 0.0479191 \\ 0.0478742 \\ 0.0476269 \\ 0.0476259 \\ 0.0478894 \\ 0.0471385 \\ 0.0489251 \\ 0.0473774 \\ 0.0479793 \\ 0.0481567 \\ 0.0478930 \\ 0.0475743 \\ 0.0474493 \\ 0.0475526 \\ 0.0481331 \\ 0.0473844 \\ 0.0477988 \\ 0.0477943 \\ 0.0476746 \\ 0.0475722 \\ 0.0474336 \\ 0.0479670 \\ 0.0473037 \\ 0.0475485 \\ 0.0481359 \\ 0.0637424 \\ 0.0476828 \\ 0.0479393 \\ 0.0477148 \\ 0.0474544 \\ 0.0479852 \\ 0.0476162 \\ 0.0477311 \\ 0.0473917 \\ 0.0478474 \\ 0.0479927 \\ 0.0476829 \\ 0.0481498 \\ 0.0475446 \\ 0.0479168 \\ 0.0472752 \\ 0.0476237 \\ 0.0482158 \\ 0.0479975 \\ 0.0477116 \\ 0.0472904 \\ 0.0480298 \\ 0.0484026 \\ 0.0487056 \\ 0.0476220 \\ 0.0480366 \\ 0.0477915 \\ 0.0479117 \\ 0.0481190 \\ 0.6963128 \\ 0.0474793 \\ 0.0484402 \\ 0.0477559 \\ 0.0479372 \\ 0.0479057 \\ 0.0474784 \\ 0.0482640 \\ 0.0478521 \\ 0.0481334 \\ 0.0475261 \\ 0.0476646 \\ 0.0486527 \\ 0.0480088 \\ 0.0473746 \\ 0.0480202 \\ 0.0478130 \\ 0.0480196 \\ 0.0475285 \\ 0.0482914 \\
//runDeterministicGenerationAndSquash_10000_deps0_b10_s10_true_PersistentStorage(101)
//5.3031170 \\ 5.2856313 \\ 5.3089642 \\ 5.3154019 \\ 5.3024276 \\ 5.3343199 \\ 4.6833018 \\ 4.6611484 \\ 5.3391136 \\ 5.2881380 \\ 5.2753917 \\ 5.2849916 \\ 5.2960601 \\ 5.3061556 \\ 5.3353377 \\ 5.2973109 \\ 5.3341943 \\ 5.2797589 \\ 5.4121642 \\ 5.2946822 \\ 5.3040424 \\ 5.2611035 \\ 5.2833598 \\ 5.2564085 \\ 4.6508039 \\ 5.2922701 \\ 5.2867532 \\ 5.3209659 \\ 5.3058017 \\ 5.4016978 \\ 5.3327447 \\ 5.2962531 \\ 5.2705634 \\ 5.2726858 \\ 5.2998109 \\ 5.3009345 \\ 5.2736147 \\ 5.2993244 \\ 5.3009664 \\ 5.3137350 \\ 5.2985355 \\ 4.6844890 \\ 5.3123753 \\ 5.3003872 \\ 5.3289358 \\ 5.2884876 \\ 5.2704741 \\ 5.3096308 \\ 5.2967709 \\ 5.2690516 \\ 5.2748875 \\ 5.3058519 \\ 5.3379861 \\ 5.3147633 \\ 5.2991529 \\ 5.2874218 \\ 5.2940955 \\ 5.3501076 \\ 4.6469366 \\ 5.3839911 \\ 5.3016371 \\ 5.2680588 \\ 5.3065642 \\ 5.3365650 \\ 5.2982190 \\ 5.3054208 \\ 5.3640045 \\ 5.2819036 \\ 5.2753445 \\ 5.2874061 \\ 5.2927159 \\ 5.2768291 \\ 5.2913053 \\ 5.3018164 \\ 5.3222204 \\ 4.6911073 \\ 5.2944582 \\ 5.2686390 \\ 5.2833640 \\ 5.2603199 \\ 5.3253589 \\ 5.2863418 \\ 5.2965512 \\ 5.3433553 \\ 5.3005229 \\ 5.3027043 \\ 5.3013793 \\ 5.2513157 \\ 5.2894287 \\ 5.3301480 \\ 5.2918344 \\ 5.3291739 \\ 4.6376035 \\ 5.2884117 \\ 5.2672214 \\ 5.3133938 \\ 5.2839877 \\ 5.2664365 \\ 5.2581830 \\ 5.3073617 \\ 5.2850742 \\
//runDeterministicGenerationAndSquash_10000_deps4_b1000_s10000_true_PersistentStorage(101)
//6.2451762 \\ 6.1769026 \\ 6.1706296 \\ 6.1344542 \\ 6.1672738 \\ 6.1024318 \\ 6.1077858 \\ 6.1408784 \\ 6.1129969 \\ 6.1421381 \\ 6.1343505 \\ 6.1135467 \\ 6.1142907 \\ 6.0939703 \\ 6.1368551 \\ 6.1676446 \\ 6.0997769 \\ 6.1433729 \\ 6.1137770 \\ 6.1368913 \\ 6.0718001 \\ 6.1814470 \\ 6.1140781 \\ 6.1152968 \\ 6.1174281 \\ 6.1369572 \\ 6.1523526 \\ 6.1365535 \\ 6.1571576 \\ 6.1505433 \\ 6.0990217 \\ 6.1868759 \\ 6.1089834 \\ 6.1179964 \\ 6.1281729 \\ 6.1770493 \\ 6.1368470 \\ 6.1254482 \\ 6.1258567 \\ 6.1945830 \\ 6.1111410 \\ 6.1176027 \\ 6.0928987 \\ 6.1108688 \\ 6.1771823 \\ 6.1222480 \\ 6.1387133 \\ 6.0895563 \\ 6.1382480 \\ 6.1412029 \\ 6.1314723 \\ 6.1356128 \\ 6.0848311 \\ 6.1443078 \\ 6.0735165 \\ 6.1226516 \\ 6.0922302 \\ 6.0881227 \\ 6.1403517 \\ 6.1666245 \\ 6.1251150 \\ 6.1089487 \\ 6.0966562 \\ 6.1472581 \\ 6.1299260 \\ 6.1466890 \\ 6.1206102 \\ 6.0918188 \\ 6.1120578 \\ 6.0897890 \\ 6.2145578 \\ 6.1092492 \\ 6.1097669 \\ 6.1237461 \\ 6.1056936 \\ 6.2426295 \\ 6.1136069 \\ 6.1225371 \\ 6.1760403 \\ 6.0932744 \\ 6.2020044 \\ 6.0813803 \\ 6.0953675 \\ 6.1497914 \\ 6.1129122 \\ 6.1319628 \\ 6.1570673 \\ 6.1314525 \\ 6.1192973 \\ 6.1145044 \\ 6.1256789 \\ 6.0877429 \\ 6.1149999 \\ 6.1892429 \\ 6.0751470 \\ 6.1224516 \\ 6.0978770 \\ 6.0973389 \\ 6.1271381 \\ 6.1171547 \\ 6.1488814 \\
//runDeterministicGenerationAndSquash_10000_deps10_b10_s10_true_PersistentStorage(101)
//5.4996084 \\ 5.5105605 \\ 5.4615805 \\ 5.5147873 \\ 5.4860050 \\ 5.5620094 \\ 5.5232502 \\ 5.4952232 \\ 5.4546220 \\ 5.5513150 \\ 5.4911338 \\ 5.4758169 \\ 5.5403616 \\ 5.4601228 \\ 7.2806796 \\ 6.0216594 \\ 6.0363239 \\ 6.0848325 \\ 6.2019822 \\ 6.0904412 \\ 6.0255413 \\ 6.2140009 \\ 6.0234224 \\ 5.9969991 \\ 6.0295258 \\ 6.6238919 \\ 6.0442130 \\ 6.0591848 \\ 5.9989815 \\ 6.1148860 \\ 5.9999718 \\ 6.0949421 \\ 6.5547961 \\ 6.0048391 \\ 6.0312116 \\ 6.0009623 \\ 6.5831770 \\ 6.1000942 \\ 6.0772788 \\ 5.9960152 \\ 6.6055359 \\ 6.0498606 \\ 6.0148101 \\ 6.0439999 \\ 6.6638039 \\ 6.1290594 \\ 6.0475578 \\ 6.5983374 \\ 6.0951034 \\ 6.1938591 \\ 6.6939109 \\ 6.0893459 \\ 6.6615727 \\ 6.0788566 \\ 6.0296328 \\ 6.6023288 \\ 6.0832324 \\ 6.6917282 \\ 6.0424855 \\ 6.6405667 \\ 6.0737575 \\ 6.8178516 \\ 6.1064950 \\ 6.6349163 \\ 6.0535564 \\ 6.6785240 \\ 6.6497573 \\ 6.0832683 \\ 6.6537691 \\ 6.6863581 \\ 6.0983746 \\ 6.8749860 \\ 6.6607296 \\ 6.0637878 \\ 6.7005221 \\ 6.6690056 \\ 6.6529386 \\ 6.1015710 \\ 6.7159401 \\ 6.6952665 \\ 6.6982154 \\ 6.7512580 \\ 6.7147641 \\ 6.7078826 \\ 6.7001666 \\ 6.7065368 \\ 6.7101198 \\ 6.7626087 \\ 6.6791518 \\ 6.7538750 \\ 6.8621451 \\ 6.7445218 \\ 6.7326308 \\ 6.7883373 \\ 7.3413513 \\ 6.8107713 \\ 6.8044615 \\ 7.4662711 \\ 6.8324835 \\ 6.8422611 \\ 7.4420089 \\
//runDeterministicGenerationAndSquash_10000_deps4_b10_s10_true_PersistentStorage(101)
//4.9959240 \\ 5.0156625 \\ 5.0367843 \\ 5.0735006 \\ 5.0019062 \\ 5.0059551 \\ 5.1226946 \\ 5.0357480 \\ 4.9826540 \\ 5.0824455 \\ 5.0104846 \\ 5.1146787 \\ 5.0345549 \\ 4.9949134 \\ 5.0320037 \\ 5.0168303 \\ 5.0273924 \\ 5.0082428 \\ 5.0511457 \\ 5.0551370 \\ 5.0294483 \\ 5.0082190 \\ 5.0550549 \\ 5.0576111 \\ 5.2018872 \\ 4.9969832 \\ 4.9700762 \\ 4.9854058 \\ 5.0050959 \\ 5.1129618 \\ 4.9965778 \\ 5.1242301 \\ 5.1168768 \\ 5.0258876 \\ 5.1442245 \\ 5.1290291 \\ 5.0515826 \\ 5.0057784 \\ 5.0275347 \\ 5.1266229 \\ 5.0064844 \\ 4.9983745 \\ 5.0208588 \\ 5.1970195 \\ 5.1302178 \\ 5.0351759 \\ 4.9593355 \\ 5.1665955 \\ 5.0280781 \\ 5.0439597 \\ 5.0895200 \\ 5.0306765 \\ 5.0109138 \\ 4.9945292 \\ 4.9684360 \\ 4.9983694 \\ 5.0938037 \\ 5.0019631 \\ 5.0485609 \\ 5.1604191 \\ 5.0194759 \\ 5.0228003 \\ 5.0654557 \\ 5.0129645 \\ 5.1670888 \\ 5.0193180 \\ 5.0229451 \\ 5.0140234 \\ 5.1086727 \\ 5.2308863 \\ 5.0261362 \\ 5.0870632 \\ 5.0354829 \\ 5.0210702 \\ 5.0245595 \\ 5.0162537 \\ 5.0109455 \\ 4.9993860 \\ 5.0105409 \\ 5.1707676 \\ 5.0140471 \\ 5.0642263 \\ 5.1874668 \\ 5.0354992 \\ 5.0118151 \\ 4.9962266 \\ 5.0281089 \\ 5.0841696 \\ 5.0326330 \\ 5.0172807 \\ 5.0121204 \\ 5.0469290 \\ 5.0037644 \\ 5.1826207 \\ 5.0541502 \\ 5.0288043 \\ 5.0239130 \\ 5.0091126 \\ 5.0789099 \\ 4.9993070 \\ 5.0390233 \\
//=== End End EndEnd End End ===
//
//Process finished with exit code 0














//NOT OLD, BUT OLD PARAMS

//runDeterministicGenerationAndSquash_5000_deps5_b500_s1_true_PersistentStorage
//ended(numberOfSquashes=11, numberOfBlocks=10, numberOfGeneratedTx=5000)
//RAW STORAGE REQUIREMENTS: after: 2097168bytes, max: 2170293bytes
//PERSISTED TRANSACTION COUNT: after: 4380txs, max: 4430txs
//runDeterministicGenerationAndSquash_5000_deps5_b500_s10_true_PersistentStorage
//ended(numberOfSquashes=11, numberOfBlocks=10, numberOfGeneratedTx=5000)
//RAW STORAGE REQUIREMENTS: after: 2097168bytes, max: 2170293bytes
//PERSISTED TRANSACTION COUNT: after: 4380txs, max: 4430txs
//runDeterministicGenerationAndSquash_5000_deps5_b500_s100_true_PersistentStorage
//ended(numberOfSquashes=11, numberOfBlocks=10, numberOfGeneratedTx=5000)
//RAW STORAGE REQUIREMENTS: after: 2097168bytes, max: 2170293bytes
//PERSISTED TRANSACTION COUNT: after: 4380txs, max: 4430txs
//runDeterministicGenerationAndSquash_5000_deps5_b500_s1000_true_PersistentStorage
//ended(numberOfSquashes=6, numberOfBlocks=10, numberOfGeneratedTx=5000)
//RAW STORAGE REQUIREMENTS: after: 2097168bytes, max: 2170293bytes
//PERSISTED TRANSACTION COUNT: after: 4380txs, max: 4462txs
//runDeterministicGenerationAndSquash_5000_deps5_b500_s10000_true_PersistentStorage
//ended(numberOfSquashes=1, numberOfBlocks=10, numberOfGeneratedTx=5000)
//RAW STORAGE REQUIREMENTS: after: 3145744bytes, max: 3145744bytes
//PERSISTED TRANSACTION COUNT: after: 4380txs, max: 4802txs
//runDeterministicGenerationAndSquash_5000_deps5_b10_s1_true_PersistentStorage
//ended(numberOfSquashes=501, numberOfBlocks=500, numberOfGeneratedTx=5000)
//RAW STORAGE REQUIREMENTS: after: 4194320bytes, max: 4195767bytes
//PERSISTED TRANSACTION COUNT: after: 4424txs, max: 4425txs
//runDeterministicGenerationAndSquash_5000_deps5_b10_s10_true_PersistentStorage
//ended(numberOfSquashes=501, numberOfBlocks=500, numberOfGeneratedTx=5000)
//RAW STORAGE REQUIREMENTS: after: 4194320bytes, max: 4195767bytes
//PERSISTED TRANSACTION COUNT: after: 4424txs, max: 4425txs
//runDeterministicGenerationAndSquash_5000_deps5_b10_s100_true_PersistentStorage
//ended(numberOfSquashes=51, numberOfBlocks=500, numberOfGeneratedTx=5000)
//RAW STORAGE REQUIREMENTS: after: 3145744bytes, max: 3147165bytes
//PERSISTED TRANSACTION COUNT: after: 4424txs, max: 4430txs
//runDeterministicGenerationAndSquash_5000_deps5_b10_s1000_true_PersistentStorage
//ended(numberOfSquashes=6, numberOfBlocks=500, numberOfGeneratedTx=5000)
//RAW STORAGE REQUIREMENTS: after: 3145744bytes, max: 3147165bytes
//PERSISTED TRANSACTION COUNT: after: 4424txs, max: 4487txs
//runDeterministicGenerationAndSquash_5000_deps5_b10_s10000_true_PersistentStorage
//ended(numberOfSquashes=1, numberOfBlocks=500, numberOfGeneratedTx=5000)
//RAW STORAGE REQUIREMENTS: after: 3145744bytes, max: 3145744bytes
//PERSISTED TRANSACTION COUNT: after: 4424txs, max: 4827txs
//runDeterministicGenerationAndSquash_5000_deps5_b1_s1_true_PersistentStorage
//ended(numberOfSquashes=5001, numberOfBlocks=5000, numberOfGeneratedTx=5000)
//RAW STORAGE REQUIREMENTS: after: 14724406bytes, max: 18784886bytes
//PERSISTED TRANSACTION COUNT: after: 4597txs, max: 4597txs
//runDeterministicGenerationAndSquash_5000_deps5_b1_s10_true_PersistentStorage
//ended(numberOfSquashes=501, numberOfBlocks=5000, numberOfGeneratedTx=5000)
//RAW STORAGE REQUIREMENTS: after: 9456593bytes, max: 10390385bytes
//PERSISTED TRANSACTION COUNT: after: 4597txs, max: 4598txs
//runDeterministicGenerationAndSquash_5000_deps5_b1_s100_true_PersistentStorage
//ended(numberOfSquashes=51, numberOfBlocks=5000, numberOfGeneratedTx=5000)
//RAW STORAGE REQUIREMENTS: after: 4194320bytes, max: 4194537bytes
//PERSISTED TRANSACTION COUNT: after: 4597txs, max: 4603txs
//runDeterministicGenerationAndSquash_5000_deps5_b1_s1000_true_PersistentStorage
//ended(numberOfSquashes=6, numberOfBlocks=5000, numberOfGeneratedTx=5000)
//RAW STORAGE REQUIREMENTS: after: 4194320bytes, max: 4194320bytes
//PERSISTED TRANSACTION COUNT: after: 4597txs, max: 4660txs
//runDeterministicGenerationAndSquash_5000_deps5_b1_s10000_true_PersistentStorage
//ended(numberOfSquashes=1, numberOfBlocks=5000, numberOfGeneratedTx=5000)
//RAW STORAGE REQUIREMENTS: after: 3145744bytes, max: 3145961bytes
//PERSISTED TRANSACTION COUNT: after: 4597txs, max: 5000txs
//runDeterministicGenerationAndSquash_5000_deps50_b10_s1_true_PersistentStorage
//ended(numberOfSquashes=501, numberOfBlocks=500, numberOfGeneratedTx=5000)
//RAW STORAGE REQUIREMENTS: after: 22351417bytes, max: 23394173bytes
//PERSISTED TRANSACTION COUNT: after: 4455txs, max: 4456txs
//runDeterministicGenerationAndSquash_5000_deps50_b10_s10_true_PersistentStorage
//ended(numberOfSquashes=501, numberOfBlocks=500, numberOfGeneratedTx=5000)
//RAW STORAGE REQUIREMENTS: after: 22351417bytes, max: 23394173bytes
//PERSISTED TRANSACTION COUNT: after: 4455txs, max: 4456txs
//runDeterministicGenerationAndSquash_5000_deps50_b10_s100_true_PersistentStorage
//ended(numberOfSquashes=51, numberOfBlocks=500, numberOfGeneratedTx=5000)
//RAW STORAGE REQUIREMENTS: after: 5242896bytes, max: 5244317bytes
//PERSISTED TRANSACTION COUNT: after: 4455txs, max: 4461txs
//runDeterministicGenerationAndSquash_5000_deps50_b10_s1000_true_PersistentStorage
//ended(numberOfSquashes=6, numberOfBlocks=500, numberOfGeneratedTx=5000)
//RAW STORAGE REQUIREMENTS: after: 3145744bytes, max: 3147165bytes
//PERSISTED TRANSACTION COUNT: after: 4455txs, max: 4518txs
//runDeterministicGenerationAndSquash_5000_deps50_b10_s10000_true_PersistentStorage
//ended(numberOfSquashes=1, numberOfBlocks=500, numberOfGeneratedTx=5000)
//RAW STORAGE REQUIREMENTS: after: 3145744bytes, max: 3145744bytes
//PERSISTED TRANSACTION COUNT: after: 4455txs, max: 4836txs
//runDeterministicGenerationAndSquash_5000_deps0_b10_s1_true_PersistentStorage
//ended(numberOfSquashes=501, numberOfBlocks=500, numberOfGeneratedTx=5000)
//RAW STORAGE REQUIREMENTS: after: 2097168bytes, max: 2098535bytes
//PERSISTED TRANSACTION COUNT: after: 5000txs, max: 5000txs
//runDeterministicGenerationAndSquash_5000_deps0_b10_s10_true_PersistentStorage
//ended(numberOfSquashes=501, numberOfBlocks=500, numberOfGeneratedTx=5000)
//RAW STORAGE REQUIREMENTS: after: 2097168bytes, max: 2098535bytes
//PERSISTED TRANSACTION COUNT: after: 5000txs, max: 5000txs
//runDeterministicGenerationAndSquash_5000_deps0_b10_s100_true_PersistentStorage
//ended(numberOfSquashes=51, numberOfBlocks=500, numberOfGeneratedTx=5000)
//RAW STORAGE REQUIREMENTS: after: 2097168bytes, max: 2098535bytes
//PERSISTED TRANSACTION COUNT: after: 5000txs, max: 5000txs
//runDeterministicGenerationAndSquash_5000_deps0_b10_s1000_true_PersistentStorage
//ended(numberOfSquashes=6, numberOfBlocks=500, numberOfGeneratedTx=5000)
//RAW STORAGE REQUIREMENTS: after: 2097168bytes, max: 2098535bytes
//PERSISTED TRANSACTION COUNT: after: 5000txs, max: 5000txs
//runDeterministicGenerationAndSquash_5000_deps0_b10_s10000_true_PersistentStorage
//ended(numberOfSquashes=1, numberOfBlocks=500, numberOfGeneratedTx=5000)
//RAW STORAGE REQUIREMENTS: after: 2097168bytes, max: 2098535bytes
//PERSISTED TRANSACTION COUNT: after: 5000txs, max: 5000txs
//runDeterministicGenerationAndSquash_500_deps5_b10_s1_true_PersistentStorage
//ended(numberOfSquashes=51, numberOfBlocks=50, numberOfGeneratedTx=500)
//RAW STORAGE REQUIREMENTS: after: 2097168bytes, max: 2098671bytes
//PERSISTED TRANSACTION COUNT: after: 444txs, max: 446txs
//runDeterministicGenerationAndSquash_500_deps5_b10_s10_true_PersistentStorage
//ended(numberOfSquashes=51, numberOfBlocks=50, numberOfGeneratedTx=500)
//RAW STORAGE REQUIREMENTS: after: 2097168bytes, max: 2098671bytes
//PERSISTED TRANSACTION COUNT: after: 444txs, max: 446txs
//runDeterministicGenerationAndSquash_500_deps5_b10_s100_true_PersistentStorage
//ended(numberOfSquashes=6, numberOfBlocks=50, numberOfGeneratedTx=500)
//RAW STORAGE REQUIREMENTS: after: 2097168bytes, max: 2098671bytes
//PERSISTED TRANSACTION COUNT: after: 444txs, max: 452txs
//runDeterministicGenerationAndSquash_500_deps5_b10_s1000_true_PersistentStorage
//ended(numberOfSquashes=1, numberOfBlocks=50, numberOfGeneratedTx=500)
//RAW STORAGE REQUIREMENTS: after: 2097168bytes, max: 2098671bytes
//PERSISTED TRANSACTION COUNT: after: 444txs, max: 483txs
//runDeterministicGenerationAndSquash_500_deps5_b10_s10000_true_PersistentStorage
//ended(numberOfSquashes=1, numberOfBlocks=50, numberOfGeneratedTx=500)
//RAW STORAGE REQUIREMENTS: after: 2097168bytes, max: 2098671bytes
//PERSISTED TRANSACTION COUNT: after: 444txs, max: 483txs
//=== Printing ------- Calls ===
//app verify - 690100 - av: 0.0000009s - max: 0.0002487s - min: 0.0000002s
//persist new block - 656000 - av: 0.0000385s - max: 0.1519272s - min: 0.0000028s
//squash verify - 656600 - av: 0.0000949s - max: 0.0895907s - min: 0.0000020s
//runDeterministicGenerationAndSquash_5000_deps0_b10_s10_true_PersistentStorage - only squash - 10020 - av: 0.0001381s - max: 0.0082495s - min: 0.0000419s
//runDeterministicGenerationAndSquash_5000_deps0_b10_s1_true_PersistentStorage - only squash - 10020 - av: 0.0001397s - max: 0.0098130s - min: 0.0000419s
//runDeterministicGenerationAndSquash_5000_deps0_b10_s100_true_PersistentStorage - only squash - 1020 - av: 0.0001442s - max: 0.0005653s - min: 0.0000436s
//runDeterministicGenerationAndSquash_5000_deps0_b10_s1000_true_PersistentStorage - only squash - 120 - av: 0.0001538s - max: 0.0005152s - min: 0.0000510s
//runDeterministicGenerationAndSquash_5000_deps5_b10_s10_true_PersistentStorage - only squash - 10020 - av: 0.0002043s - max: 0.0595262s - min: 0.0000453s
//runDeterministicGenerationAndSquash_500_deps5_b10_s10_true_PersistentStorage - only squash - 1020 - av: 0.0002302s - max: 0.0039135s - min: 0.0000425s
//runDeterministicGenerationAndSquash_500_deps5_b10_s1_true_PersistentStorage - only squash - 1020 - av: 0.0002369s - max: 0.0040495s - min: 0.0000429s
//runDeterministicGenerationAndSquash_5000_deps5_b10_s1_true_PersistentStorage - only squash - 10020 - av: 0.0002438s - max: 0.0751215s - min: 0.0000454s
//runDeterministicGenerationAndSquash_500_deps5_b10_s100_true_PersistentStorage - only squash - 120 - av: 0.0003476s - max: 0.0010352s - min: 0.0002300s
//runDeterministicGenerationAndSquash_5000_deps5_b10_s100_true_PersistentStorage - only squash - 1020 - av: 0.0003512s - max: 0.0037439s - min: 0.0002283s
//runDeterministicGenerationAndSquash_5000_deps0_b10_s10000_true_PersistentStorage - only squash - 20 - av: 0.0003760s - max: 0.0005167s - min: 0.0002277s
//runDeterministicGenerationAndSquash_5000_deps5_b1_s1_true_PersistentStorage - only squash - 100020 - av: 0.0003883s - max: 0.0565151s - min: 0.0000141s
//runDeterministicGenerationAndSquash_5000_deps5_b1_s10_true_PersistentStorage - only squash - 10020 - av: 0.0004068s - max: 0.1416882s - min: 0.0000154s
//runDeterministicGenerationAndSquash_5000_deps50_b10_s10_true_PersistentStorage - only squash - 10020 - av: 0.0007741s - max: 0.1559377s - min: 0.0000427s
//runDeterministicGenerationAndSquash_5000_deps50_b10_s1_true_PersistentStorage - only squash - 10020 - av: 0.0008013s - max: 0.1400172s - min: 0.0000423s
//runDeterministicGenerationAndSquash_5000_deps50_b10_s100_true_PersistentStorage - only squash - 1020 - av: 0.0009448s - max: 0.0368219s - min: 0.0000520s
//runDeterministicGenerationAndSquash_5000_deps5_b1_s100_true_PersistentStorage - only squash - 1020 - av: 0.0009765s - max: 0.0379794s - min: 0.0002347s
//runDeterministicGenerationAndSquash_500_deps5_b10_s1000_true_PersistentStorage - only squash - 20 - av: 0.0015208s - max: 0.0029093s - min: 0.0013173s
//introduceChanges - 177800 - av: 0.0016068s - max: 0.1551899s - min: 0.0000010s
//runDeterministicGenerationAndSquash_500_deps5_b10_s10000_true_PersistentStorage - only squash - 20 - av: 0.0023014s - max: 0.0089926s - min: 0.0012777s
//runDeterministicGenerationAndSquash_5000_deps5_b10_s1000_true_PersistentStorage - only squash - 120 - av: 0.0023989s - max: 0.0833871s - min: 0.0002406s
//runDeterministicGenerationAndSquash_5000_deps50_b10_s1000_true_PersistentStorage - only squash - 120 - av: 0.0026730s - max: 0.0224905s - min: 0.0002405s
//runDeterministicGenerationAndSquash_5000_deps5_b500_s1_true_PersistentStorage - only squash - 220 - av: 0.0027699s - max: 0.3054408s - min: 0.0002983s
//runDeterministicGenerationAndSquash_5000_deps5_b500_s10_true_PersistentStorage - only squash - 220 - av: 0.0032888s - max: 0.1599813s - min: 0.0002435s
//runDeterministicGenerationAndSquash_5000_deps5_b500_s100_true_PersistentStorage - only squash - 220 - av: 0.0033087s - max: 0.0622016s - min: 0.0002442s
//runDeterministicGenerationAndSquash_5000_deps5_b500_s1000_true_PersistentStorage - only squash - 120 - av: 0.0038874s - max: 0.0615484s - min: 0.0002632s
//runDeterministicGenerationAndSquash_5000_deps5_b1_s1000_true_PersistentStorage - only squash - 120 - av: 0.0044039s - max: 0.0205631s - min: 0.0002442s
//runDeterministicGenerationAndSquash_5000_deps50_b10_s10000_true_PersistentStorage - only squash - 20 - av: 0.0166808s - max: 0.0390645s - min: 0.0118005s
//runDeterministicGenerationAndSquash_5000_deps5_b500_s10000_true_PersistentStorage - only squash - 20 - av: 0.0172884s - max: 0.0322401s - min: 0.0135111s
//runDeterministicGenerationAndSquash_5000_deps5_b10_s10000_true_PersistentStorage - only squash - 20 - av: 0.0200011s - max: 0.0307934s - min: 0.0124935s
//runDeterministicGenerationAndSquash_5000_deps5_b1_s10000_true_PersistentStorage - only squash - 20 - av: 0.0283724s - max: 0.0567960s - min: 0.0249651s
//runDeterministicGenerationAndSquash_500_deps5_b10_s10000_true_PersistentStorage - 20 - av: 0.3203437s - max: 0.4482979s - min: 0.2569146s
//runDeterministicGenerationAndSquash_500_deps5_b10_s10_true_PersistentStorage - 20 - av: 0.3364865s - max: 0.4122993s - min: 0.2594117s
//runDeterministicGenerationAndSquash_500_deps5_b10_s1000_true_PersistentStorage - 20 - av: 0.3420077s - max: 0.4142333s - min: 0.2432929s
//runDeterministicGenerationAndSquash_500_deps5_b10_s100_true_PersistentStorage - 20 - av: 0.3424924s - max: 0.4212319s - min: 0.2869951s
//runDeterministicGenerationAndSquash_500_deps5_b10_s1_true_PersistentStorage - 20 - av: 0.3929448s - max: 0.4242529s - min: 0.2585715s
//runDeterministicGenerationAndSquash_5000_deps0_b10_s100_true_PersistentStorage - 20 - av: 3.2159635s - max: 3.6581682s - min: 2.9790802s
//runDeterministicGenerationAndSquash_5000_deps0_b10_s1_true_PersistentStorage - 20 - av: 3.2290280s - max: 3.7672703s - min: 2.9679714s
//runDeterministicGenerationAndSquash_5000_deps5_b10_s100_true_PersistentStorage - 20 - av: 3.2364794s - max: 3.5828496s - min: 3.0115177s
//runDeterministicGenerationAndSquash_5000_deps5_b10_s10_true_PersistentStorage - 20 - av: 3.2388866s - max: 3.6516494s - min: 2.9961899s
//runDeterministicGenerationAndSquash_5000_deps0_b10_s1000_true_PersistentStorage - 20 - av: 3.2913706s - max: 3.5926183s - min: 3.0200588s
//runDeterministicGenerationAndSquash_5000_deps5_b10_s1000_true_PersistentStorage - 20 - av: 3.3202940s - max: 3.6764381s - min: 2.9522216s
//runDeterministicGenerationAndSquash_5000_deps5_b10_s1_true_PersistentStorage - 20 - av: 3.3598189s - max: 3.5098320s - min: 2.9482951s
//runDeterministicGenerationAndSquash_5000_deps50_b10_s100_true_PersistentStorage - 20 - av: 3.3657374s - max: 3.8108528s - min: 3.1274850s
//runDeterministicGenerationAndSquash_5000_deps5_b1_s100_true_PersistentStorage - 20 - av: 3.3742349s - max: 3.8145700s - min: 3.1913731s
//runDeterministicGenerationAndSquash_5000_deps5_b10_s10000_true_PersistentStorage - 20 - av: 3.3906197s - max: 3.6249242s - min: 3.0746048s
//runDeterministicGenerationAndSquash_5000_deps0_b10_s10000_true_PersistentStorage - 20 - av: 3.3977935s - max: 3.7091647s - min: 3.0905922s
//runDeterministicGenerationAndSquash_5000_deps0_b10_s10_true_PersistentStorage - 20 - av: 3.4583704s - max: 3.5950127s - min: 2.9531228s
//runDeterministicGenerationAndSquash_5000_deps50_b10_s10000_true_PersistentStorage - 20 - av: 3.4635301s - max: 3.7294610s - min: 3.1508202s
//runDeterministicGenerationAndSquash_5000_deps50_b10_s1000_true_PersistentStorage - 20 - av: 3.5481304s - max: 3.7721817s - min: 2.9984918s
//runDeterministicGenerationAndSquash_5000_deps5_b500_s1000_true_PersistentStorage - 20 - av: 3.5640271s - max: 4.1554497s - min: 3.2761026s
//runDeterministicGenerationAndSquash_5000_deps5_b1_s1000_true_PersistentStorage - 20 - av: 3.5745878s - max: 4.0582185s - min: 3.4934600s
//runDeterministicGenerationAndSquash_5000_deps5_b500_s100_true_PersistentStorage - 20 - av: 3.7155322s - max: 3.9623007s - min: 3.3699419s
//runDeterministicGenerationAndSquash_5000_deps5_b500_s10000_true_PersistentStorage - 20 - av: 3.7633015s - max: 4.0598265s - min: 3.4872120s
//runDeterministicGenerationAndSquash_5000_deps5_b500_s1_true_PersistentStorage - 20 - av: 3.8123459s - max: 4.0854376s - min: 3.3070615s
//runDeterministicGenerationAndSquash_5000_deps5_b500_s10_true_PersistentStorage - 20 - av: 3.8694365s - max: 4.3416983s - min: 3.5123350s
//runDeterministicGenerationAndSquash_5000_deps5_b1_s10_true_PersistentStorage - 20 - av: 3.9661582s - max: 4.3546356s - min: 3.6723489s
//runDeterministicGenerationAndSquash_5000_deps5_b1_s10000_true_PersistentStorage - 20 - av: 4.2708780s - max: 4.9471068s - min: 4.1794532s
//runDeterministicGenerationAndSquash_5000_deps5_b1_s1_true_PersistentStorage - 20 - av: 4.5473708s - max: 4.9508866s - min: 4.1398722s
//runDeterministicGenerationAndSquash_5000_deps50_b10_s1_true_PersistentStorage - 20 - av: 4.6791683s - max: 5.1291353s - min: 4.4042916s
//runDeterministicGenerationAndSquash_5000_deps50_b10_s10_true_PersistentStorage - 20 - av: 4.8340874s - max: 5.2348764s - min: 4.3860172s
//=== End End EndEnd End End ===
//=== Printing ------- Calls ===
//runDeterministicGenerationAndSquash_5000_deps5_b1_s1_true_PersistentStorage(20)
//4.5839385 \\ 4.4741989 \\ 4.6368611 \\ 4.5100277 \\ 4.6501460 \\ 4.3796320 \\ 4.3180300 \\ 4.6267301 \\ 4.4535813 \\ 4.5073325 \\ 4.6530960 \\ 4.9508899 \\ 4.7725719 \\ 4.4284549 \\ 4.6635060 \\ 4.4856185 \\ 4.1398753 \\ 4.7690267 \\ 4.5357197 \\
//runDeterministicGenerationAndSquash_5000_deps5_b500_s100_true_PersistentStorage(20)
//3.7384429 \\ 3.6159535 \\ 3.9623041 \\ 3.6557056 \\ 3.6738665 \\ 3.6162385 \\ 3.9409873 \\ 3.7148887 \\ 3.4971657 \\ 3.6799522 \\ 3.6493169 \\ 3.6748062 \\ 3.6271639 \\ 3.8200777 \\ 3.5392367 \\ 3.6632310 \\ 3.5922052 \\ 3.5760287 \\ 3.3699450 \\ 3.9468675 \\
//runDeterministicGenerationAndSquash_5000_deps5_b10_s10000_true_PersistentStorage(20)
//3.4417787 \\ 3.3506149 \\ 3.2227225 \\ 3.2507247 \\ 3.2699144 \\ 3.0746076 \\ 3.3611491 \\ 3.2147726 \\ 3.4689856 \\ 3.3140341 \\ 3.2924865 \\ 3.3633949 \\ 3.4400324 \\ 3.2156394 \\ 3.6249267 \\ 3.4981577 \\ 3.3417533 \\ 3.4828216 \\ 3.2978775 \\ 3.4087678 \\
//runDeterministicGenerationAndSquash_5000_deps5_b10_s1_true_PersistentStorage(20)
//3.3216645 \\ 3.0188836 \\ 2.9482981 \\ 3.2122260 \\ 3.2623914 \\ 3.3011698 \\ 3.4356447 \\ 3.0768915 \\ 3.0729535 \\ 3.4608289 \\ 3.4748940 \\ 3.3242799 \\ 3.5098348 \\ 3.2753653 \\ 3.2796782 \\ 3.3085332 \\ 3.2851139 \\ 3.3500886 \\ 3.4454010 \\ 3.3346905 \\
//runDeterministicGenerationAndSquash_5000_deps0_b10_s10000_true_PersistentStorage(20)
//3.3226795 \\ 3.1765410 \\ 3.2102198 \\ 3.4918747 \\ 3.3783941 \\ 3.4613672 \\ 3.5208863 \\ 3.2827596 \\ 3.2184245 \\ 3.1589318 \\ 3.0905937 \\ 3.7091673 \\ 3.3987366 \\ 3.5661387 \\ 3.4238255 \\ 3.2517989 \\ 3.1905136 \\ 3.4060605 \\ 3.2931829 \\ 3.4793352 \\
//runDeterministicGenerationAndSquash_5000_deps50_b10_s10000_true_PersistentStorage(20)
//3.7294624 \\ 3.4757508 \\ 3.5444737 \\ 3.2295570 \\ 3.3713387 \\ 3.2348100 \\ 3.5557269 \\ 3.3623559 \\ 3.4917903 \\ 3.3777258 \\ 3.3386752 \\ 3.6655247 \\ 3.1508222 \\ 3.4114339 \\ 3.3804200 \\ 3.4774377 \\ 3.3527076 \\ 3.4835030 \\ 3.3691017 \\ 3.5241495 \\
//runDeterministicGenerationAndSquash_5000_deps50_b10_s1_true_PersistentStorage(20)
//4.5495061 \\ 4.6906266 \\ 4.5344615 \\ 4.8427649 \\ 4.8908070 \\ 4.7129571 \\ 4.7036772 \\ 5.1291369 \\ 4.8173853 \\ 4.8100388 \\ 4.6905237 \\ 4.8922514 \\ 4.6726323 \\ 4.4911838 \\ 4.8168159 \\ 4.6300431 \\ 4.6021479 \\ 4.4042928 \\ 4.9300099 \\ 4.6326815 \\
//runDeterministicGenerationAndSquash_5000_deps5_b500_s1000_true_PersistentStorage(20)
//3.6364334 \\ 3.6344641 \\ 3.5515694 \\ 3.5049292 \\ 3.5481484 \\ 3.6203115 \\ 3.6593547 \\ 3.8073146 \\ 3.2761127 \\ 3.8044577 \\ 3.3838293 \\ 3.7432368 \\ 4.1554601 \\ 3.6374038 \\ 3.7325706 \\ 3.4722177 \\ 3.4667774 \\ 3.8823858 \\ 3.8098307 \\ 3.3678894 \\
//runDeterministicGenerationAndSquash_5000_deps50_b10_s100_true_PersistentStorage(20)
//3.8108542 \\ 3.1690914 \\ 3.4832762 \\ 3.1274869 \\ 3.4540498 \\ 3.3989949 \\ 3.4124405 \\ 3.4439171 \\ 3.3965560 \\ 3.3971539 \\ 3.3972199 \\ 3.5144334 \\ 3.2548303 \\ 3.2268879 \\ 3.7250235 \\ 3.3586402 \\ 3.3196344 \\ 3.5327952 \\ 3.3747896 \\ 3.3167488 \\
//runDeterministicGenerationAndSquash_5000_deps50_b10_s1000_true_PersistentStorage(20)
//3.2179319 \\ 3.2375896 \\ 3.6525110 \\ 3.3849185 \\ 3.5872716 \\ 3.1396947 \\ 3.3835943 \\ 3.4540660 \\ 3.4382903 \\ 3.3392146 \\ 3.3656850 \\ 3.7721828 \\ 3.2391542 \\ 3.4145011 \\ 3.1744203 \\ 3.6763059 \\ 3.6138247 \\ 2.9984928 \\ 3.6201692 \\ 3.6492901 \\
//runDeterministicGenerationAndSquash_5000_deps0_b10_s10_true_PersistentStorage(20)
//3.3801226 \\ 3.1429284 \\ 3.3846358 \\ 3.2303201 \\ 2.9531249 \\ 3.2442509 \\ 3.4958943 \\ 3.3002737 \\ 3.2990747 \\ 3.2170985 \\ 3.1960606 \\ 3.5911476 \\ 3.4058177 \\ 3.4727338 \\ 3.3861004 \\ 3.5950147 \\ 3.4575113 \\ 3.0606011 \\ 3.4366652 \\ 3.5630550 \\
//runDeterministicGenerationAndSquash_5000_deps5_b500_s1_true_PersistentStorage(20)
//4.0433998 \\ 3.4825613 \\ 3.6274016 \\ 3.6141816 \\ 3.5094723 \\ 4.0207632 \\ 3.5089766 \\ 3.8459871 \\ 3.9639616 \\ 3.5948252 \\ 3.3070638 \\ 3.5841211 \\ 3.7455571 \\ 3.3912684 \\ 3.8213306 \\ 4.0854473 \\ 3.8370566 \\ 3.5563538 \\ 3.9276515 \\ 3.8074184 \\
//runDeterministicGenerationAndSquash_500_deps5_b10_s1_true_PersistentStorage(20)
//0.2735542 \\ 0.2974172 \\ 0.3264710 \\ 0.3827835 \\ 0.3102196 \\ 0.3188955 \\ 0.2863422 \\ 0.3148125 \\ 0.3059844 \\ 0.3042417 \\ 0.2787210 \\ 0.2869584 \\ 0.2585729 \\ 0.2731712 \\ 0.3800754 \\ 0.2730592 \\ 0.3797916 \\ 0.3146187 \\ 0.3960286 \\ 0.4242540 \\
//runDeterministicGenerationAndSquash_5000_deps5_b500_s10_true_PersistentStorage(20)
//3.5222715 \\ 3.8103019 \\ 3.9784498 \\ 4.0272858 \\ 3.9039439 \\ 3.8895250 \\ 3.8102769 \\ 3.8426489 \\ 3.7429035 \\ 3.8601010 \\ 3.9913381 \\ 3.6333684 \\ 3.7974610 \\ 3.8624242 \\ 3.5123390 \\ 3.6803789 \\ 3.8287518 \\ 4.3417008 \\ 4.0942026 \\ 3.6684905 \\
//runDeterministicGenerationAndSquash_500_deps5_b10_s10000_true_PersistentStorage(20)
//0.3341935 \\ 0.2828384 \\ 0.2710257 \\ 0.2835903 \\ 0.3333122 \\ 0.3829558 \\ 0.3187859 \\ 0.2638425 \\ 0.2569166 \\ 0.3560424 \\ 0.2801176 \\ 0.3082426 \\ 0.4482996 \\ 0.3718706 \\ 0.2761169 \\ 0.3472932 \\ 0.3300287 \\ 0.3717147 \\ 0.3445224 \\ 0.2922307 \\
//runDeterministicGenerationAndSquash_5000_deps5_b1_s100_true_PersistentStorage(20)
//3.2836188 \\ 3.4013229 \\ 3.4344554 \\ 3.4739026 \\ 3.8145746 \\ 3.5622931 \\ 3.4978400 \\ 3.5323323 \\ 3.6677046 \\ 3.5148903 \\ 3.7617642 \\ 3.4328968 \\ 3.6347883 \\ 3.5515203 \\ 3.4044509 \\ 3.5550914 \\ 3.2472021 \\ 3.4013622 \\ 3.1913765 \\ 3.4563680 \\
//runDeterministicGenerationAndSquash_5000_deps5_b10_s100_true_PersistentStorage(20)
//3.3029719 \\ 3.0115205 \\ 3.2339485 \\ 3.0531937 \\ 3.3231900 \\ 3.2749130 \\ 3.0421788 \\ 3.3705945 \\ 3.3292171 \\ 3.4796552 \\ 3.5243521 \\ 3.2706340 \\ 3.4969226 \\ 3.4258316 \\ 3.5051163 \\ 3.0169070 \\ 3.4306728 \\ 3.5828526 \\ 3.4396243 \\ 3.0233923 \\
//runDeterministicGenerationAndSquash_5000_deps5_b10_s10_true_PersistentStorage(20)
//3.0621571 \\ 3.1746122 \\ 3.6516523 \\ 3.5793155 \\ 3.4431734 \\ 3.2456593 \\ 3.5178177 \\ 3.2474403 \\ 3.2348267 \\ 3.5521779 \\ 3.3992711 \\ 3.4467836 \\ 3.3641511 \\ 3.1413310 \\ 3.4255876 \\ 3.2965942 \\ 3.3559864 \\ 2.9961926 \\ 3.1957732 \\ 3.2961100 \\
//runDeterministicGenerationAndSquash_500_deps5_b10_s100_true_PersistentStorage(20)
//0.3424890 \\ 0.3158910 \\ 0.3528126 \\ 0.2869969 \\ 0.4040479 \\ 0.4212331 \\ 0.3526003 \\ 0.3294793 \\ 0.4046355 \\ 0.3672724 \\ 0.3244953 \\ 0.3501157 \\ 0.3700148 \\ 0.3450721 \\ 0.3591020 \\ 0.3336285 \\ 0.3201523 \\ 0.3814187 \\ 0.3263383 \\ 0.3433598 \\
//runDeterministicGenerationAndSquash_5000_deps5_b1_s1000_true_PersistentStorage(20)
//3.4934642 \\ 3.7575121 \\ 3.8709035 \\ 3.8746137 \\ 3.6676125 \\ 4.0582198 \\ 3.5518437 \\ 3.8690548 \\ 3.8867667 \\ 3.8500386 \\ 3.7565286 \\ 3.5812275 \\ 3.8470738 \\ 3.7743883 \\ 3.7081826 \\ 3.9118873 \\ 3.4999349 \\ 3.6579421 \\ 3.5765422 \\ 3.5306889 \\
//runDeterministicGenerationAndSquash_5000_deps50_b10_s10_true_PersistentStorage(20)
//4.8929492 \\ 4.8044847 \\ 4.6349206 \\ 4.4524854 \\ 4.8369277 \\ 4.7468208 \\ 4.6773035 \\ 4.5939206 \\ 4.3860186 \\ 4.8078919 \\ 4.6983710 \\ 4.6922566 \\ 4.5838640 \\ 4.7990443 \\ 4.8184701 \\ 4.8698027 \\ 4.7114940 \\ 5.2348778 \\ 4.9080205 \\ 4.7141623 \\
//runDeterministicGenerationAndSquash_5000_deps5_b1_s10_true_PersistentStorage(20)
//4.0182205 \\ 4.0744585 \\ 3.6723516 \\ 3.9776671 \\ 3.9468559 \\ 4.1203764 \\ 3.9087728 \\ 3.9096994 \\ 4.3088639 \\ 3.9472267 \\ 3.8736424 \\ 4.1342584 \\ 4.0752282 \\ 4.0206352 \\ 4.3546385 \\ 4.0212352 \\ 3.9379419 \\ 4.2261277 \\ 3.8664565 \\ 3.9366532 \\
//runDeterministicGenerationAndSquash_5000_deps0_b10_s100_true_PersistentStorage(20)
//3.3783344 \\ 3.5152147 \\ 3.6581710 \\ 3.2943494 \\ 3.3926853 \\ 3.3014006 \\ 3.4495434 \\ 3.5406477 \\ 3.1616178 \\ 2.9790820 \\ 3.0527907 \\ 3.6052219 \\ 3.3781771 \\ 3.2790106 \\ 3.4074971 \\ 3.2693433 \\ 3.3936201 \\ 3.3438564 \\ 3.4173790 \\ 3.0484365 \\
//runDeterministicGenerationAndSquash_5000_deps0_b10_s1_true_PersistentStorage(20)
//2.9679724 \\ 3.3493455 \\ 3.2190533 \\ 3.3190401 \\ 3.4781855 \\ 3.2568345 \\ 3.1115422 \\ 3.1705644 \\ 3.1458123 \\ 3.1566793 \\ 3.2864554 \\ 3.4289676 \\ 3.7672719 \\ 3.5143236 \\ 3.1979188 \\ 3.4941410 \\ 3.2094446 \\ 3.0270972 \\ 3.1519514 \\ 3.2954769 \\
//runDeterministicGenerationAndSquash_500_deps5_b10_s10_true_PersistentStorage(20)
//0.4026237 \\ 0.3373768 \\ 0.2927477 \\ 0.2658938 \\ 0.3239245 \\ 0.2594134 \\ 0.3482869 \\ 0.2745850 \\ 0.4103360 \\ 0.2745871 \\ 0.4123006 \\ 0.2599498 \\ 0.3320337 \\ 0.3626905 \\ 0.3427905 \\ 0.3364963 \\ 0.3213012 \\ 0.2619641 \\ 0.3130228 \\ 0.3683733 \\
//runDeterministicGenerationAndSquash_5000_deps0_b10_s1000_true_PersistentStorage(20)
//3.2107218 \\ 3.5926199 \\ 3.1764354 \\ 3.0594098 \\ 3.5793437 \\ 3.5815695 \\ 3.5575078 \\ 3.2159391 \\ 3.4393421 \\ 3.4110300 \\ 3.0200602 \\ 3.3855744 \\ 3.5562243 \\ 3.3379885 \\ 3.2965595 \\ 3.5169284 \\ 3.4192878 \\ 3.2825999 \\ 3.2924847 \\ 3.2598980 \\
//runDeterministicGenerationAndSquash_5000_deps5_b500_s10000_true_PersistentStorage(20)
//3.6665856 \\ 3.6978502 \\ 4.0598289 \\ 3.6585582 \\ 3.7648646 \\ 3.7343889 \\ 3.6082755 \\ 4.0444769 \\ 3.6252021 \\ 3.8751617 \\ 3.7026757 \\ 3.7259494 \\ 3.8295142 \\ 3.9216019 \\ 3.4872143 \\ 3.5617316 \\ 3.5166489 \\ 3.8229156 \\ 3.5833602 \\ 3.8876158 \\
//runDeterministicGenerationAndSquash_500_deps5_b10_s1000_true_PersistentStorage(20)
//0.3932937 \\ 0.3025472 \\ 0.3258769 \\ 0.3360402 \\ 0.3119748 \\ 0.2915406 \\ 0.3913957 \\ 0.3375228 \\ 0.4142347 \\ 0.2567117 \\ 0.3624060 \\ 0.3268779 \\ 0.3424413 \\ 0.2937512 \\ 0.2969111 \\ 0.2432943 \\ 0.4039704 \\ 0.3097836 \\ 0.3178331 \\ 0.3628048 \\
//runDeterministicGenerationAndSquash_5000_deps5_b10_s1000_true_PersistentStorage(20)
//3.5479669 \\ 3.1432138 \\ 3.1948536 \\ 3.1743381 \\ 3.2055202 \\ 3.6764422 \\ 3.0214660 \\ 3.6256977 \\ 3.1235959 \\ 3.3324411 \\ 3.5190299 \\ 3.4830212 \\ 3.4721135 \\ 3.1853784 \\ 3.4439867 \\ 2.9522257 \\ 3.2413347 \\ 3.3097378 \\ 3.1717069 \\ 3.4261725 \\
//runDeterministicGenerationAndSquash_5000_deps5_b1_s10000_true_PersistentStorage(20)
//4.8042666 \\ 4.8564333 \\ 4.7784463 \\ 4.8941970 \\ 4.6638418 \\ 4.5669835 \\ 4.7587308 \\ 4.9471083 \\ 4.6826745 \\ 4.8911768 \\ 4.6973578 \\ 4.4646436 \\ 4.6633522 \\ 4.6036404 \\ 4.6551952 \\ 4.9286728 \\ 4.5799196 \\ 4.3898985 \\ 4.1888461 \\ 4.1794545 \\
//=== End End EndEnd End End ===
















//  PRE PRE PRE PRE PRE PRE
// PRE REWRITING STORAGE MODEL

//runDeterministicGenerationAndSquash_5000_deps5_b500_s1_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore
//ended(numberOfSquashes=11, numberOfBlocks=10, numberOfGeneratedTx=5000)
//RAW STORAGE REQUIREMENTS: after: 4194336bytes, max: 4267461bytes
//PERSISTED TRANSACTION COUNT: after: 4380txs, max: 4380txs
//runDeterministicGenerationAndSquash_5000_deps5_b500_s10_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore
//ended(numberOfSquashes=11, numberOfBlocks=10, numberOfGeneratedTx=5000)
//RAW STORAGE REQUIREMENTS: after: 4194336bytes, max: 4267461bytes
//PERSISTED TRANSACTION COUNT: after: 4380txs, max: 4380txs
//runDeterministicGenerationAndSquash_5000_deps5_b500_s100_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore
//ended(numberOfSquashes=11, numberOfBlocks=10, numberOfGeneratedTx=5000)
//RAW STORAGE REQUIREMENTS: after: 4194336bytes, max: 4267461bytes
//PERSISTED TRANSACTION COUNT: after: 4380txs, max: 4380txs
//runDeterministicGenerationAndSquash_5000_deps5_b500_s1000_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore
//ended(numberOfSquashes=6, numberOfBlocks=10, numberOfGeneratedTx=5000)
//RAW STORAGE REQUIREMENTS: after: 4194336bytes, max: 4267461bytes
//PERSISTED TRANSACTION COUNT: after: 4628txs, max: 4628txs
//runDeterministicGenerationAndSquash_5000_deps5_b500_s10000_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore
//ended(numberOfSquashes=1, numberOfBlocks=10, numberOfGeneratedTx=5000)
//RAW STORAGE REQUIREMENTS: after: 4194336bytes, max: 4267461bytes
//PERSISTED TRANSACTION COUNT: after: 4783txs, max: 4783txs
//runDeterministicGenerationAndSquash_5000_deps5_b10_s1_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore
//ended(numberOfSquashes=501, numberOfBlocks=500, numberOfGeneratedTx=5000)
//RAW STORAGE REQUIREMENTS: after: 6291488bytes, max: 6292909bytes
//PERSISTED TRANSACTION COUNT: after: 4424txs, max: 4424txs
//runDeterministicGenerationAndSquash_5000_deps5_b10_s10_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore
//ended(numberOfSquashes=501, numberOfBlocks=500, numberOfGeneratedTx=5000)
//RAW STORAGE REQUIREMENTS: after: 6291488bytes, max: 6292909bytes
//PERSISTED TRANSACTION COUNT: after: 4424txs, max: 4424txs
//runDeterministicGenerationAndSquash_5000_deps5_b10_s100_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore
//ended(numberOfSquashes=51, numberOfBlocks=500, numberOfGeneratedTx=5000)
//RAW STORAGE REQUIREMENTS: after: 4194336bytes, max: 4195967bytes
//PERSISTED TRANSACTION COUNT: after: 4778txs, max: 4778txs
//runDeterministicGenerationAndSquash_5000_deps5_b10_s1000_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore
//ended(numberOfSquashes=6, numberOfBlocks=500, numberOfGeneratedTx=5000)
//RAW STORAGE REQUIREMENTS: after: 4194336bytes, max: 4195967bytes
//PERSISTED TRANSACTION COUNT: after: 4824txs, max: 4824txs
//runDeterministicGenerationAndSquash_5000_deps5_b10_s10000_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore
//ended(numberOfSquashes=1, numberOfBlocks=500, numberOfGeneratedTx=5000)
//RAW STORAGE REQUIREMENTS: after: 4194336bytes, max: 4195967bytes
//PERSISTED TRANSACTION COUNT: after: 4827txs, max: 4827txs
//runDeterministicGenerationAndSquash_5000_deps5_b1_s1_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore
//ended(numberOfSquashes=5001, numberOfBlocks=5000, numberOfGeneratedTx=5000)
//RAW STORAGE REQUIREMENTS: after: 15737831bytes, max: 17303041bytes
//PERSISTED TRANSACTION COUNT: after: 4597txs, max: 4597txs
//runDeterministicGenerationAndSquash_5000_deps5_b1_s10_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore
//ended(numberOfSquashes=501, numberOfBlocks=5000, numberOfGeneratedTx=5000)
//RAW STORAGE REQUIREMENTS: after: 11541240bytes, max: 13107297bytes
//PERSISTED TRANSACTION COUNT: after: 5000txs, max: 5000txs
//runDeterministicGenerationAndSquash_5000_deps5_b1_s100_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore
//ended(numberOfSquashes=51, numberOfBlocks=5000, numberOfGeneratedTx=5000)
//RAW STORAGE REQUIREMENTS: after: 6291488bytes, max: 6291693bytes
//PERSISTED TRANSACTION COUNT: after: 5000txs, max: 5000txs
//runDeterministicGenerationAndSquash_5000_deps5_b1_s1000_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore
//ended(numberOfSquashes=6, numberOfBlocks=5000, numberOfGeneratedTx=5000)
//RAW STORAGE REQUIREMENTS: after: 5242912bytes, max: 5243129bytes
//PERSISTED TRANSACTION COUNT: after: 5000txs, max: 5000txs
//runDeterministicGenerationAndSquash_5000_deps5_b1_s10000_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore
//ended(numberOfSquashes=1, numberOfBlocks=5000, numberOfGeneratedTx=5000)
//RAW STORAGE REQUIREMENTS: after: 5242912bytes, max: 5242912bytes
//PERSISTED TRANSACTION COUNT: after: 5000txs, max: 5000txs
//runDeterministicGenerationAndSquash_5000_deps50_b10_s1_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore
//ended(numberOfSquashes=501, numberOfBlocks=500, numberOfGeneratedTx=5000)
//RAW STORAGE REQUIREMENTS: after: 23256925bytes, max: 25508513bytes
//PERSISTED TRANSACTION COUNT: after: 4455txs, max: 4455txs
//runDeterministicGenerationAndSquash_5000_deps50_b10_s10_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore
//ended(numberOfSquashes=501, numberOfBlocks=500, numberOfGeneratedTx=5000)
//RAW STORAGE REQUIREMENTS: after: 23256925bytes, max: 25508513bytes
//PERSISTED TRANSACTION COUNT: after: 4455txs, max: 4455txs
//runDeterministicGenerationAndSquash_5000_deps50_b10_s100_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore
//ended(numberOfSquashes=51, numberOfBlocks=500, numberOfGeneratedTx=5000)
//RAW STORAGE REQUIREMENTS: after: 6291488bytes, max: 6292909bytes
//PERSISTED TRANSACTION COUNT: after: 4790txs, max: 4790txs
//runDeterministicGenerationAndSquash_5000_deps50_b10_s1000_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore
//ended(numberOfSquashes=6, numberOfBlocks=500, numberOfGeneratedTx=5000)
//RAW STORAGE REQUIREMENTS: after: 4194336bytes, max: 4195967bytes
//PERSISTED TRANSACTION COUNT: after: 4833txs, max: 4833txs
//runDeterministicGenerationAndSquash_5000_deps50_b10_s10000_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore
//ended(numberOfSquashes=1, numberOfBlocks=500, numberOfGeneratedTx=5000)
//RAW STORAGE REQUIREMENTS: after: 4194336bytes, max: 4195967bytes
//PERSISTED TRANSACTION COUNT: after: 4836txs, max: 4836txs
//runDeterministicGenerationAndSquash_5000_deps0_b10_s1_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore
//ended(numberOfSquashes=501, numberOfBlocks=500, numberOfGeneratedTx=5000)
//RAW STORAGE REQUIREMENTS: after: 4194336bytes, max: 4195703bytes
//PERSISTED TRANSACTION COUNT: after: 5000txs, max: 5000txs
//runDeterministicGenerationAndSquash_5000_deps0_b10_s10_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore
//ended(numberOfSquashes=501, numberOfBlocks=500, numberOfGeneratedTx=5000)
//RAW STORAGE REQUIREMENTS: after: 4194336bytes, max: 4195703bytes
//PERSISTED TRANSACTION COUNT: after: 5000txs, max: 5000txs
//runDeterministicGenerationAndSquash_5000_deps0_b10_s100_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore
//ended(numberOfSquashes=51, numberOfBlocks=500, numberOfGeneratedTx=5000)
//RAW STORAGE REQUIREMENTS: after: 4194336bytes, max: 4195703bytes
//PERSISTED TRANSACTION COUNT: after: 5000txs, max: 5000txs
//runDeterministicGenerationAndSquash_5000_deps0_b10_s1000_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore
//ended(numberOfSquashes=6, numberOfBlocks=500, numberOfGeneratedTx=5000)
//RAW STORAGE REQUIREMENTS: after: 4194336bytes, max: 4195703bytes
//PERSISTED TRANSACTION COUNT: after: 5000txs, max: 5000txs
//runDeterministicGenerationAndSquash_5000_deps0_b10_s10000_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore
//ended(numberOfSquashes=1, numberOfBlocks=500, numberOfGeneratedTx=5000)
//RAW STORAGE REQUIREMENTS: after: 4194336bytes, max: 4195703bytes
//PERSISTED TRANSACTION COUNT: after: 5000txs, max: 5000txs
//runDeterministicGenerationAndSquash_500_deps5_b10_s1_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore
//ended(numberOfSquashes=51, numberOfBlocks=50, numberOfGeneratedTx=500)
//RAW STORAGE REQUIREMENTS: after: 4194336bytes, max: 4195839bytes
//PERSISTED TRANSACTION COUNT: after: 444txs, max: 444txs
//runDeterministicGenerationAndSquash_500_deps5_b10_s10_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore
//ended(numberOfSquashes=51, numberOfBlocks=50, numberOfGeneratedTx=500)
//RAW STORAGE REQUIREMENTS: after: 4194336bytes, max: 4195839bytes
//PERSISTED TRANSACTION COUNT: after: 444txs, max: 444txs
//runDeterministicGenerationAndSquash_500_deps5_b10_s100_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore
//ended(numberOfSquashes=6, numberOfBlocks=50, numberOfGeneratedTx=500)
//RAW STORAGE REQUIREMENTS: after: 4194336bytes, max: 4195839bytes
//PERSISTED TRANSACTION COUNT: after: 478txs, max: 478txs
//runDeterministicGenerationAndSquash_500_deps5_b10_s1000_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore
//ended(numberOfSquashes=1, numberOfBlocks=50, numberOfGeneratedTx=500)
//RAW STORAGE REQUIREMENTS: after: 4194336bytes, max: 4195839bytes
//PERSISTED TRANSACTION COUNT: after: 482txs, max: 482txs
//runDeterministicGenerationAndSquash_500_deps5_b10_s10000_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore
//ended(numberOfSquashes=1, numberOfBlocks=50, numberOfGeneratedTx=500)
//RAW STORAGE REQUIREMENTS: after: 4194336bytes, max: 4195839bytes
//PERSISTED TRANSACTION COUNT: after: 482txs, max: 482txs
//=== Printing ------- Calls ===
//app verify - 690100 - av: 0.0000003s - max: 0.0018381s - min: 0.0000001s
//squash verify - 690100 - av: 0.0000341s - max: 0.0709576s - min: 0.0000022s
//persist new block - 656000 - av: 0.0000399s - max: 0.5176647s - min: 0.0000059s
//runDeterministicGenerationAndSquash_5000_deps0_b10_s10_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - only squash - 10020 - av: 0.0002484s - max: 0.0037413s - min: 0.0000483s
//runDeterministicGenerationAndSquash_5000_deps0_b10_s1000_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - only squash - 120 - av: 0.0002545s - max: 0.0004585s - min: 0.0000599s
//runDeterministicGenerationAndSquash_5000_deps0_b10_s100_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - only squash - 1020 - av: 0.0002605s - max: 0.0019911s - min: 0.0000542s
//runDeterministicGenerationAndSquash_5000_deps0_b10_s1_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - only squash - 10020 - av: 0.0002700s - max: 0.0036813s - min: 0.0000482s
//runDeterministicGenerationAndSquash_5000_deps5_b1_s1_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - only squash - 100020 - av: 0.0002718s - max: 0.2485292s - min: 0.0000154s
//runDeterministicGenerationAndSquash_500_deps5_b10_s1_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - only squash - 1020 - av: 0.0003345s - max: 0.0038723s - min: 0.0000545s
//runDeterministicGenerationAndSquash_5000_deps5_b10_s1_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - only squash - 10020 - av: 0.0003377s - max: 0.0628047s - min: 0.0000512s
//runDeterministicGenerationAndSquash_5000_deps5_b10_s10_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - only squash - 10020 - av: 0.0003429s - max: 0.0625380s - min: 0.0000506s
//runDeterministicGenerationAndSquash_500_deps5_b10_s10_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - only squash - 1020 - av: 0.0003513s - max: 0.0025036s - min: 0.0000549s
//runDeterministicGenerationAndSquash_5000_deps0_b10_s10000_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - only squash - 20 - av: 0.0004549s - max: 0.0010047s - min: 0.0004390s
//runDeterministicGenerationAndSquash_5000_deps5_b10_s100_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - only squash - 1020 - av: 0.0005109s - max: 0.0030551s - min: 0.0004192s
//runDeterministicGenerationAndSquash_500_deps5_b10_s100_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - only squash - 120 - av: 0.0005526s - max: 0.0026333s - min: 0.0004369s
//runDeterministicGenerationAndSquash_5000_deps5_b1_s10_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - only squash - 10020 - av: 0.0005682s - max: 0.3267508s - min: 0.0000165s
//runDeterministicGenerationAndSquash_5000_deps50_b10_s10_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - only squash - 10020 - av: 0.0007821s - max: 0.0179072s - min: 0.0000485s
//runDeterministicGenerationAndSquash_5000_deps50_b10_s1_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - only squash - 10020 - av: 0.0008045s - max: 0.5123923s - min: 0.0000485s
//runDeterministicGenerationAndSquash_5000_deps5_b1_s100_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - only squash - 1020 - av: 0.0009368s - max: 0.0565532s - min: 0.0004463s
//runDeterministicGenerationAndSquash_5000_deps50_b10_s100_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - only squash - 1020 - av: 0.0010064s - max: 0.0149290s - min: 0.0000607s
//introduceChanges - 177800 - av: 0.0015555s - max: 0.5121818s - min: 0.0000011s
//runDeterministicGenerationAndSquash_5000_deps5_b10_s1000_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - only squash - 120 - av: 0.0019646s - max: 0.0119517s - min: 0.0004577s
//runDeterministicGenerationAndSquash_500_deps5_b10_s10000_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - only squash - 20 - av: 0.0020721s - max: 0.0042241s - min: 0.0020424s
//runDeterministicGenerationAndSquash_5000_deps50_b10_s1000_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - only squash - 120 - av: 0.0025068s - max: 0.0074625s - min: 0.0004626s
//runDeterministicGenerationAndSquash_500_deps5_b10_s1000_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - only squash - 20 - av: 0.0026296s - max: 0.0042087s - min: 0.0020297s
//runDeterministicGenerationAndSquash_5000_deps5_b500_s10_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - only squash - 220 - av: 0.0035619s - max: 0.0147222s - min: 0.0004768s
//runDeterministicGenerationAndSquash_5000_deps5_b500_s1_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - only squash - 220 - av: 0.0036423s - max: 0.1672791s - min: 0.0004848s
//runDeterministicGenerationAndSquash_5000_deps5_b500_s100_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - only squash - 220 - av: 0.0037585s - max: 0.0147976s - min: 0.0004696s
//runDeterministicGenerationAndSquash_5000_deps5_b1_s1000_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - only squash - 120 - av: 0.0040898s - max: 0.0383681s - min: 0.0004609s
//runDeterministicGenerationAndSquash_5000_deps5_b500_s1000_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - only squash - 120 - av: 0.0047022s - max: 0.0715481s - min: 0.0004832s
//runDeterministicGenerationAndSquash_5000_deps50_b10_s10000_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - only squash - 20 - av: 0.0194303s - max: 0.0205446s - min: 0.0170474s
//runDeterministicGenerationAndSquash_5000_deps5_b500_s10000_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - only squash - 20 - av: 0.0203382s - max: 0.0217569s - min: 0.0196003s
//runDeterministicGenerationAndSquash_5000_deps5_b10_s10000_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - only squash - 20 - av: 0.0256529s - max: 0.0755842s - min: 0.0178568s
//runDeterministicGenerationAndSquash_5000_deps5_b1_s10000_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - only squash - 20 - av: 0.0360405s - max: 0.0395347s - min: 0.0345222s
//runDeterministicGenerationAndSquash_500_deps5_b10_s1000_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - 20 - av: 0.4627297s - max: 0.4807086s - min: 0.4569259s
//runDeterministicGenerationAndSquash_500_deps5_b10_s1_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - 20 - av: 0.4672281s - max: 0.4867222s - min: 0.4624977s
//runDeterministicGenerationAndSquash_500_deps5_b10_s100_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - 20 - av: 0.4689339s - max: 0.4976545s - min: 0.4585865s
//runDeterministicGenerationAndSquash_500_deps5_b10_s10000_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - 20 - av: 0.4704912s - max: 0.4785214s - min: 0.4562229s
//runDeterministicGenerationAndSquash_500_deps5_b10_s10_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - 20 - av: 0.4780081s - max: 0.4864252s - min: 0.4606378s
//runDeterministicGenerationAndSquash_5000_deps0_b10_s1_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - 20 - av: 4.5391576s - max: 4.6946079s - min: 4.5105103s
//runDeterministicGenerationAndSquash_5000_deps0_b10_s1000_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - 20 - av: 4.5613012s - max: 4.6147921s - min: 4.5218036s
//runDeterministicGenerationAndSquash_5000_deps0_b10_s10_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - 20 - av: 4.5727169s - max: 4.7403053s - min: 4.5153201s
//runDeterministicGenerationAndSquash_5000_deps0_b10_s100_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - 20 - av: 4.5809916s - max: 4.6074133s - min: 4.5224364s
//runDeterministicGenerationAndSquash_5000_deps0_b10_s10000_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - 20 - av: 4.5913564s - max: 4.6343354s - min: 4.5380175s
//runDeterministicGenerationAndSquash_5000_deps50_b10_s1000_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - 20 - av: 4.6423183s - max: 4.7347897s - min: 4.5979312s
//runDeterministicGenerationAndSquash_5000_deps5_b10_s100_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - 20 - av: 4.6523877s - max: 4.7364846s - min: 4.6309441s
//runDeterministicGenerationAndSquash_5000_deps5_b10_s1000_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - 20 - av: 4.6876578s - max: 4.7696930s - min: 4.6129875s
//runDeterministicGenerationAndSquash_5000_deps5_b10_s10_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - 20 - av: 4.7026448s - max: 4.7955633s - min: 4.6533349s
//runDeterministicGenerationAndSquash_5000_deps5_b10_s1_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - 20 - av: 4.7104068s - max: 4.8184363s - min: 4.6304310s
//runDeterministicGenerationAndSquash_5000_deps50_b10_s100_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - 20 - av: 4.7145998s - max: 4.8110805s - min: 4.6558548s
//runDeterministicGenerationAndSquash_5000_deps5_b10_s10000_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - 20 - av: 4.7205055s - max: 4.8056811s - min: 4.6909577s
//runDeterministicGenerationAndSquash_5000_deps50_b10_s10000_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - 20 - av: 4.7354591s - max: 4.8543986s - min: 4.6840612s
//runDeterministicGenerationAndSquash_5000_deps5_b1_s100_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - 20 - av: 4.7894080s - max: 5.2679522s - min: 4.7559135s
//runDeterministicGenerationAndSquash_5000_deps5_b1_s1000_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - 20 - av: 4.9122936s - max: 4.9881426s - min: 4.8404054s
//runDeterministicGenerationAndSquash_5000_deps5_b500_s1_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - 20 - av: 4.9868702s - max: 5.9413850s - min: 4.9678350s
//runDeterministicGenerationAndSquash_5000_deps5_b500_s10_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - 20 - av: 5.0027943s - max: 5.1238240s - min: 4.9728132s
//runDeterministicGenerationAndSquash_5000_deps5_b500_s1000_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - 20 - av: 5.0182478s - max: 5.1550156s - min: 4.9845253s
//runDeterministicGenerationAndSquash_5000_deps5_b500_s100_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - 20 - av: 5.0271435s - max: 5.0809452s - min: 4.9430830s
//runDeterministicGenerationAndSquash_5000_deps5_b500_s10000_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - 20 - av: 5.0742635s - max: 5.1119890s - min: 4.9973761s
//runDeterministicGenerationAndSquash_5000_deps5_b1_s10_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - 20 - av: 5.2072836s - max: 5.5078333s - min: 5.1272474s
//runDeterministicGenerationAndSquash_5000_deps5_b1_s1_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - 20 - av: 5.5744219s - max: 5.8928686s - min: 5.5034283s
//runDeterministicGenerationAndSquash_5000_deps5_b1_s10000_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - 20 - av: 5.6210176s - max: 5.6915714s - min: 5.5461752s
//runDeterministicGenerationAndSquash_5000_deps50_b10_s10_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - 20 - av: 5.6423809s - max: 5.8531972s - min: 5.5403681s
//runDeterministicGenerationAndSquash_5000_deps50_b10_s1_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore - 20 - av: 5.8011348s - max: 6.2062248s - min: 5.5679814s
//=== End End EndEnd End End ===
//=== Printing ------- Calls ===
//runDeterministicGenerationAndSquash_5000_deps5_b10_s1000_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore(20)
//4.6781759 \\ 4.7223454 \\ 4.6434476 \\ 4.6759099 \\ 4.7341876 \\ 4.6700538 \\ 4.6694443 \\ 4.6612311 \\ 4.6482569 \\ 4.6303803 \\ 4.6129906 \\ 4.6980574 \\ 4.6501943 \\ 4.6453091 \\ 4.6589685 \\ 4.7696956 \\ 4.6707398 \\ 4.6659808 \\ 4.6965569 \\
//runDeterministicGenerationAndSquash_500_deps5_b10_s1_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore(20)
//0.4709247 \\ 0.4707435 \\ 0.4794312 \\ 0.4794452 \\ 0.4644642 \\ 0.4628582 \\ 0.4640518 \\ 0.4649406 \\ 0.4867228 \\ 0.4657811 \\ 0.4655051 \\ 0.4645168 \\ 0.4779880 \\ 0.4633638 \\ 0.4624986 \\ 0.4864477 \\ 0.4635236 \\ 0.4644443 \\ 0.4674337 \\ 0.4672160 \\
//runDeterministicGenerationAndSquash_5000_deps5_b10_s100_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore(20)
//4.6807927 \\ 4.6583448 \\ 4.6368531 \\ 4.6578996 \\ 4.7128664 \\ 4.7364876 \\ 4.6620424 \\ 4.6525341 \\ 4.6562845 \\ 4.6553887 \\ 4.6663269 \\ 4.7203524 \\ 4.6685608 \\ 4.6995852 \\ 4.6366303 \\ 4.6309468 \\ 4.6461977 \\ 4.6439177 \\ 4.6910868 \\ 4.6365980 \\
//runDeterministicGenerationAndSquash_5000_deps5_b1_s1000_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore(20)
//4.9164006 \\ 4.9280182 \\ 4.9748011 \\ 4.9470796 \\ 4.9631006 \\ 4.8708582 \\ 4.8699544 \\ 4.9203217 \\ 4.9442926 \\ 4.9629614 \\ 4.9881439 \\ 4.9358562 \\ 4.9644986 \\ 4.8817944 \\ 4.9485370 \\ 4.8992810 \\ 4.9236844 \\ 4.8741305 \\ 4.8404068 \\ 4.9558112 \\
//runDeterministicGenerationAndSquash_5000_deps50_b10_s100_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore(20)
//4.6839333 \\ 4.6916960 \\ 4.7077371 \\ 4.7115332 \\ 4.7318554 \\ 4.7082017 \\ 4.7029129 \\ 4.7167754 \\ 4.7216354 \\ 4.7280973 \\ 4.6873822 \\ 4.6948218 \\ 4.6558561 \\ 4.7007527 \\ 4.6971459 \\ 4.6895802 \\ 4.7131377 \\ 4.8110818 \\ 4.6820665 \\ 4.7098308 \\
//runDeterministicGenerationAndSquash_5000_deps50_b10_s1000_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore(20)
//4.6430980 \\ 4.7124696 \\ 4.6367531 \\ 4.6323956 \\ 4.6541174 \\ 4.6488546 \\ 4.6901564 \\ 4.6650459 \\ 4.6737376 \\ 4.6929610 \\ 4.6712603 \\ 4.7347910 \\ 4.6357949 \\ 4.6357252 \\ 4.6544886 \\ 4.7045737 \\ 4.6332825 \\ 4.5979402 \\ 4.6392430 \\ 4.6514729 \\
//runDeterministicGenerationAndSquash_500_deps5_b10_s100_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore(20)
//0.4967989 \\ 0.4598777 \\ 0.4587998 \\ 0.4661205 \\ 0.4611198 \\ 0.4615394 \\ 0.4661512 \\ 0.4621282 \\ 0.4665277 \\ 0.4976557 \\ 0.4631586 \\ 0.4606095 \\ 0.4611814 \\ 0.4623947 \\ 0.4585875 \\ 0.4853133 \\ 0.4609941 \\ 0.4654185 \\ 0.4841055 \\ 0.4627037 \\
//runDeterministicGenerationAndSquash_5000_deps5_b1_s10000_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore(20)
//5.6866905 \\ 5.6004893 \\ 5.6734399 \\ 5.5710359 \\ 5.5461763 \\ 5.6196316 \\ 5.6376465 \\ 5.6915728 \\ 5.6068475 \\ 5.6794260 \\ 5.6221877 \\ 5.6641903 \\ 5.6157314 \\ 5.5568504 \\ 5.5822980 \\ 5.6123989 \\ 5.6579635 \\ 5.5820936 \\ 5.6034062 \\ 5.6374943 \\
//runDeterministicGenerationAndSquash_5000_deps0_b10_s10000_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore(20)
//4.6343363 \\ 4.5692542 \\ 4.5502583 \\ 4.5491514 \\ 4.5587279 \\ 4.5432195 \\ 4.5610057 \\ 4.5666520 \\ 4.5403720 \\ 4.5536199 \\ 4.5380192 \\ 4.5623257 \\ 4.5803118 \\ 4.5826570 \\ 4.5699344 \\ 4.5763169 \\ 4.5404998 \\ 4.5755874 \\ 4.5860459 \\ 4.6064397 \\
//runDeterministicGenerationAndSquash_5000_deps5_b10_s10000_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore(20)
//4.7149892 \\ 4.6999207 \\ 4.7408621 \\ 4.7357348 \\ 4.7344015 \\ 4.8009862 \\ 4.7263552 \\ 4.7182270 \\ 4.6914800 \\ 4.6909696 \\ 4.7872041 \\ 4.8056848 \\ 4.7129817 \\ 4.7827205 \\ 4.7961668 \\ 4.7429711 \\ 4.7812498 \\ 4.7798912 \\ 4.7131353 \\ 4.6966592 \\
//runDeterministicGenerationAndSquash_500_deps5_b10_s1000_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore(20)
//0.4611928 \\ 0.4629000 \\ 0.4600297 \\ 0.4762461 \\ 0.4616313 \\ 0.4720530 \\ 0.4670722 \\ 0.4573780 \\ 0.4581500 \\ 0.4596991 \\ 0.4609453 \\ 0.4611150 \\ 0.4627858 \\ 0.4569279 \\ 0.4633172 \\ 0.4807101 \\ 0.4787051 \\ 0.4655930 \\ 0.4627702 \\ 0.4589633 \\
//runDeterministicGenerationAndSquash_5000_deps5_b500_s1000_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore(20)
//4.9996636 \\ 5.0538276 \\ 5.0220630 \\ 5.0144101 \\ 5.0021056 \\ 4.9963538 \\ 5.0300516 \\ 4.9941645 \\ 5.0292418 \\ 5.0928804 \\ 5.0111685 \\ 4.9941733 \\ 5.0082798 \\ 5.0483616 \\ 5.0436899 \\ 5.0457225 \\ 5.0238073 \\ 5.1550188 \\ 5.0101863 \\ 4.9845284 \\
//runDeterministicGenerationAndSquash_5000_deps5_b500_s10_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore(20)
//4.9960435 \\ 4.9771656 \\ 4.9944845 \\ 5.0725571 \\ 5.1238341 \\ 4.9728158 \\ 4.9919183 \\ 4.9810573 \\ 5.0626111 \\ 5.0000948 \\ 4.9914476 \\ 4.9961793 \\ 5.0125082 \\ 5.0661577 \\ 5.0610834 \\ 5.0194691 \\ 5.0061721 \\ 5.0054147 \\ 4.9856233 \\ 5.0064114 \\
//runDeterministicGenerationAndSquash_5000_deps0_b10_s1000_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore(20)
//4.5695284 \\ 4.5352686 \\ 4.5706087 \\ 4.5472373 \\ 4.5645989 \\ 4.6147936 \\ 4.5509572 \\ 4.5611982 \\ 4.5408992 \\ 4.5570330 \\ 4.5932544 \\ 4.5947740 \\ 4.5712779 \\ 4.6042973 \\ 4.5740896 \\ 4.5708038 \\ 4.6007827 \\ 4.5305030 \\ 4.5218051 \\ 4.5818926 \\
//runDeterministicGenerationAndSquash_5000_deps0_b10_s10_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore(20)
//4.5417244 \\ 4.5643121 \\ 4.5543562 \\ 4.5629751 \\ 4.5505226 \\ 4.7403068 \\ 4.5326744 \\ 4.5480948 \\ 4.5153216 \\ 4.5167376 \\ 4.5831868 \\ 4.5578568 \\ 4.5932158 \\ 4.5869007 \\ 4.6068405 \\ 4.5465202 \\ 4.6067949 \\ 4.5539002 \\ 4.5391297 \\ 4.5902690 \\
//runDeterministicGenerationAndSquash_500_deps5_b10_s10_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore(20)
//0.4672582 \\ 0.4802334 \\ 0.4812443 \\ 0.4668210 \\ 0.4696152 \\ 0.4672161 \\ 0.4787233 \\ 0.4671117 \\ 0.4637277 \\ 0.4619938 \\ 0.4620968 \\ 0.4648312 \\ 0.4611149 \\ 0.4643909 \\ 0.4606390 \\ 0.4633370 \\ 0.4690451 \\ 0.4625303 \\ 0.4864263 \\ 0.4807047 \\
//runDeterministicGenerationAndSquash_5000_deps5_b10_s10_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore(20)
//4.7137171 \\ 4.6970548 \\ 4.7456251 \\ 4.7580253 \\ 4.7524652 \\ 4.6930265 \\ 4.6562503 \\ 4.6533381 \\ 4.7559008 \\ 4.7762264 \\ 4.7160440 \\ 4.6757030 \\ 4.6964088 \\ 4.7187087 \\ 4.7255272 \\ 4.7478985 \\ 4.7955665 \\ 4.7008365 \\ 4.7331621 \\ 4.6724807 \\
//runDeterministicGenerationAndSquash_5000_deps50_b10_s1_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore(20)
//5.7651066 \\ 5.6691308 \\ 5.5679829 \\ 5.6847046 \\ 5.5930917 \\ 5.6168384 \\ 5.6204557 \\ 5.6260445 \\ 5.6456285 \\ 5.5967711 \\ 5.6814332 \\ 5.6209229 \\ 5.6428952 \\ 5.6346665 \\ 5.5758936 \\ 5.6873940 \\ 5.5984628 \\ 5.6127902 \\ 6.2062260 \\ 5.6902968 \\
//runDeterministicGenerationAndSquash_5000_deps50_b10_s10000_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore(20)
//4.6888329 \\ 4.7064957 \\ 4.7465702 \\ 4.6840634 \\ 4.7976607 \\ 4.7663076 \\ 4.7294239 \\ 4.7118725 \\ 4.7154956 \\ 4.7229751 \\ 4.7067187 \\ 4.7298994 \\ 4.7549739 \\ 4.7116957 \\ 4.7343055 \\ 4.7111100 \\ 4.7635260 \\ 4.8544006 \\ 4.7051626 \\ 4.7192477 \\
//runDeterministicGenerationAndSquash_5000_deps5_b500_s1_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore(20)
//5.9413984 \\ 5.1271884 \\ 5.1113145 \\ 5.0462525 \\ 5.1081948 \\ 5.0499489 \\ 5.0642162 \\ 4.9988992 \\ 4.9996192 \\ 5.0563319 \\ 5.0603692 \\ 5.0164017 \\ 5.0412364 \\ 4.9866741 \\ 4.9818029 \\ 5.0131543 \\ 5.0400149 \\ 4.9930098 \\ 5.0040606 \\ 4.9678377 \\
//runDeterministicGenerationAndSquash_5000_deps0_b10_s100_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore(20)
//4.5713057 \\ 4.5620117 \\ 4.5607109 \\ 4.5486304 \\ 4.5359208 \\ 4.5466438 \\ 4.5544225 \\ 4.5436806 \\ 4.6010529 \\ 4.6074144 \\ 4.5490639 \\ 4.5497377 \\ 4.5728666 \\ 4.5613892 \\ 4.5730754 \\ 4.5224374 \\ 4.5292915 \\ 4.5874866 \\ 4.5562474 \\ 4.6026470 \\
//runDeterministicGenerationAndSquash_5000_deps5_b1_s1_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore(20)
//5.8056798 \\ 5.6502385 \\ 5.6504016 \\ 5.6313028 \\ 5.6250491 \\ 5.6636596 \\ 5.5677912 \\ 5.5034319 \\ 5.5265334 \\ 5.5859643 \\ 5.6268523 \\ 5.6141407 \\ 5.6369989 \\ 5.8928719 \\ 5.5859121 \\ 5.5836416 \\ 5.5308001 \\ 5.6198989 \\ 5.6120056 \\ 5.5430830 \\
//runDeterministicGenerationAndSquash_5000_deps5_b10_s1_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore(20)
//4.8003460 \\ 4.7110878 \\ 4.7043050 \\ 4.7561497 \\ 4.7059759 \\ 4.8184395 \\ 4.7061509 \\ 4.6858975 \\ 4.7048466 \\ 4.7316899 \\ 4.8077678 \\ 4.6969345 \\ 4.7365026 \\ 4.7104927 \\ 4.7000364 \\ 4.6304338 \\ 4.6987797 \\ 4.6867200 \\ 4.6732994 \\ 4.7413013 \\
//runDeterministicGenerationAndSquash_5000_deps0_b10_s1_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore(20)
//4.5805090 \\ 4.5469178 \\ 4.6056276 \\ 4.5741712 \\ 4.5462987 \\ 4.6354563 \\ 4.5519627 \\ 4.5914794 \\ 4.5749033 \\ 4.5890471 \\ 4.5692611 \\ 4.6946093 \\ 4.6273251 \\ 4.6067896 \\ 4.5106315 \\ 4.5729736 \\ 4.5556259 \\ 4.6153535 \\ 4.5105118 \\ 4.5286542 \\
//runDeterministicGenerationAndSquash_5000_deps5_b500_s100_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore(20)
//5.0061204 \\ 4.9952348 \\ 4.9640802 \\ 5.0809479 \\ 4.9866324 \\ 5.0139139 \\ 5.0055874 \\ 4.9430857 \\ 4.9740350 \\ 4.9936763 \\ 4.9639201 \\ 5.0216615 \\ 5.0140683 \\ 4.9551414 \\ 4.9874583 \\ 4.9765219 \\ 5.0270341 \\ 4.9517775 \\ 4.9845355 \\ 5.0731689 \\
//runDeterministicGenerationAndSquash_500_deps5_b10_s10000_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore(20)
//0.4620206 \\ 0.4670544 \\ 0.4660636 \\ 0.4629152 \\ 0.4562240 \\ 0.4608613 \\ 0.4637601 \\ 0.4660953 \\ 0.4727906 \\ 0.4605962 \\ 0.4744031 \\ 0.4649442 \\ 0.4650132 \\ 0.4629005 \\ 0.4643131 \\ 0.4609064 \\ 0.4666071 \\ 0.4775315 \\ 0.4785223 \\ 0.4661819 \\
//runDeterministicGenerationAndSquash_5000_deps5_b1_s100_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore(20)
//4.8452469 \\ 4.8681924 \\ 4.8853766 \\ 4.8782737 \\ 4.7859130 \\ 4.9207074 \\ 4.8262602 \\ 4.8439692 \\ 4.8540407 \\ 4.8123913 \\ 4.8400471 \\ 4.8674282 \\ 4.7974579 \\ 4.8137434 \\ 4.7828730 \\ 5.2679555 \\ 4.8208733 \\ 4.7559168 \\ 4.8018973 \\ 4.7569753 \\
//runDeterministicGenerationAndSquash_5000_deps5_b500_s10000_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore(20)
//5.0065089 \\ 5.0016302 \\ 5.0208787 \\ 5.0211289 \\ 5.0252314 \\ 5.1119922 \\ 4.9978939 \\ 4.9973797 \\ 5.0007280 \\ 5.0074301 \\ 5.0582849 \\ 5.0102320 \\ 5.0370224 \\ 5.0455423 \\ 5.0264595 \\ 5.0191861 \\ 5.0328301 \\ 5.0918050 \\ 5.0693360 \\ 5.0836150 \\
//runDeterministicGenerationAndSquash_5000_deps50_b10_s10_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore(20)
//5.5409448 \\ 5.6232083 \\ 5.6124271 \\ 5.6409479 \\ 5.6322598 \\ 5.7957938 \\ 5.6123999 \\ 5.5403694 \\ 5.8531983 \\ 5.5715307 \\ 5.6417210 \\ 5.6698205 \\ 5.5980142 \\ 5.6567440 \\ 5.6672757 \\ 5.6592649 \\ 5.5851971 \\ 5.7248828 \\ 5.6350219 \\ 5.6307634 \\
//runDeterministicGenerationAndSquash_5000_deps5_b1_s10_true_PersistentBlockChainStorage_PersistentBlockChainStorage_PersistentReverseDependencyStore(20)
//5.1272498 \\ 5.2392551 \\ 5.1484966 \\ 5.2113440 \\ 5.2184766 \\ 5.2098294 \\ 5.2030959 \\ 5.1517200 \\ 5.4848409 \\ 5.1927879 \\ 5.2103974 \\ 5.1795193 \\ 5.5078367 \\ 5.1779093 \\ 5.1911538 \\ 5.2458557 \\ 5.2560197 \\ 5.2211842 \\ 5.2650200 \\ 5.1650542 \\
//=== End End EndEnd End End ===