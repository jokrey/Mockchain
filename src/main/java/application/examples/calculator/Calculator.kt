package application.examples.calculator

import jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.string.LITagStringEncoder
import squash.BuildUponSquashHandler
import squash.PartialReplaceSquashHandler
import squash.SquashRejectedException
import storage_classes.*
import visualization.VisualizableApp
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.math.min

/**
 * These are three implementations of a calculator using the concept of a blockchain.
 * It is a very simple calculator. It has a current result, on which operations can be executed(+, -, /, *) - which then creates a new result.
 *
 * The calculator can optionally run multiple such result calculations at once(multiple "strings" of operations)
 *
 * And it can optionally combine strings using an operation.
 *
 *
 * In reality it would be a peculiar choice to implement a calculator with a blockchain.
 *   Though a group can now have a result calculated by many different people and ensuring that the result was reached without ever using the operation +1.
 *   Now it is plausible that no one in history ever wanted to do this, but the general idea of altering a state using transactions here can be applied to other applications.
 *   (which is exactly the concept of blockchain, but this example has the additional constraint that after a transaction has been executed it becomes no longer relevant / can be partially deleted)
 *
 * @author jokrey
 */

class SingleStringCalculator(verify:(Calculation) -> Boolean={true}) : MashedCalculator(1, verify, maxDependencies = 1) {
    override fun getCreatorParamNames(): Array<String> = emptyArray()
    override fun getCurrentParamContentForEqualCreation(): Array<String> = emptyArray()
    override fun createNewInstance(vararg params: String) = SingleStringCalculator()
    override fun shortDescriptor(tx: Transaction): String {
        return super.shortDescriptor(tx).replace(" in 0", "")
    }
    override fun createTxFrom(input: String): Transaction = super.createTxFrom(if(!input.contains("in")) input + "in 0" else input)
}
class MultiStringCalculator(numberOfStrings:Int) : MashedCalculator(numberOfStrings, maxDependencies = 1) {
    override fun getCreatorParamNames() = arrayOf("number of initial states")
    override fun getCurrentParamContentForEqualCreation() = arrayOf(numberOfInitialStates.toString())
    override fun createNewInstance(vararg params: String) = MultiStringCalculator(params[0].toInt())
}

open class MashedCalculator(internal val numberOfInitialStates:Int, val verify:(Calculation) -> Boolean={true}, private val maxDependencies: Int = 1) : VisualizableApp {
    private val results = HashMap<Int, Double>(numberOfInitialStates)

    fun getResults() = results.toList().sortedBy { it.first }.map { it.second }
    fun getRawResults():HashMap<Int, Double> = results.clone() as HashMap<Int, Double>

    override fun verify(chain: Chain, vararg txs: Transaction): List<Pair<Transaction, RejectionReason.APP_VERIFY>> {
        val virtualResults = results.clone() as HashMap<Int, Double>

        val denied = ArrayList<Pair<Transaction, RejectionReason.APP_VERIFY>>()
        for(tx in txs) {
            if (tx.bDependencies.isNotEmpty()) {
                if(!tx.bDependencies.any {it.type == DependencyType.BUILDS_UPON}) {
                    denied.add(Pair(tx, RejectionReason.APP_VERIFY("has dependencies, but no build-upon dependency")))
                    continue
                }
                if(maxDependencies == 1 && !tx.bDependencies.any {it.type == DependencyType.REPLACES}) {
                    denied.add(Pair(tx, RejectionReason.APP_VERIFY("has dependencies and maxDependencies == 1, but no replace dependency")))
                    continue
                }
                if(maxDependencies > 1 && !tx.bDependencies.any {it.type == DependencyType.REPLACES_PARTIAL}) {
                    denied.add(Pair(tx, RejectionReason.APP_VERIFY("has dependencies and maxDependencies > 1, but no replace-partial dependency")))
                    continue
                }
                if(tx.bDependencies.size/2 > maxDependencies) {
                    denied.add(Pair(tx, RejectionReason.APP_VERIFY("has uneven number of dependencies")))
                    continue
                }
            }

            try {
                val c = calcFromTx(tx)
                if (tx.bDependencies.isEmpty() && c !is Initial)
                    denied.add(Pair(tx, RejectionReason.APP_VERIFY("non initial transaction is missing dependencies")))
                else if (!tryUpdateResult(virtualResults, c))
                    denied.add(Pair(tx, RejectionReason.APP_VERIFY("could not update result (for example /0) - MATH ERROR")))
            } catch (ex: Exception) {
                ex.printStackTrace()
                denied.add(Pair(tx, RejectionReason.APP_VERIFY("exception thrown - ${ex.message}")))
            }
        }
        return denied
    }


