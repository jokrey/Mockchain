package jokrey.mockchain.squash

import jokrey.mockchain.storage_classes.*
import java.util.*
import java.util.logging.Logger
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.collections.LinkedHashMap

val LOG = Logger.getLogger("SquashAlgorithm")

/**
 * This function is called when the algorithm hits a 'partial-replace' edge
 * It is meant to calculate the new next of the partially replaced old next
 *
 * First argument is the current content of the pointer to be altered (maybe different to the content stored in the transaction pointer, if an change has been made during this squash cycle)
 * Second argument is the current content of the replacing tx
 * As return the new content of the old TransactionPointer (first arg) is expected - or null if the transaction has been fully replaced now
 *
 * HAS TO BE DETERMINISTIC AND IDEMPOTENT
 *   NO CHANGES TO ANY INTERNAL STATE ARE ALLOWED
 *   this is due to the fact that the operation may be run multiple times within the same squash cycle(verification + squash itself) for the same inputs - it has to yield the same result
 *       this is not generally speaking a problem since a blockchain application should only alter it's internal state when a new 'permanent' block is added to the chain - or on txAltered and txRemoved
 *
 * returns new content at stead of TransactionHash(first parameter)
 * throws SquashRejectedException
 */
typealias PartialReplaceSquashHandler = (ByteArray, ByteArray) -> ByteArray?

/**
 * This function is called when the algorithm hits a 'build-upon' edge
 * It will be called with all build-upon dependencies a transaction has (n deps to 1 tx)
 *
 * First argument is all the transactions and their latest content(potentially updated by the return of this method previously)
 * Second argument is the transaction that builds upon it's dependencies and will be replaced
 * As return the new content of the transaction that builds upon it's dependencies
 *
 * HAS TO BE DETERMINISTIC AND IDEMPOTENT
 *   NO CHANGES TO ANY INTERNAL STATE ARE ALLOWEDPartialReplaceSquashHandler
 *   this is due to the fact that the operation may be run multiple times within the same squash cycle(verification + squash itself) for the same inputs - it has to yield the same result
 *       this is not generally speaking a problem since a blockchain application should only alter it's internal state when a new 'permanent' block is added to the chain - or on txAltered and txRemoved
 *
 * returns new content at stead of transaction(second parameter)
 * throws SquashRejectedException
 */
typealias BuildUponSquashHandler = (List<ByteArray>, ByteArray) -> ByteArray?

/**
 * This function is called when the algorithms hits a 'sequence-end' edge
 * It will be called with all latest contents of the transactions in the sequence (i.e. all sequence-parts prior to the sequence-end)
 *
 * HAS TO BE DETERMINISTIC AND IDEMPOTENT
 *   NO CHANGES TO ANY INTERNAL STATE ARE ALLOWEDPartialReplaceSquashHandler
 *   this is due to the fact that the operation may be run multiple times within the same squash cycle(verification + squash itself) for the same inputs - it has to yield the same result
 *       this is not generally speaking a problem since a blockchain application should only alter it's internal state when a new 'permanent' block is added to the chain - or on txAltered and txRemoved
 *
 * returns new content at stead of the current, latest, transaction that has the sequence-end dependency
 * throws SquashRejectedException
 */
typealias SequenceSquashHandler = (List<ByteArray>) -> ByteArray

class SquashRejectedException(why: String = "Squash has been rejected for a reason") : Exception(why)


/**
 * The algorithm will find changes and determine denied transactions in one.
 * It can be used when there is no prior state, i.e. in the rare case of using this algorithm without a chain.
 *
 * Denied transactions can be propagated to the application, changes either introduced or discarded to be re-found in the next consensus round.
 */
fun findChangesAndDeniedTransactions(chain: Chain, partialReplaceCallback: PartialReplaceSquashHandler, buildUponCallback: BuildUponSquashHandler, sequenceCallback: SequenceSquashHandler,
                                     subset: Array<Transaction>) : Pair<LinkedHashMap<TransactionHash, VirtualChange>, List<Pair<Transaction, RejectionReason.SQUASH_VERIFY>>> {
    val state = findChanges(chain, partialReplaceCallback, buildUponCallback, sequenceCallback, null, subset)
    return Pair(state.virtualChanges, state.rejections)
}

