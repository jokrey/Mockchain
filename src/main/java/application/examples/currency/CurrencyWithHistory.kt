package application.examples.currency

import storage_classes.*
import visualization.VisualizableApp
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

/**
 * Concept of currency as implemented on Cadeia a while back.
 *
 * @author jokrey
 */

class CurrencyWithHistory : VisualizableApp {
    private val balances = HashMap<String, Long>()
    private val history = ArrayList<Triple<String, String, Long>>()

    override fun verify(chain: Chain, vararg txs: Transaction): List<Pair<Transaction, RejectionReason.APP_VERIFY>> {
        val denied = ArrayList<Pair<Transaction, RejectionReason.APP_VERIFY>>()

        val virtualBalances = balances.toMutableMap() //creates a copy

        for(tx in txs) {
            if(tx.bDependencies.isNotEmpty()) {
                denied.add(Pair(tx, RejectionReason.APP_VERIFY("has dependencies")))
                continue
            }
            try {
                executeTransaction(virtualBalances, tx = tx)
            } catch(ex: IllegalAccessException) {
                denied.add(Pair(tx, RejectionReason.APP_VERIFY("could not execute transaction - "+ex.message)))
            }
        }

        return denied
    }

    override fun newBlock(chain: Chain, block: Block) {
        for(txp in block) {
            val tx = chain[txp]
            executeTransaction(balances, tx = tx)
            val csp = crrFromTx(tx)
            if(csp is ValueTransaction) {
                history.add(Triple(csp.sender, csp.receiver, csp.amount))
            }
        }
    }

    override fun txRemoved(chain: Chain, oldHash: TransactionHash, oldTx: Transaction, txWasPersisted: Boolean) { }
    override fun txAltered(chain: Chain, oldHash: TransactionHash, oldTx: Transaction, newHash: TransactionHash, newTx: Transaction, txWasPersisted: Boolean) { }
    override fun txRejected(chain: Chain, oldHash: TransactionHash, oldTx: Transaction, reason: RejectionReason) {}

    override fun shortDescriptor(tx: Transaction) = crrFromTx(tx).toShortString()
    override fun longDescriptor(tx: Transaction) = crrFromTx(tx).toString()
    override fun shortStateDescriptor() = exhaustiveStateDescriptor()
    override fun exhaustiveStateDescriptor() = balances.entries.sortedBy { it.key }.joinToString { it.key+"("+it.value+")" } + " - history: "+history.joinToString { "${it.first}-${it.third}>${it.second}" }



    private val possibleNames = arrayOf("Peter", "Mathilda", "Hans", "Ulrich", "Margaretha", "Augusta")
    private fun randomName(random: Random) = possibleNames[random.nextInt(possibleNames.size)]
    override fun next(chain: Chain, step: Long, random: Random) = Optional.of(
            when {
                step == 3L -> Transaction(Registration("Friederike", INITIAL_MONEY).toTxContent())
                step % 8 == 0L -> Transaction(Registration(randomName(random), INITIAL_MONEY).toTxContent())
                else -> Transaction(ValueTransaction(randomName(random), randomName(random), Random().nextInt(100).toLong()).toTxContent())
            })


    override fun getEqualFreshCreator(): () -> VisualizableApp = { CurrencyWithHistory() }
    override fun getCreatorParamNames():Array<String> = emptyArray()
    override fun getCurrentParamContentForEqualCreation():Array<String> = emptyArray()
    override fun createNewInstance(vararg params: String) = CurrencyWithHistory()
    override fun createTxFrom(input: String): Transaction {
        val input = input.replace(" ", "")
        return when {
            input.startsWith("+") -> Transaction(Registration(input.substring(1), INITIAL_MONEY).toTxContent())
            input.contains("->") -> {
                val (sender, receiver, amount) = input.split("->", ",")
                Transaction(ValueTransaction(sender, receiver, amount.toLong()).toTxContent())
            }
            else -> {
                val (amount, sender, receiver) = input.split("from", "to")
                Transaction(ValueTransaction(sender, receiver, amount.toLong()).toTxContent())
            }
        }
    }
}