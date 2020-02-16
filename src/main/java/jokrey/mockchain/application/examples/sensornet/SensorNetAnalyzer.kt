package jokrey.mockchain.application.examples.sensornet

import jokrey.mockchain.Mockchain
import jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.string.LITagStringEncoder
import jokrey.mockchain.squash.BuildUponSquashHandler
import jokrey.mockchain.squash.SquashRejectedException
import jokrey.mockchain.storage_classes.*
import jokrey.mockchain.visualization.VisualizableApp
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

/**
 * Concept:
 * Say that a number of people have sensor data and want to make said sensor data and subsequent analysis available.
 * They however do not have a central server to run this on / they do not trust any one server to do such critical analysis.
 *
 * So they write this app. However once a day the sensor data becomes less relevant for following analysis, one sensor value per sensor per day is enough
 * So they squash using replace + build upon dependencies to remove the old transactions and replace them with an average.
 */
class SensorNetAnalyzer: VisualizableApp {
    private val sensorData = HashMap<String, MutableList<SensorResult>>()

    override fun verify(instance: Mockchain, blockCreatorIdentity:ImmutableByteArray, vararg txs: Transaction): List<Pair<Transaction, RejectionReason.APP_VERIFY>> {
        val denied = ArrayList<Pair<Transaction, RejectionReason.APP_VERIFY>>()
        for(tx in txs) {
            val sr = srFromTx(tx)
            if(java.lang.Double.isNaN(sr.second.result) && !sr.second.isAverage)
                denied.add(Pair(tx, RejectionReason.APP_VERIFY("value is NaN and will not become an average")))
            else if(java.lang.Double.isInfinite(sr.second.result))
                denied.add(Pair(tx, RejectionReason.APP_VERIFY("value is infinite")))
            else if(tx.bDependencies.isNotEmpty() && (tx.bDependencies.size % 2 != 0 || tx.bDependencies.any { it.type != DependencyType.REPLACES && it.type != DependencyType.BUILDS_UPON }))
                denied.add(Pair(tx, RejectionReason.APP_VERIFY("dependencies not empty and not even or contain non-replace or non-build-upon edges")))
        }
        return denied
    }

    //cannot introduce change before build upon, is this the right way or should the average be pre calculated??
    //    this is kinda weird and precalculation might be even weirder
    override fun txAltered(instance: Mockchain, oldHash: TransactionHash, oldTx: Transaction, newHash: TransactionHash, newTx: Transaction, txWasPersisted: Boolean) {
        if(srFromTx(oldTx).second.isAverage && srFromTx(newTx).second.isAverage)
            sensorData[srFromTx(oldTx).first]!!.add(srFromTx(newTx).second)
    }
    override fun txRejected(instance: Mockchain, oldHash: TransactionHash, oldTx: Transaction, reason: RejectionReason) {}
    override fun txRemoved(instance: Mockchain, oldHash: TransactionHash, oldTx: Transaction, txWasPersisted: Boolean) {
        val removed = srFromTx(oldTx)
        for ((i, snActual) in sensorData[removed.first]!!.withIndex())
            if (snActual == removed.second) {
                sensorData[removed.first]!!.removeAt(i)
                break
            }
    }

    override fun newBlock(instance: Mockchain, block: Block) {
        for (res in Array(block.size) { srFromTx(instance[block[it]]) }) {
            if (java.lang.Double.isNaN(res.second.result)) {
                if (!res.second.isAverage)
                    throw IllegalStateException("Should not happen, verification should have caught this")
            } else
                sensorData.computeIfAbsent(res.first) { ArrayList() }.add(res.second)
        }
    }

    private val possibleNames: Array<String> = arrayOf("Straße", "Haus", "Auto", "Garage")
    private var lastStep = 0L
    override fun next(instance: Mockchain, step: Long, random: Random): Optional<Transaction> {
        val pastDayToMinimize = 51
        if (step != 0L && getDay(step - pastDayToMinimize) != getDay(step - (pastDayToMinimize-1))) {
            val today = getDay(step - pastDayToMinimize) //meaning 10 steps ago was the switch - how much this needs to be depends on mem pool size - in a real world application this makes no difference
            val namesToMinimize = sensorData.keys.filter { sensorData[it]!!.size > 1 }
            for (name in namesToMinimize) {
                val list = sensorData[name]!!
                val dayToSquash = list.filter { it.day == today }
                val txsOfNameAndDay = dayToSquash.map { sd -> sd.getTx(name) }.toTypedArray()
                instance.commitToMemPool(SensorResult(step - pastDayToMinimize, Double.NaN, true).getTx(name, *dependenciesFrom(DependencyType.REPLACES, *txsOfNameAndDay) + dependenciesFrom(DependencyType.BUILDS_UPON, *txsOfNameAndDay)))
            }
        }

        lastStep = step
        return Optional.of(SensorResult(step, random.nextInt(10).toDouble(), false).getTx(possibleNames.toList().shuffled(random)[0]))
    }