/**
 * From the supplied mem pool the selection algorithm will determine which transactions the algorithm needs to handle
 *     If 'priorState' is null, i.e. not available, the algorithm will use reverseDependencies to select the transactions
 *     If 'priorState' is not null it is required to have been produced by a previous iteration of this algorithm.
 *        In one of those iterations 'priorState' is required to have been null.
 *
 * The selected transactions will be sorted and multi level dependencies will be eliminated.
 *
 * From then every transaction in the remaining set will be handled according to the rules set in the concept and with the 'findChangesForTx' function.
 *
 * Each iteration can either change the state, not change the state or have the transaction be rejected.
 *     Every subsequent transaction that has a dependency on the rejected transaction will also be rejected.
 *
 */
fun findChanges(chain: Chain,
                partialReplaceCallback: PartialReplaceSquashHandler,
                buildUponCallback: BuildUponSquashHandler,
                sequenceCallback: SequenceSquashHandler,
                priorState: SquashAlgorithmState?,
                proposed: Array<Transaction>): SquashAlgorithmState {

    //important pre-condition ensured by selectStateAndSubset:
    // 1. Every tx of tx.bDependencies of every tx in 'selectStateAndSubset' is also in 'selectStateAndSubset'
    val (state, selectedSubset) = selectStateAndSubset(chain, priorState, proposed)

    //important pre-conditions ensured by dependencyLevelSortWithinBlockBoundariesButAlsoEliminateMultiLevelDependencies:
    // 1. Every Transaction's dependencies are at a smaller index than the transaction itself + the entire dependency tree exists + acyclic
    // 2. All hashes in chain and toConsider are unique
    // 3. toConsider tx have no illegal multi level dependencies
    val (sortedFilteredTxToConsider, denied) = dependencyLevelSortWithinBlockBoundariesButAlsoEliminateMultiLevelDependencies(selectedSubset)

    handleSortDeniedTxs(state, denied)

    for(tx in sortedFilteredTxToConsider) {
        if(tx.bDependencies.isEmpty()) continue

        val resolver = proposed.asTxResolver().combineWith(chain)
        determineStateAlterationsFrom(tx, resolver, state, partialReplaceCallback, buildUponCallback, sequenceCallback)
    }

    return state
}

fun selectStateAndSubset(chain: Chain, priorState: SquashAlgorithmState?, proposed: Array<Transaction>):
        Pair<SquashAlgorithmState, Iterable<Transaction>> {
// return chain.getPersistedTransactions() + subset                                 // too slow
// return completeDependencyList(chain, reverseDependencies, *subset).asIterable()  //this creates an issue:: The unfound dependency problem persists here
//                                                                                  //      since through the intense hash checks transactions now have relationships that are invisible to any dependencies
    return if(priorState == null) {
        //it is assumed here that either the chain was restarted or this is the first round
        return Pair(
                SquashAlgorithmState(),
                chain.getAllTransactionWithDependenciesOrThatAreDependedUpon() +
                 proposed.asIterable()
        )
    } else {
        Pair(SquashAlgorithmState(priorState), proposed.asIterable())
    }
}

fun handleSortDeniedTxs(state: SquashAlgorithmState, denied: List<Transaction>) {
    for(deniedTx in denied) {
        state.deny(deniedTx, SquashRejectedException("dependency loop detected"))
        state.virtualChanges[deniedTx.hash] = VirtualChange.Error
    }
}

fun determineStateAlterationsFrom(tx: Transaction,
                                  resolver: TransactionResolver, state: SquashAlgorithmState,
                                  partialReplaceCallback: PartialReplaceSquashHandler,
                                  buildUponCallback: BuildUponSquashHandler,
                                  sequenceCallback: SequenceSquashHandler) {
    try {
        val uncommittedState = findChangesForTx(tx, resolver, state, buildUponCallback, partialReplaceCallback, sequenceCallback)

        verifyAllHashesAvailable(resolver, state, uncommittedState)

        commitState(state, uncommittedState)
    } catch (ex: Exception) {
        state.deny(tx, ex)
    }
}

fun findChangesForTx(tx: Transaction, resolver: TransactionResolver, state: SquashAlgorithmState,
                     buildUponCallback: BuildUponSquashHandler,
                     partialReplaceCallback: PartialReplaceSquashHandler,
                     sequenceCallback: SequenceSquashHandler): SquashAlgorithmState {
    verifyNoDoubleEdges(tx.bDependencies)

    val uncommittedState = generateUncommittedState(state)

    handleBuildUponDependencies(tx, buildUponCallback, resolver, state, uncommittedState)

    handleReplaceDependencies(tx, resolver, state, uncommittedState)

    handlePartialReplaceDependencies(tx, partialReplaceCallback, resolver, state, uncommittedState)

    handleReplacedByDependencies(tx, state, uncommittedState)

    handleSequenceDependencies(tx, sequenceCallback, resolver, state, uncommittedState)

    return uncommittedState
}