    override fun newBlock(chain: Chain, block: Block) {
        for(txp in block) {
            val tx = chain[txp]
            val calculation = calcFromTx(tx)

            if(!tryUpdateResult(results, calculation))
                throw Error("This is a fatal error - how did verify not get this??") //throwing an exception here leads to inconsistent lastTransactionsInStrings states - and the exception should not occur

            for (string in calculation.strings) {
                //remove all tx up until this tx, there may have been newer once added since, but remove all up until this point
                val iterator = lastTransactionsInString(string).iterator()
                while (iterator.hasNext()) {
                    val it = iterator.next()
                    if(it == tx) break
                    else iterator.remove()
                }
            }

        }
    }

    private fun tryUpdateResult(res: HashMap<Int, Double>, c: Calculation): Boolean {
        return if(! verify(c) || c.strings.size > maxDependencies) {
            LOG.finest("!verify(c) = ${!verify(c)}")
            LOG.finest("c.strings.size > maxDependencies = ${c.strings.size > maxDependencies}")
            false
        } else {
            //todo this leads to unexpected results: a combined chain (strings: 0,1) that gets build upon by another (0,1) is calculated as:
            //       x0  x1  amount, where x0==x1 - even though it would intuitively be x01  amount
            //       also build upon algorithm has to do some complex filtering to imitate this weird behaviour
            val previousResultsInRelevantStrings = res.filterKeys { string -> c.strings.contains(string) }.map { it.value }.toDoubleArray()
            val result = c.perform(*previousResultsInRelevantStrings)
            if(java.lang.Double.isNaN(result) || java.lang.Double.isInfinite(result)) //when does not work with NaN, because it does === check - but NaN !== NaN - but NaN.equals(NaN), so(in kotlin): NaN == NaN
                false
            else {
                for (string in c.strings)
                    res[string] = result
                true
            }
        }
    }

    override fun txRemoved(chain: Chain, oldHash: TransactionHash, oldTx: Transaction, txWasPersisted: Boolean) {}
    override fun txAltered(chain: Chain, oldHash: TransactionHash, oldTx: Transaction, newHash: TransactionHash, newTx: Transaction, txWasPersisted: Boolean) {
        for(string in calcFromTx(oldTx).strings) {
            val oldIndex = lastTransactionsInString(string).indexOf(oldTx)
            if(oldIndex>=0 && oldIndex < lastTransactionsInString(string).size)
                lastTransactionsInString(string)[oldIndex] = newTx
        }
    }
    override fun txRejected(chain: Chain, oldHash: TransactionHash, oldTx: Transaction, reason: RejectionReason) {
        for(string in calcFromTx(oldTx).strings)
            lastTransactionsInString(string).remove(oldTx)
    }


    override fun shortDescriptor(tx: Transaction): String = calcFromTx(tx).toShortString()
    override fun longDescriptor(tx: Transaction): String = calcFromTx(tx).toString()
    override fun shortStateDescriptor(): String = getResults().joinToString(", ") { "%.1f".format(it) }
    override fun exhaustiveStateDescriptor(): String = "Current Results are: ${results.toList()}"





    private var lastTransactionsInStrings = HashMap<Int, MutableList<Transaction>>(numberOfInitialStates)
    private fun lastTransactionsInString(string: Int) = lastTransactionsInStrings.computeIfAbsent(string) {ArrayList()}
    fun getLastInString(string: Int): Transaction? = if(lastTransactionsInString(string).isEmpty()) null else lastTransactionsInString(string).last()
    fun addLastInString(string: Int, new: Transaction) = lastTransactionsInString(string).add(new)
    private fun updateLastInStrings(newTx: Transaction) {
        for (string in calcFromTx(newTx).strings)
            addLastInString(string, newTx)
    }
    override fun next(chain: Chain, step: Long, random: Random): Optional<Transaction> {
        val newTx = when {
            step < numberOfInitialStates -> {
                Transaction(Initial(strings = intArrayOf(step.toInt())).toTxContent())
            }
            else -> {
//                if(random.nextInt(20) == 1) {
//                    //a (likely) illegal transaction, to check whether dependency checks will take care of it
//                    Transaction(Multiplication(Double.MAX_VALUE, intArrayOf(1, 2, 3)).toTxContent(), *Array(results.size) { getLastInString(it) }.filterNotNull().flatMap { dependenciesFrom(it, DependencyType.BUILDS_UPON, DependencyType.REPLACES_PARTIAL).asIterable() }.toTypedArray())
//                } else {
                    val numberOfDependencies = 1 + random.nextInt(min(maxDependencies, numberOfInitialStates))
                    val dependencyStrings = Array(numberOfDependencies) {
                        random.nextInt(numberOfInitialStates)
                    }.distinct().toIntArray()
                    val amount = step + 1 //todo using: step % 200 +1 here causes a serious issue: after roughly 200 steps the calculations repeat - which the current dependency algorithm CANNOT handle - HASH PROBLEM
                    val newCalc =
                            when ((step % 4).toInt()) {
                                0 -> Division(amount.toDouble(), dependencyStrings)
                                1 -> Subtraction(amount.toDouble(), dependencyStrings)
                                2 -> Multiplication(amount.toDouble(), dependencyStrings)
                                else -> Addition(amount.toDouble(), dependencyStrings)
                            }

                    val dependencies = getDependenciesForStrings(dependencyStrings)
                    Transaction(newCalc.toTxContent(), *dependencies)
//                }
            }
        }

        updateLastInStrings(newTx)
        return Optional.of(newTx)
    }