    override fun shortDescriptor(tx: Transaction): String {
        val sr = srFromTx(tx)
        return sr.first+"(${"%.1f".format(sr.second.result)})"
    }
    override fun longDescriptor(tx: Transaction): String {
        val sr = srFromTx(tx)
        return sr.first + " - "+sr.second.result + " @ "+sr.second.day
    }
    override fun shortStateDescriptor() = possibleNames.joinToString {name ->
        val list = sensorData[name]
        if(list!=null && list.isNotEmpty()) {
            val grouped = list.groupBy { it.day }
            val dailyAverages = HashMap<Long, Double>()
            for((day, listOfDay) in grouped) {
                val averaged = listOfDay.filter { it.isAverage }
                val notAveraged = listOfDay.filterNot { it.isAverage }
                if(notAveraged.isEmpty())
                    dailyAverages[day] = averaged[0].result
                else
                    dailyAverages[day] = notAveraged.map { it.result }.average()
            }
            "$name: (${"%.2f".format(dailyAverages.values.average())})"
        } else
            "$name: (None)"
    }
    override fun exhaustiveStateDescriptor() = possibleNames.joinToString { name ->
        val list = sensorData[name]
        if(list!=null && list.isNotEmpty()) {
            val grouped = list.groupBy { it.day }
            val dailyAverages = HashMap<Long, Double>()
            for((day, listOfDay) in grouped) {
                val averaged = listOfDay.filter { it.isAverage }
                val notAveraged = listOfDay.filterNot { it.isAverage }
                if(notAveraged.isEmpty())
                    dailyAverages[day] = averaged[0].result
                else
                    dailyAverages[day] = notAveraged.map { it.result }.average()
            }
            "AVERAGES FOR $name are " + dailyAverages.entries.joinToString { "${it.key}: ${it.value}" }
        } else
            "$name has no data yet"
    }

    override fun getEqualFreshCreator(): () -> VisualizableApp = { SensorNetAnalyzer() }
    override fun getCreatorParamNames():Array<String> = emptyArray()
    override fun getCurrentParamContentForEqualCreation():Array<String> = emptyArray()
    override fun createNewInstance(vararg params: String) = SensorNetAnalyzer()
    override fun createTxFrom(input: String): Transaction {
        val (name, sensorResult) = input.replace(")", "").split("(")
        return SensorResult(lastStep, sensorResult.toDouble(), false).getTx(name)
    }

    override fun getBuildUponSquashHandler(): BuildUponSquashHandler = {buildingUpon, buildsUpon ->
        val (averageName, averageEmptySensorResult) = srFromTxCont(buildsUpon)
        if(!averageEmptySensorResult.isAverage || !java.lang.Double.isNaN(averageEmptySensorResult.result)) throw SquashRejectedException()

        val previousResults = buildingUpon.map { srFromTxCont(it)  }

        if(previousResults.any { it.first !=  averageName || it.second.day != averageEmptySensorResult.day}) throw SquashRejectedException()

        val average = previousResults.map { it.second.result }.average()
        LOG.finest("averageName = ${averageName}")
        LOG.finest("av = ${average}")
        SensorResult(averageEmptySensorResult.timestamp, average, true).getTx(averageName).content
    }
}

data class SensorResult(val timestamp: Long, val result: Double, val isAverage: Boolean) {
    fun getTx(sensorName: String, vararg dependencies: Dependency) : Transaction {
        return Transaction(
                LITagStringEncoder().
                        addEntryT_nocheck("sn", sensorName).
                        addEntryT_nocheck("timestamp", timestamp).
                        addEntryT_nocheck("isAverage", isAverage).
                        addEntryT_nocheck("rs", result).encodedBytes, *dependencies)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SensorResult
        return timestamp == other.timestamp && result == other.result && isAverage == other.isAverage
    }
    override fun hashCode(): Int {
        var result1 = timestamp.hashCode()
        result1 = 31 * result1 + result.hashCode()
        result1 = 31 * result1 + isAverage.hashCode()
        return result1
    }

    val day: Long
        get() = getDay(timestamp)
}


fun getDay(timestamp: Long) : Long = timestamp/24

fun srFromTx(tx: Transaction) = srFromTxCont(tx.content)
fun srFromTxCont(tx_content: ByteArray) : Pair<String, SensorResult> {
    val de = LITagStringEncoder()
    de.readFromEncodedBytes(tx_content)

    return Pair(de.getEntry("sn"), SensorResult(
            de.getEntryT("timestamp", 0L),
            de.getEntryT("rs", 0.0),
            de.getEntryT("isAverage", false)
    ))
}