fun handleBuildUponDependencies(tx: Transaction, buildUponCallback: BuildUponSquashHandler,
                                resolver: TransactionResolver, state: SquashAlgorithmState, uncommittedState: SquashAlgorithmState) {
    val buildUponDependencies = tx.bDependencies.filter { it.type == DependencyType.BUILDS_UPON }
    val dependenciesWithUpdatedContent = ArrayList<ByteArray>(buildUponDependencies.size)
    for (dependency in buildUponDependencies) {
        val toBuildUponContent = latestTxContent(resolver, state, uncommittedState, dependency.txp, failIfSequence = false)
        dependenciesWithUpdatedContent.add(toBuildUponContent)
    }
    if (dependenciesWithUpdatedContent.isNotEmpty()) {
        val newContent = buildUponCallback(dependenciesWithUpdatedContent, latestTxContent(resolver, state, uncommittedState, tx.hash))
        when {
            newContent == null || newContent.isEmpty() -> {
                overrideChangeAt(state, uncommittedState, tx.hash, VirtualChange.Deletion)
            } //all of it has been replaced by the previous transactions
            else -> overrideChangeAt(state, uncommittedState, tx.hash, VirtualChange.Alteration(newContent)) //override fine, because txp is unaltered, because changes are only done backwards or in place
        }
    }
}

fun handleReplaceDependencies(tx: Transaction,
                              resolver: TransactionResolver, state: SquashAlgorithmState, uncommittedState: SquashAlgorithmState) {
    val replaceDependencies = tx.bDependencies.filter { it.type == DependencyType.REPLACES }
    for (dependency in replaceDependencies) {
        when(latestChange(state, uncommittedState, dependency.txp)) {
            VirtualChange.Error -> throw SquashRejectedException("dependency had an error, this tx is subsequently marked as illegal")
            is VirtualChange.PartOfSequence -> throw SquashRejectedException("dependency is part of sequence should be considered deleted and can therefore not be replaced")
            VirtualChange.Deletion -> throw SquashRejectedException("prohibited 1 to n dependency on replace edge (no two transactions can replace the same transaction[dev error, not ignored because it goes against the principle of minimization])")
            null, is VirtualChange.DependencyAlteration, is VirtualChange.Alteration -> { //changes have to be legal here now, because build-upon edges, might incrementally alter, but later request deletion
                if(! resolver.contains(dependency.txp))
                    throw SquashRejectedException("unresolved dependency detected")
                overrideChangeAt(state, uncommittedState, dependency.txp, VirtualChange.Deletion)
                uncommittedState.virtualChanges.computeIfAbsent(tx.hash) {VirtualChange.DependencyAlteration(emptyArray())} //does nothing(causes no problems) if any other edge alters txp
            }
        }
    }
}

fun handlePartialReplaceDependencies(tx: Transaction, partialReplaceCallback: PartialReplaceSquashHandler,
                                     resolver: TransactionResolver, state: SquashAlgorithmState, uncommittedState: SquashAlgorithmState) {
    val replacePartialDependencies = tx.bDependencies.filter { it.type == DependencyType.REPLACES_PARTIAL }
    for (dependency in replacePartialDependencies) {
        val toPartiallyReplaceContent = latestTxContent(resolver, state, uncommittedState, dependency.txp, failIfSequence = true)
        val newContent = partialReplaceCallback(toPartiallyReplaceContent, latestTxContent(state, uncommittedState, tx.hash, failIfSequence =true) {tx.content})
        if(newContent == null || newContent.isEmpty()) { //all of it has been replaced
            overrideChangeAt(state, uncommittedState, dependency.txp, VirtualChange.Deletion)
        } else {
            overrideChangeAt(state, uncommittedState, dependency.txp, VirtualChange.Alteration(newContent))
        }
        uncommittedState.virtualChanges.computeIfAbsent(tx.hash) {VirtualChange.DependencyAlteration(emptyArray())} //does nothing(causes no problems) if any other edge alters txp
    }
}

fun handleReplacedByDependencies(tx: Transaction,
                                 state: SquashAlgorithmState, uncommittedState: SquashAlgorithmState) {
    if(tx.bDependencies.any { it.type == DependencyType.ONE_OFF_MUTATION }) {
        if(latestChange(state, uncommittedState, tx.hash) is VirtualChange.Alteration)
            throw SquashRejectedException("replaced by dependency detected an illegal previous alteration")
        overrideChangeAt(state, uncommittedState, tx.hash, VirtualChange.Deletion)
    }
}


