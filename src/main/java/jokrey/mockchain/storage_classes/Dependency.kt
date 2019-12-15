package jokrey.mockchain.storage_classes

/**
 * Tuple of a hash and a dependency type
 */
data class Dependency(val txp:TransactionHash, val type:DependencyType) {
    constructor(tx: Transaction, type: DependencyType) : this(tx.hash, type)
}

/**
 * Shortcut to create a number of dependencies of the same type to a number of different transactions
 */
fun dependenciesFrom(type:DependencyType, vararg txs:Transaction) : Array<Dependency> = Array(txs.size) {Dependency(txs[it].hash, type)}
/**
 * Shortcut to create a number of dependencies of the same type to a number of different transactions
 */
fun dependenciesFrom(type:DependencyType, vararg txps:TransactionHash) : Array<Dependency> = Array(txps.size) {Dependency(txps[it], type)}

/**
 * Shortcut to create a number of dependencies to the same transaction with a number of different types
 */
fun dependenciesFrom(tx:Transaction, vararg types:DependencyType) : Array<Dependency> = Array(types.size) {Dependency(tx.hash, types[it])}
/**
 * Shortcut to create a number of dependencies to the same transaction with a number of different types
 */
fun dependenciesFrom(txp:TransactionHash, vararg types:DependencyType) : Array<Dependency> = Array(types.size) {Dependency(txp, types[it])}

fun Iterable<Dependency>.replace(dependency: Dependency, txp: TransactionHash): Array<Dependency> =
        map { if(it == dependency) Dependency(txp, dependency.type) else it }.toTypedArray()
fun Iterable<Dependency>.find(vararg typesAllowed: DependencyType): Dependency =
        find {dep -> typesAllowed.any { it == dep.type } }!!
fun Iterable<Dependency>.filter(vararg typesAllowed: DependencyType): List<Dependency> =
        filter {dep -> typesAllowed.any { it == dep.type } }

/**
 * Enumerable class for the six different dependency types whose behavior is enforced in the squash algorithm
 *
 * Some details can be found in the comments below, most should be read upon in the thesis corresponding to this framework
 */
enum class DependencyType {
    REPLACES,         //i.e. the new transaction makes the data in the old transaction obsolete
                      //       - it may retain that data and add upon it
                      //       - it may delete parts or all of that data - (adding new, different data)
                      //     NO CALLBACK
                      //  1 to 1, n-level: delete older transaction (can be done dependenciesFrom either direction)
                      //  n to 1, n-level(1 transaction replaces many): iterative replace ALL older transactions
                      //  1 to n, 1-level(many transactions replace 1): PROHIBITED - makes no sense
    REPLACES_PARTIAL, //i.e. the new transaction makes part of the data in the old transaction obsolete
                      //       the old transaction has to be partially removed(callback to application for new bytes)
                      //           and 're-added' in place((SAME POINTER)) [if other transactions have a dependency on the rest of the data]
                      //           problem: if the transaction has a dependency in which the removed data replaces other data
                      //              solve: who cares. the dependency remains and can be altered without knowing any of the (now removed) replacing data.
                      //     CALLBACK TO ALTER OLDER TRANSACTION - NEWER TRANSACTION IS NOT ALTERED
                      //  1 to 1, n-level: alter older transaction - write change back (can that be done dependenciesFrom either way???)
                      //  n to 1, n-level(1 transaction partially replaces many): iterative replace ALL older transactions
                      //  1 to n, n-level(many transactions partially replace 1): allowed, but it is checked that the partial transactions really replace something and something different
                      //  DIAMOND:
    BUILDS_UPON,       //i.e the new transaction builds upon the old transaction, but does not replace or invalidate any of its data;;;
    //       This also entails that the most recent transaction does not necessarily contain any of the data of transactions it builds upon
                      //       the only thing it really says is that the chain of transactions building on each other can be squashed
                      //       the main difference to Partial replace
                      //     CALLBACK TO ALTER NEWER TRANSACTION - NOT OLDER
                      //          [Older transaction is not altered, but since that is often desired it is perfectly legal to add a second replace or replace partial dependency on the same edge]
                      //          Build-On callback is called before replace-partial, but in the same cycle - therefore build-upon will see content altered by replace-partial
                      //              this entails that a simulated partial-build-on edge requires a strike simulation within the contents of the transaction [difference to 'real' strikes:: hash changes]
                      //                  because otherwise the old transaction will never be replaced or the content changes and the algorithm will only see the altered tx
                      //
                      //              pro: it's easier and closer to complete replace handling [where this 'after' is strictly required to make any sense - cannot build on nothing]
                      //              con: has more of an incremental feel - if the changes occur in different squash cycles the theoretically same situation will result in different calls to the callbacks
                      //              BEST REASON:
                                        //    //problem: 0,1 has been replaced correctly on newBlock by a 0, here however partial replace does that - and partial replace has not yet been called
                                        //    // solution call partial replace directly after
                      //
                      //  1 to 1, n-level: t1, t2, t3 -> (t1, t2) => t12. (t12, t3) => t123.
                      //        (the algorithm will call a handler in pairs dependenciesFrom least recent to newest,
                      //           the latest created transaction (t123 in this case) will be added to the chain, all old ones will be deleted)
                      //  n to 1(a single transaction builds upon many):
                      //       Problem: the naive algorithm would create n transactions (n1+1, n2+1, etc.)
                      //       Solve:
                      //  1 to n(many transactions build upon a single one):
                      //       ?: for every n, create a (1+n1, 1+n2, etc.) transaction
                      //  DIAMOND:
    ONE_OFF_MUTATION,      //i.e. the newer transaction will be removed once the change on the previous transaction was executed.
                      //   this way replace-partial can change a previous transaction and remove this transaction once the change is executed.
                      //       this is required for example in the currency application
                      //       a transaction with only replaced-by dependencies is legal, but makes no sense since it just removes itself - nothing else
                      //   1 to 1, 1 to n and n to 1 are all legal
                      //      however only at the first level
                      //   a transaction with even a single replaced-by dependency cannot be depended upon, because semantically it is already deleted at that point
    SEQUENCE_PART,    // Special case of build-upon + replace + await-squash
    SEQUENCE_END,     //     Sequence will be fully and only squashed upon seeing a sequence end
                      //     Sequences can be chained (i.e. a sequence can depend on a previous sequence
                      //     Sequences can only be 1 to 1 - a tx cannot depend on multiple sequences - n to 1 prohibited
                      //                                    multiple tx cannot be depended on by a single tx - 1 to n prohibited
                      //SEQUENCE INTERACTION WITH OTHERS:
                      //    Generally speaking it is perfectly legal, but it does become a problem(and therefore illegal) at a point:
                      //    A tx that is part of a sequence cannot have it's hash altered - because otherwise a sequence might become invalid after the fact
}