    private fun getDependenciesForStrings(dependencyStrings: IntArray): Array<out Dependency> {
        val dependencyTransactions = Array(dependencyStrings.size) { getLastInString(dependencyStrings[it]) }.filterNotNull().distinct()
        return when {
            maxDependencies == 1 -> dependenciesFrom(dependencyTransactions[0], DependencyType.BUILDS_UPON, DependencyType.REPLACES)
            dependencyTransactions.size == 1 ->
                //ATTENTION::: Replace here will not work. Because in fact not always everything can and has to be replaced.
                //               with replace there is NO callback
                dependenciesFrom(dependencyTransactions[0], DependencyType.BUILDS_UPON, DependencyType.REPLACES_PARTIAL)
            else -> dependencyTransactions.flatMap { dependenciesFrom(it, DependencyType.BUILDS_UPON, DependencyType.REPLACES_PARTIAL).asIterable() }.toTypedArray()
        }
    }


    override fun getPartialReplaceSquashHandler(): PartialReplaceSquashHandler {
        return { previousContent: ByteArray, replacingTx: ByteArray ->
            val previousCalc = calcFromTx(previousContent)
            val replacingCalc = calcFromTx(replacingTx)

            if(replacingCalc.strings.toList().containsAll(previousCalc.strings.toList())) {
                null //complete replace
            } else {
                val onlyNonReplacedStrings = previousCalc.strings.filterNot { replacingCalc.strings.contains(it) }.toIntArray()
                if(onlyNonReplacedStrings.isEmpty())
                    throw SquashRejectedException("cannot replace different strings - attempted: $previousCalc -> $replacingCalc")
                previousCalc.newInstance(previousCalc.amount, onlyNonReplacedStrings).toTxContent()
            }
        }
    }

    override fun getBuildUponSquashHandler(): BuildUponSquashHandler = { buildingUpon, buildsUpon ->
            val cToReplace = calcFromTx(buildsUpon)
            val dependencyCalcs = buildingUpon.map { calcFromTx(it) }
            val dependencyAmountBuilder = HashMap<Int, Double>(numberOfInitialStates)
            for(dc in dependencyCalcs) {
                for(string in dc.strings) {
                    if(string in cToReplace.strings) {
                        val previousDependencyAmount = dependencyAmountBuilder[string]
                        if (previousDependencyAmount != null && dc.amount != previousDependencyAmount)
                            throw SquashRejectedException("conflicting previous information (depends on multiple of the same strings) - attempted: $cToReplace -> $dependencyCalcs")

                        dependencyAmountBuilder[string] = dc.amount
                    }
                }
            }
            val dependencyAmounts = dependencyAmountBuilder.map { it.value }.toDoubleArray()

            if(dependencyAmounts.size != cToReplace.strings.size)
                throw SquashRejectedException("cannot replace different strings - attempted: $dependencyCalcs -> $cToReplace")
            Initial(cToReplace.perform(*dependencyAmounts), cToReplace.strings).toTxContent()
        }