fun handleSequenceDependencies(tx: Transaction, sequenceCallback: SequenceSquashHandler,
                               resolver: TransactionResolver, state: SquashAlgorithmState, uncommittedState: SquashAlgorithmState) {
    val sequenceDependencies = tx.bDependencies.filter(DependencyType.SEQUENCE_PART, DependencyType.SEQUENCE_END)
    if(sequenceDependencies.isEmpty()) return

    val dependency = sequenceDependencies.first()
    if (sequenceDependencies.size > 1) //this takes care of n to 1 bDependencies
        throw SquashRejectedException("a tx cannot be part of multiple sequences - n to 1 on sequence edge detected")
    if(latestChange(state, uncommittedState, tx.hash) == VirtualChange.Deletion)
        throw SquashRejectedException("sequence tx cannot be deleted")
    if(! resolver.contains(dependency.txp))
        throw SquashRejectedException("unresolved dependency detected")

    if(dependency.txp in state.sequences)
        throw SquashRejectedException("a tx cannot be part of multiple sequences")

    if (dependency.type == DependencyType.SEQUENCE_PART) {
        handleSequencePartDependency(tx, dependency, state, uncommittedState)
    } else if (dependency.type == DependencyType.SEQUENCE_END) {
        handleSequenceEndDependency(tx, sequenceCallback, resolver, state, uncommittedState)
    }
}

fun handleSequencePartDependency(tx: Transaction, dependency: Dependency,
                                 state: SquashAlgorithmState, uncommittedState: SquashAlgorithmState) {
    when(val depChange = latestChange(state, uncommittedState, dependency.txp)) {
        VirtualChange.Deletion, VirtualChange.Error -> throw SquashRejectedException("illegal state of dependency in sequence")
        null, is VirtualChange.PartOfSequence, is VirtualChange.DependencyAlteration -> {
            overrideChangeAt(state, uncommittedState, tx.hash, VirtualChange.PartOfSequence(null))
        }
        is VirtualChange.Alteration -> {
            //assuming the dependency has previously been altered, then assume that this sequence won't end
            //    in that case the change of the dependency txp will be committed to the chain - thereby changing it's hash
            //    that means this dependency txp (MIND YOU: of completely valid and persisted txs) would point into emptyness
            //    therefore we will need to repoint that txp
            //  in case the sequence does end, this change will be overridden by a deletion - in that case this effort was meaningless
            //     but we don't know that yet
            val updatedDependencies = tx.bDependencies.replace(dependency, TransactionHash(depChange.newContent))
            overrideChangeAt(state, uncommittedState, tx.hash, VirtualChange.PartOfSequence(updatedDependencies))
        }
    }

    uncommittedState.sequences.add(dependency.txp)
}

fun handleSequenceEndDependency(tx:Transaction, sequenceCallback: SequenceSquashHandler,
                                resolver: TransactionResolver, state: SquashAlgorithmState, uncommittedState: SquashAlgorithmState) {
    val sequence = LinkedList<ByteArray>()

    var prior: TransactionHash
    var priorDependencies = tx.bDependencies
    sequence.add(latestTxContent(resolver, state, uncommittedState, tx.hash))
    do {
        // the sequence part checks ensure this always works
        prior = priorDependencies.find(DependencyType.SEQUENCE_PART, DependencyType.SEQUENCE_END).txp
        sequence.addFirst(latestTxContent(resolver, state, uncommittedState, prior, false))
        overrideChangeAt(state, uncommittedState, prior, VirtualChange.Deletion)

        priorDependencies = resolver[prior].bDependencies
    } while(priorDependencies.size == 1 && priorDependencies.any { it.type == DependencyType.SEQUENCE_PART } )

    overrideChangeAt(state, uncommittedState, tx.hash, VirtualChange.Alteration(sequenceCallback(sequence)))
}

fun generateUncommittedState(state: SquashAlgorithmState): SquashAlgorithmState {
    val new = SquashAlgorithmState()
    new.reservedHashes.putAll(state.reservedHashes)
    return new
}

