package jokrey.mockchain.application.examples.currency

import jokrey.mockchain.Mockchain
import jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.bytes.LITagBytesEncoder
import jokrey.mockchain.squash.PartialReplaceSquashHandler
import jokrey.mockchain.squash.SquashRejectedException
import jokrey.mockchain.storage_classes.*
import jokrey.mockchain.visualization.VisualizableApp
import jokrey.mockchain.visualization.util.contentIsArbitrary
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

/**
 * Concept of currency as implemented on Cadeia a while back.
 *
 * No longer features a history, but allows removal of value transaction by writing their result into the registration transaction as initial money.
 *
 * @author jokrey
 */

class Currency : VisualizableApp {
    private val balances = HashMap<String, Long>()
    private val registrationTxs = HashMap<String, Transaction>()

    override fun verify(instance: Mockchain, blockCreatorIdentity:ImmutableByteArray, vararg txs: Transaction): List<Pair<Transaction, RejectionReason.APP_VERIFY>> {
        val denied = ArrayList<Pair<Transaction, RejectionReason.APP_VERIFY>>()

        val newlyDenied = ArrayList<Pair<Transaction, RejectionReason.APP_VERIFY>>()
        do {
            denied.addAll(newlyDenied)
            newlyDenied.clear()


            val virtualBalances = balances.toMutableMap() //creates a copy

            for(tx in txs) {
                if(denied.any { it.first == tx })
                    continue

                if(tx.bDependencies.isNotEmpty() && ! (tx.bDependencies.size == 4 &&
                        tx.bDependencies.all { it.type == DependencyType.REPLACES_PARTIAL || it.type == DependencyType.ONE_OFF_MUTATION })) {
                    newlyDenied.add(Pair(tx, RejectionReason.APP_VERIFY("dependencies illegal(have to be 0 or 4 and only replace-partial and replaced-by)")))
                    continue
                }
                try {
                    executeTransaction(virtualBalances, tx = tx) //registrations are not required for verification
                } catch(ex: IllegalAccessException) {
                    newlyDenied.add(Pair(tx, RejectionReason.APP_VERIFY("could not execute transaction - "+ex.message)))
                }
            }
        } while (newlyDenied.isNotEmpty())

        return denied
    }

    override fun newBlock(instance: Mockchain, block: Block) {
        for(txp in block) {
            val tx = instance[txp]
            executeTransaction(balances, registrationTxs, tx)
        }
    }

    override fun txRemoved(instance: Mockchain, oldHash: TransactionHash, oldTx: Transaction, txWasPersisted: Boolean) { }
    override fun txAltered(instance: Mockchain, oldHash: TransactionHash, oldTx: Transaction, newHash: TransactionHash, newTx: Transaction, txWasPersisted: Boolean) {
        val regTx = crrFromTx(oldTx) as Registration //is always a registration, since value transaction are only ever removed, never altered.
        registrationTxs[regTx.name] = newTx
    }
    override fun txRejected(instance: Mockchain, oldHash: TransactionHash, oldTx: Transaction, reason: RejectionReason) {}

    override fun shortDescriptor(tx: Transaction) = crrFromTx(tx).toShortString()
    override fun longDescriptor(tx: Transaction) = crrFromTx(tx).toString()
    override fun shortStateDescriptor() = exhaustiveStateDescriptor()
    override fun exhaustiveStateDescriptor() = balances.entries.sortedBy { it.key }.joinToString { it.key+"("+it.value+")" }



    private val possibleNames = arrayOf("Peter", "Mathilda", "Hans", "Ulrich", "Margaretha", "Augusta")
    private fun randomName(random: Random) = if(balances.isNotEmpty()) balances.keys.toList()[random.nextInt(balances.size)] else possibleNames[random.nextInt(possibleNames.size)]
    override fun next(instance: Mockchain, step: Long, random: Random) = Optional.of(
            when {
                step == 3L -> Transaction(Registration("Friederike", INITIAL_MONEY).toTxContent())
                step % 8 == 0L && balances.size != possibleNames.size -> Transaction(Registration(possibleNames[random.nextInt(possibleNames.size)], INITIAL_MONEY).toTxContent())
                else -> {
                    val sender = randomName(random)
                    val receiver = randomName(random)
                    Transaction(ValueTransaction(sender, receiver, random.nextInt(100).toLong()).toTxContent(), *appropriateDependenciesFor(sender, receiver))
                }
            })

    private fun appropriateDependenciesFor(sender: String, receiver: String) =
        dependenciesFrom(registrationTxs[sender]?: Transaction(contentIsArbitrary()), DependencyType.REPLACES_PARTIAL, DependencyType.ONE_OFF_MUTATION) +
        dependenciesFrom(registrationTxs[receiver]?: Transaction(contentIsArbitrary()), DependencyType.REPLACES_PARTIAL, DependencyType.ONE_OFF_MUTATION)


    override fun getEqualFreshCreator(): () -> VisualizableApp = { Currency() }
    override fun getCreatorParamNames():Array<String> = emptyArray()
    override fun getCurrentParamContentForEqualCreation():Array<String> = emptyArray()
    override fun createNewInstance(vararg params: String) = Currency()
    override fun createTxFrom(input: String): Transaction {
        val input = input.replace(" ", "")
        return when {
            input.startsWith("+") -> Transaction(Registration(input.substring(1), INITIAL_MONEY).toTxContent())
            input.contains("->") -> {
                val (sender, receiver, amount) = input.split("->", ",")
                Transaction(ValueTransaction(sender, receiver, amount.toLong()).toTxContent(), *appropriateDependenciesFor(sender, receiver))
            }
            else -> {
                val (amount, sender, receiver) = input.split("from", "to")
                Transaction(ValueTransaction(sender, receiver, amount.toLong()).toTxContent(), *appropriateDependenciesFor(sender, receiver))
            }
        }
    }