    override fun getEqualFreshCreator(): () -> VisualizableApp = { MashedCalculator(numberOfInitialStates, verify, maxDependencies) }
    override fun getCreatorParamNames() = arrayOf("number of initial states", "maximum dependencies in each string")
    override fun getCurrentParamContentForEqualCreation() = arrayOf(numberOfInitialStates.toString(), maxDependencies.toString())
    override fun createNewInstance(vararg params: String) = MashedCalculator(params[0].toInt(), verify, params[1].toInt())
    override fun createTxFrom(input: String): Transaction {
        val (operandAndValue, stringsAsString) = input.replace(" ", "").split("in")
        val operand = operandAndValue[0]
        val amount = operandAndValue.substring(1).toDouble()

        val strings = if(stringsAsString.contains(","))
                stringsAsString.replace("[", "").replace("]", "").split(",").map { it.toInt() }.toIntArray()
            else
                intArrayOf(stringsAsString.toInt())

        val newTx = Transaction(fromOperand(operand.toString(), amount, strings).toTxContent(), *getDependenciesForStrings(strings))
        updateLastInStrings(newTx)
        return newTx
    }

}








abstract class Calculation(val amount: Double, strings:IntArray = intArrayOf(1)) {
    val strings = strings.distinct().sorted().toIntArray()
    val string: Int
        get() = strings[0]

    /**
    *  Semantic(for 3 inputs: b1, b2, b3 and -):
    *  result = ( b1 - b2 - b3 ) - amount
    */
    fun perform(vararg before:Double): Double {
        return if(before.isEmpty()) {
            amount
//            return perform(neutral()) //same thing as amount..
        } else {
            //a reduce, written out for clarity
            var r = before.first()
            for (b in before.drop(1))
                r = newInstance(b, strings).perform(r)
            perform(r)
        }
    }

    abstract fun perform(before:Double): Double
    abstract fun getOperandChar(): Char
    abstract fun newInstance(amount: Double, strings: IntArray) : Calculation
    abstract fun neutral() : Double

    fun toTxContent(): ByteArray = LITagStringEncoder().addEntry_nocheck("op", getOperandChar().toString()).addEntryT_nocheck("amount", amount).addEntryT_nocheck("strings", strings).encodedBytes
    override fun toString(): String = "[${this.javaClass.simpleName}($amount), at strings: \"${strings.toList()}\")]"
    fun toShortString(): String = "${getOperandChar()}${"%.1f".format(amount)}" + " in " + (if(strings.size == 1) string else Arrays.toString(strings))
}
class Initial(amount: Double = 0.0, strings: IntArray = intArrayOf(0)) : Calculation(amount, strings) {
    override fun perform(before: Double) = amount
    override fun getOperandChar() = '='
    override fun newInstance(amount: Double, strings: IntArray) = Initial(amount, strings)
    override fun neutral(): Double = 0.0
}
class Addition(amount: Double = 0.0, strings: IntArray = intArrayOf(0)) : Calculation(amount, strings) {
    override fun perform(before: Double) = before + amount
    override fun getOperandChar() = '+'
    override fun newInstance(amount: Double, strings: IntArray) = Addition(amount, strings)
    override fun neutral(): Double = 0.0
}
class Subtraction(amount: Double = 0.0, strings: IntArray = intArrayOf(0)) : Calculation(amount, strings) {
    override fun perform(before: Double) = before - amount
    override fun getOperandChar() = '-'
    override fun newInstance(amount: Double, strings: IntArray) = Subtraction(amount, strings)
    override fun neutral(): Double = 0.0
}
class Multiplication(amount: Double = 1.0, strings: IntArray = intArrayOf(0)) : Calculation(amount, strings) {
    override fun perform(before: Double) = before * amount
    override fun getOperandChar() = '*'
    override fun newInstance(amount: Double, strings: IntArray) = Multiplication(amount, strings)
    override fun neutral(): Double = 1.0
}
class Division(amount: Double = 1.0, strings: IntArray = intArrayOf(0)) : Calculation(amount, strings) {
    override fun perform(before: Double) = before / amount
    override fun getOperandChar() = '/'
    override fun newInstance(amount: Double, strings: IntArray) = Division(amount, strings)
    override fun neutral(): Double = 1.0
}



fun calcFromTx(tx: Transaction) : Calculation = calcFromTx(tx.content)
fun calcFromTx(tx_content: ByteArray) : Calculation {
    val decoder = LITagStringEncoder()
    decoder.readFromEncodedBytes(tx_content)

    val amount = decoder.getEntryT("amount", Double::class.java) ?: throw IllegalStateException("amount not found")
    val strings = decoder.getEntryT("strings", IntArray::class.java) ?: throw IllegalStateException("string ids not found")

    return fromOperand(decoder.getEntry("op"), amount, strings)
}

fun fromOperand(operand: String, amount: Double, strings: IntArray) = when(operand) {
    "+" -> Addition(amount, strings)
    "-" -> Subtraction(amount, strings)
    "*" -> Multiplication(amount, strings)
    "/" -> Division(amount, strings)
    "=" -> Initial(amount, strings)
    else -> throw IllegalStateException("error")
}