fun commitState(state: SquashAlgorithmState, uncommittedState: SquashAlgorithmState) {
    state.reservedHashes.clear()
    state.reservedHashes.putAll(uncommittedState.reservedHashes)

    for((uncommittedTxp, uncommittedChange) in uncommittedState.virtualChanges) {
        val oldChange = state.virtualChanges[uncommittedTxp]
        if(oldChange is VirtualChange.Alteration) {
            state.unreserveHash(TransactionHash(oldChange.newContent), uncommittedTxp)
        }
        if(uncommittedChange is VirtualChange.Alteration) {
            state.reserveHashIfAvailable(TransactionHash(uncommittedChange.newContent), uncommittedTxp)
        }

        val latestReserver = latestReserver(state, uncommittedState, uncommittedTxp)
        if(latestReserver != null && (uncommittedChange is VirtualChange.Deletion || uncommittedChange is VirtualChange.Alteration) ) {
            //if there is a reserver we have a problem: It means that the hash we are trying to delete here will be occupied again
            //   that is fine, you may think - the problem is that the alteration is done in the past
            //   meaning we would override it with this change. More specifically we would override THAT there has been an alteration in the past
            //     an alteration that might free a hash. Which could have been the basis for allowing an alteration TO that hash.
            //   therefore we override the previous change in place
            state.virtualChanges[uncommittedTxp] = uncommittedChange
        } else {
            state.virtualChanges.remove(uncommittedTxp) // linked hash map only guarantees insertion order on first insertion, so we need to make sure uncommitted txp is not reinserted in the next line
            state.virtualChanges[uncommittedTxp] = uncommittedChange
        }
    }

    state.sequences.addAll(uncommittedState.sequences)
}

private fun verifyNoDoubleEdges(dependencies: List<Dependency>) {
    val doubleEdgeExists = dependencies.groupingBy { it }.eachCount().values.all { it == 1 }
    if(! doubleEdgeExists)
        throw SquashRejectedException("detected duplicate edge on transaction")
}

fun latestChange(state: SquashAlgorithmState, uncommittedState: SquashAlgorithmState, txp: TransactionHash): VirtualChange? {
    return uncommittedState.virtualChanges[txp]?: state.virtualChanges[txp]
}

fun latestReserver(state: SquashAlgorithmState, uncommittedState: SquashAlgorithmState, txp: TransactionHash): TransactionHash? {
    return uncommittedState.reservedHashes[txp]
}


fun overrideChangeAt(state: SquashAlgorithmState, uncommittedState: SquashAlgorithmState, txp: TransactionHash, newChange: VirtualChange) {
    val oldChange = latestChange(state, uncommittedState, txp)
    if(oldChange is VirtualChange.Alteration) {
        uncommittedState.unreserveHash(TransactionHash(oldChange.newContent), txp)
    }

    uncommittedState.virtualChanges[txp] =  newChange
    if(newChange is VirtualChange.Alteration) {
        val toBeReserved = TransactionHash(newChange.newContent)

        uncommittedState.reserveHashIfAvailable(toBeReserved, txp)
    }
}

fun latestTxContent(resolver: TransactionResolver, state: SquashAlgorithmState, uncommittedState: SquashAlgorithmState, txp: TransactionHash, failIfSequence: Boolean = true) =
        latestTxContent(state, uncommittedState, txp, failIfSequence) {
            resolver.getUnsure(txp)?.content ?: throw SquashRejectedException("resolving dependency($txp) failed")
        }
fun latestTxContent(state: SquashAlgorithmState, uncommittedState: SquashAlgorithmState, txp: TransactionHash, failIfSequence: Boolean, contentQuery: () -> ByteArray) =
    latestTxContent(uncommittedState, txp, failIfSequence) {
        latestTxContent(state, txp, failIfSequence, contentQuery)
    }

fun latestTxContent(state: SquashAlgorithmState, txp: TransactionHash, failIfSequence: Boolean, contentQuery: () -> ByteArray) =
    when(val change = state.virtualChanges[txp]) {
        VirtualChange.Deletion -> throw SquashRejectedException("requested txp($txp) has been virtually deleted")
        VirtualChange.Error -> throw SquashRejectedException("requested txp has been marked as having an error - this tx should also be marked as such")
        is VirtualChange.PartOfSequence ->
            if(failIfSequence) throw SquashRejectedException("tx part of sequence - consider deleted")
            else contentQuery()
        is VirtualChange.Alteration ->
            change.newContent
        null, is VirtualChange.DependencyAlteration ->
            contentQuery()
    }