    override fun getPartialReplaceSquashHandler(): PartialReplaceSquashHandler = {
        previous, later ->
        val reg = crrFromTx(previous)
        val transfer = crrFromTx(later)
        if(reg is Registration && transfer is ValueTransaction) {
            when(reg.name) {
                transfer.sender -> {
                    if(reg.money - transfer.amount < 0)
                        throw SquashRejectedException("illegal - app verify should have caught this before")
                    Registration(reg.name, reg.money - transfer.amount).toTxContent()
                }
                transfer.receiver -> Registration(reg.name, reg.money + transfer.amount).toTxContent()
                else -> throw SquashRejectedException("transfer referred to the wrong registration ( name mismatch )")
            }
        } else {
            throw SquashRejectedException("invalid edge (either dependency is not a registration or depending transaction is not a value transfer)")
        }
    }
}

const val INITIAL_MONEY: Long = 5000L

fun executeTransaction(balances: MutableMap<String, Long>, registrationTxs: MutableMap<String, Transaction>? = null, tx: Transaction) {
    val currencyTransaction = crrFromTx(tx)
    if(currencyTransaction is ValueTransaction) {
        val senderBalance = balances[currencyTransaction.sender]
        val receiverBalance = balances[currencyTransaction.receiver]

        // actual signing of a transaction by the sender is omitted for this example
        if(senderBalance == null) throw IllegalAccessException("sender unknown")
        if(receiverBalance == null) throw IllegalAccessException("receiver unknown")
        if(senderBalance - currencyTransaction.amount < 0) throw IllegalAccessException("sender has insufficient funds")
        if(currencyTransaction.sender == currencyTransaction.receiver) throw IllegalAccessException("sender == receiver")
        if(currencyTransaction.amount <= 0) throw IllegalAccessException("transaction value 0 or less")
        if(receiverBalance + currencyTransaction.amount < 0) throw IllegalAccessException("receiver balance would overflow")
        balances.computeIfPresent(currencyTransaction.sender) { _, previous ->
            previous - currencyTransaction.amount
        }
        balances.computeIfPresent(currencyTransaction.receiver) { _, previous ->
            previous + currencyTransaction.amount
        }
    } else if(currencyTransaction is Registration) {
        if(balances.contains(currencyTransaction.name))
            throw IllegalAccessException("account with that name already registered")
        else {
            balances[currencyTransaction.name] = currencyTransaction.money
            if(registrationTxs!=null)
                registrationTxs[currencyTransaction.name] = tx
        }
    }
}



abstract class CurrencyStatePart {
    abstract fun toTxContent(): ByteArray
    abstract override fun toString() : String
    abstract fun toShortString() : String
}

data class ValueTransaction(val sender:String, val receiver:String, val amount: Long, val timestamp:Long = 0) : CurrencyStatePart() {
    override fun toShortString() = "$sender->$receiver, $amount§"
    override fun toString() = "$amount from $sender to $receiver"
    override fun toTxContent(): ByteArray = LITagBytesEncoder().addEntryT_nocheck("class", this.javaClass.simpleName).
            addEntryT_nocheck("sender", sender).
            addEntryT_nocheck("receiver", receiver).
            addEntryT_nocheck("amount", amount).
            addEntryT_nocheck("timestamp", timestamp).encodedBytes
}

data class Registration(val name:String, val money:Long) : CurrencyStatePart() {
    override fun toShortString() = "+$name!$money"
    override fun toString() = "$name registered with $money"
    override fun toTxContent(): ByteArray = LITagBytesEncoder().addEntryT_nocheck("class", this.javaClass.simpleName).addEntryT_nocheck("name", name).addEntryT_nocheck("money", money).encodedBytes
}


fun crrFromTx(tx: Transaction) = crrFromTx(tx.content)
fun crrFromTx(tx_content: ByteArray) : CurrencyStatePart {
    val decoder = LITagBytesEncoder()
    decoder.readFromEncodedBytes(tx_content)

    return when(decoder.getEntryT("class", String::class.java)) {
        "ValueTransaction" -> {
            val sender = decoder.getEntryT("sender", String::class.java) ?: throw IllegalStateException("sender not found")
            val receiver = decoder.getEntryT("receiver", String::class.java) ?: throw IllegalStateException("receiver not found")
            val amount = decoder.getEntryT("amount", Long::class.java) ?: throw IllegalStateException("amount not found")
            val timestamp = decoder.getEntryT("timestamp", Long::class.java) ?: throw IllegalStateException("timestamp not found")
            ValueTransaction(sender, receiver, amount, timestamp)
        }
        "Registration" -> {
            val name = decoder.getEntryT("name", String::class.java) ?: throw IllegalStateException("name not found")
            val money = decoder.getEntryT("money", Long::class.java) ?: throw IllegalStateException("money not found")
            Registration(name, money)
        }
        else -> throw IllegalStateException("error")
    }
}