tailrec fun verifyAllHashesAvailable(resolver:TransactionResolver, state: SquashAlgorithmState, uncommittedState: SquashAlgorithmState) {
    var redo = false
    for ((uncommittedTxp, uncommittedChange) in uncommittedState.virtualChanges.filter { it.value is VirtualChange.Alteration }) {
        if (uncommittedChange is VirtualChange.Alteration) {
            val newTxp = TransactionHash(uncommittedChange.newContent)
            if (!hashAvailable(uncommittedTxp, newTxp, resolver, state, uncommittedState))
                throw SquashRejectedException("an edge generated a tx with hash content that already exists in the chain ($uncommittedTxp -> $newTxp)")

            val indexOfThisChange = uncommittedState.virtualChanges.keys.indexOf(uncommittedTxp)
            val indexOfNewTxpChange = uncommittedState.virtualChanges.keys.indexOf(newTxp)
            if (indexOfNewTxpChange == -1 || indexOfNewTxpChange < indexOfThisChange || uncommittedTxp == newTxp) {
                //only if new txp is changed BEFORE this change we can allow it - otherwise it is in the wrong order and causes a hash issue for the chain reintroduction
                //  the hash issue needs to be adressed within chain aswell, because of an incredibly complicated and rare issue in which
                //    a tx is altered(its hash is freed) - that hash is reoccupied by an alteration - the original tx is deleted(the virtual change from no1 is overriden)
                //    in that case everything would work logically, but in step two the hash cannot be safely reoccupied, because its hash will only become free upon deletion(which is later)
            } else {
                uncommittedState.virtualChanges.remove(uncommittedTxp)
                uncommittedState.virtualChanges[uncommittedTxp] = uncommittedChange
                redo = true
                break
            }
        }
    }
    if(redo)
        verifyAllHashesAvailable(resolver, state, uncommittedState)
}
fun hashAvailable(oldTxp: TransactionHash, newTxp: TransactionHash,
                  resolver: TransactionResolver, state: SquashAlgorithmState, uncommittedState: SquashAlgorithmState): Boolean {
    if(oldTxp == newTxp) return true
    val latestReserver = latestReserver(state, uncommittedState, newTxp)
    if(latestReserver == null || latestReserver == oldTxp) {
        val latestChangeAtNewTxp = latestChange(state, uncommittedState, newTxp)
        if(latestChangeAtNewTxp != null) {
            if (latestChangeAtNewTxp is VirtualChange.Deletion)
                return true

            if (latestChangeAtNewTxp is VirtualChange.Alteration) {
                val nextHash = TransactionHash(latestChangeAtNewTxp.newContent)

                if(nextHash == latestReserver) return false // this indicates a hash flip - something that cannot be handled by a hash collision detecting reintroduction algorithm
                if (hashAvailable(newTxp, nextHash, resolver, state, uncommittedState)) {
                    return true
                }
            }
        } else {
            if (newTxp !in resolver)
                return true
        }
    }
    return false
}



sealed class VirtualChange {
    object Deletion : VirtualChange()
    data class Alteration(val newContent:ByteArray) : VirtualChange() {
        override fun equals(other: Any?) = this === other || (javaClass == other?.javaClass && other is Alteration && newContent.contentEquals(other.newContent))
        override fun hashCode() = newContent.contentHashCode()
    }
    open class DependencyAlteration(val newDependencies: Array<Dependency>?) : VirtualChange() {
        override fun equals(other: Any?) = this === other || (javaClass == other?.javaClass && other is DependencyAlteration && (newDependencies === other.newDependencies || (newDependencies!=null && other.newDependencies!= null && newDependencies.contentEquals(other.newDependencies))))
        override fun hashCode() = newDependencies?.contentHashCode() ?: 13
        override fun toString() = "[${javaClass.simpleName}(newDependencies=${newDependencies?.toList() ?: "null"})]"
    }
    class PartOfSequence(newDependencies: Array<Dependency>?) : DependencyAlteration(newDependencies)
    object Error : VirtualChange()
}

class SquashAlgorithmState {
    constructor()
    constructor(priorState: SquashAlgorithmState) {
        virtualChanges.putAll(priorState.virtualChanges.filter { it.value != VirtualChange.Error })
        sequences.addAll(priorState.sequences)
        reservedHashes.putAll(priorState.reservedHashes)
    }

    val virtualChanges = LinkedHashMap<TransactionHash, VirtualChange>()
    val sequences = HashSet<TransactionHash>()
    val reservedHashes = HashMap<TransactionHash, TransactionHash>() //map from reserved hash to it's "reserver"
    val rejections = ArrayList<Pair<Transaction, RejectionReason.SQUASH_VERIFY>>()

    fun reset() {
        virtualChanges.forEach {
            if(it.value is VirtualChange.Deletion)
                sequences.remove(it.key) //not all sequence marker can be removed, only those that have been handled and are no longer needed
        }
        virtualChanges.clear()
        reservedHashes.clear()
        rejections.clear()
    }



    fun deny(tx: Transaction, ex: Exception) {
        rejections.add(Pair(tx, RejectionReason.SQUASH_VERIFY(ex.message?:"no reason given")))
        virtualChanges[tx.hash] = VirtualChange.Error //to create a recursive exception - if the illegal tx is required by a further tx in the loop

        LOG.warning("ex on (${tx.hash}): $ex")
        if(ex !is SquashRejectedException) ex.printStackTrace()
    }

    fun reserveHashIfAvailable(toBeReserved: TransactionHash, reserver: TransactionHash) {
        LOG.finest("reserving $toBeReserved for $reserver  - happened: ${reservedHashes.containsKey(toBeReserved)}")
        reservedHashes.computeIfAbsent(toBeReserved) {reserver}
    }
    fun unreserveHash(toBeUnReserved: TransactionHash, reserver: TransactionHash) {
        LOG.finest("unreserving $toBeUnReserved for $reserver  - prior(${reservedHashes[toBeUnReserved]}), happened: ${reservedHashes[toBeUnReserved] == reserver}")
        reservedHashes.remove(toBeUnReserved, reserver)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SquashAlgorithmState
        return virtualChanges == other.virtualChanges && sequences == other.sequences && reservedHashes == other.reservedHashes
    }
    override fun hashCode() = 31 * 31 * virtualChanges.hashCode() + sequences.hashCode() + reservedHashes.hashCode()
    override fun toString() = "SquashAlgorithmState(virtualChanges=$virtualChanges, sequences=$sequences, reservedHashes=$reservedHashes)"
}


/**
 * the reason the less efficient sort within block boundaries is required is the following fringe case:
 *  if a tx0 is depended on by tx1 and tx2, where tx1 is in a block and tx2 is in the mem pool
 *  then the order of tx1 and tx2 would be undefined, except that tx1 HAS to come before tx2
 *    otherwise this algorithm could decide to reject tx1 - which is illegal because tx1 is persisted
 */
fun dependencyLevelSortWithinBlockBoundariesButAlsoEliminateMultiLevelDependencies(toSort: Iterable<Transaction>): Pair<List<Transaction>, List<Transaction>> {
    val denied = ArrayList<Transaction>()

    val levels = fillLevelsFor(toSort)

    return Pair(
        toSort.distinct().filter { tx ->
            when(levels.getValue(tx)) {
                -1 -> {
                    denied.add(tx) //reflexive dependency
                    false
                }
                -2 -> {
                    denied.add(tx) //multi-level-dependency-loop
                    false
                }
                else -> true
            }
        }.sortedWith(
            compareBy(
                { if(it.blockId<0) Integer.MAX_VALUE else it.blockId }, //block level sort
                { levels[it] },                                         //dependency level sort
                { it.hash }                                             //guarantee deterministic order by hash sort
            )
        )
        , denied
    )
}



fun fillLevelsFor(txs: Iterable<Transaction>): Map<Transaction, Int> {
    val mapper = HashMap<TransactionHash, Transaction>()
    for(tx in txs)
        mapper[tx.hash] = tx
    val levels = HashMap<Transaction, Int>()
    fillLevels(mapper.asTxResolver(), levels, txs)
    return levels
}

private fun fillLevels(transactionResolver: TransactionResolver, txs: Iterable<Transaction>):Map<Transaction, Int> {
    val levels = HashMap<Transaction, Int>()
    fillLevels(transactionResolver, levels, txs)
    return levels
}
private fun fillLevels(transactionResolver: TransactionResolver, levels:HashMap<Transaction, Int>, txs: Iterable<Transaction>) {
    txs.forEach { updateLevel(transactionResolver, levels, it) }
}

/**
 * Updates the level of the given tx and of all it's available dependencies (queried through TransactionResolver)
 *    Will not run the same tree twice
 *    TransactionResolver is expected to return the same results throughout the run of this algorithm.
 *    Levels is expected to be a thread private map
 * Checks for illegal reflexive or dependency-loop and marks their levels as a negative number.
 * -1 for a reflexive loop, and -2 for a multi-dependency loop
 */
private fun updateLevel(transactionResolver: TransactionResolver, levels:HashMap<Transaction, Int>, tx:Transaction?):Int {
    return when {
        tx == null -> //can occur if a map is used as the tx resolver - for example if a block is supposed to be sorted, without regard for dependencies in blocks before
                      //  then this is assumed as a hard border, because blocks order is regarded as superior to level order
            0
        tx.bDependencies.isEmpty() -> {
            levels[tx] = 1
            1
        }
        levels[tx] != null -> levels[tx]!!
        else -> {
            levels[tx] = -2 //used as a marker to detect multi level dependency loops, so if in the recursion this has no been overridden by 'levels[tx] = maxLevel +1', then 'if(levels[tx]!=null)' above happened with the same tx
            var maxLevel = 0
            for (dep in tx.bDependencies) {
                if (dep.txp == tx.hash) {
                    levels[tx] = -1
                    return -1 //reflexive(or hash collision) dependency detected, this tx will no longer be considered and marked as illegal for any caller
                }

                val resolvedDepTx = transactionResolver.getUnsure(dep.txp)

                val depLevel = levels[resolvedDepTx] ?: updateLevel(transactionResolver, levels, resolvedDepTx)

                if(depLevel < 0) {
                    return -2 //multi-level dependency loop detected, this tx will no longer be considered and marked as illegal for any caller
                }

                if (depLevel > maxLevel) {
                    maxLevel = depLevel
                }
            }
            levels[tx] = maxLevel + 1
            maxLevel + 1
        }
    }
}












//NO LONGER USED:

//TOO SLOW
//fun depLevelSortWithinBlockBoundariesOld(chain: Chain?, toSort: Iterable<Transaction>) :  Pair<List<Transaction>, List<Transaction>> {
//    //this costly algorithm is required on partial squash - because otherwise:
//    //    with partial it is possible that a tx1 is in block1, but is only depended on by a single tx2
//    //      then a tx3 may be in block10, but be depended on by tx4 and tx2 (tx2 -> tx4 -> tx3)
//    //      tx1 is technically before tx3, but tx3 has a higher level and is therefore before tx1
//    //   ::  IS THIS AN ISSUE THOUGH??
//    //           tx1 and tx3 have no relation - except over their very parent
//    //   This algorithm is NOT required for squash with all tx
//
//    val toSortMap = toSort.associateBy({it.hash}, {it})
//    // the following block sort algorithm is likely too slow in a real world application
//    //    even without loading each block fully
//    //    even with only sorting dependencies locally
//    val virtualBlocks = Array(chain!!.blockCount() + 1) {i ->
//        if(i == chain.blockCount()) {
//            chain.getMemPoolContent().filter { toSort.contains(it) }
//        } else
//            chain.getBlock(i).filter { toSortMap.contains(it) }.map { chain[it] }
//    }
//    val deniedList = ArrayList<Transaction>()
//    val sortedList = ArrayList<Transaction>(toSort.count())
//    for(vB in virtualBlocks) {
//        val (localSort, localDenied) = dependencyLevelSortButAlsoEliminateMultiLevelDependencies(vB)
//        sortedList.addAll(localSort)
//        deniedList.addAll(localDenied)
//    }
//    return Pair(sortedList, deniedList)
//}



//MISSES INVISIBLE HASH RELATIONSHIPS
//private fun completeDependencyList(chain: Chain, reverseDependencies: ReverseDependencyStore?, subset: Array<Transaction>): Array<Transaction> {
//    val foundElements = HashSet<Transaction>()
//
//    for(tx in subset) {
//        val queue = LinkedList<Transaction>()
//        queue.add(tx)
//
//        while(queue.isNotEmpty()) {
//            val current = queue.poll()
//            if(!foundElements.contains(current)) {
//
//                for (dep in current.bDependencies) {
//                    //no need to subset, it does not require recursive finding - since it has already been added above (addAll)
//                    val foundTX = chain.getUnsure(dep.txp)
//                    if (foundTX != null) {
//                        queue.add(foundTX)
//                        if(reverseDependencies!=null) {
//                            reverseDependencies[foundTX.hash].orEmpty().forEach {
////                            val foundTX = chain.getUnsure(it)
////                            if(foundTX != null) queue.add(foundTX) with properly cleaned up reverse dependencies this will always work correctly - and there cannot be missing dependencies here
//                                queue.add(chain[it])
//                            }
//                        }
//                    }
//                }
//
//                foundElements.add(current)
//            }
//        }
//    }
//
//    return foundElements.toTypedArray()
//}