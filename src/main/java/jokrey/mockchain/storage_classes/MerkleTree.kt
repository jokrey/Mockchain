package jokrey.mockchain.storage_classes

import java.util.*
import kotlin.math.ceil
import kotlin.math.log2

/**
 * Implementation of a Merkle tree.
 *
 * Allows generation, partial verification and extraction of the merkle root. The later is the only part strictly required for basic blockchain behavior.
 *
 * IMMUTABLE -> Thread Safe
 */

private val DEFAULT_ROOT_FOR_EMPTY_TREE: Hash = Hash(byteArrayOf(1))

class MerkleTree {
    constructor(vararg leafs: Hash) {
        this.tree = generateMerkleTree(*leafs).toTypedArray()
        this.numberOfLeaves = leafs.size
    }
    constructor(leafs: List<Hash>) {
        this.tree = generateMerkleTree(*leafs.toTypedArray()).toTypedArray()
        this.numberOfLeaves = leafs.size
    }

    internal val tree: Array<Hash>
    internal val numberOfLeaves: Int

    fun getPartialVerificationTreeFor(leafIndex: Int) = getPartialVerificationTreeFor(this, leafIndex)

    fun getRoot(): Hash = if(tree.isEmpty()) DEFAULT_ROOT_FOR_EMPTY_TREE else tree.last()

    override fun hashCode(): Int {
        return getRoot().hashCode()
    }
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as MerkleTree
        return getRoot() == other.getRoot()
    }
}


private fun generateMerkleTree(vararg leafs: Hash) : List<Hash> {
    val acc = ArrayList<Hash>(ceil(leafs.size * log2(leafs.size.toDouble())).toInt())
    generateMerkleTree(acc, leafs)
    return acc
}
private tailrec fun generateMerkleTree(acc: MutableList<Hash>, current: Array<out Hash>) {
    acc.addAll(current)
    if(current.size > 1) {
        val nextNodes = Array((current.size + 1) / 2) {
            if (it * 2 + 1 < current.size)
                Hash(current[it * 2], current[it * 2 + 1])
            else
                current[it * 2]
        }
        generateMerkleTree(acc, nextNodes)
    }
}



private fun getPartialVerificationTreeFor(merkleTree: MerkleTree, leafIndex: Int) : Array<HashOrder> {
    val tree = merkleTree.tree
    val cap = Math.ceil(log2(tree.size.toDouble())).toInt()

    val result = ArrayList<HashOrder>(cap)

    var ownIndex = leafIndex
    var currentLevelSize = merkleTree.numberOfLeaves
    var currentLevelStartIndex = 0

    while(ownIndex <= tree.lastIndex) {
        val ownIndexRelativeInLevel = (ownIndex - currentLevelStartIndex)
        val nextLevelIndex = currentLevelStartIndex + currentLevelSize

        val adjacentToOwn = when {
            currentLevelSize==1 || (ownIndexRelativeInLevel % 2 == 0 && ownIndex+1 >= nextLevelIndex) -> HashOrder.Lonely(tree[ownIndex])
            ownIndexRelativeInLevel % 2 == 0 -> HashOrder.Left(tree[ownIndex+1])
            else -> HashOrder.Right(tree[ownIndex-1])
        }
        result.add(adjacentToOwn)

        val nextLevelIndexOfOwn = currentLevelSize + ownIndexRelativeInLevel / 2
        val nextLevelSize = (currentLevelSize+1)/2

        ownIndex = currentLevelStartIndex + nextLevelIndexOfOwn
        currentLevelStartIndex = nextLevelIndex
        currentLevelSize = nextLevelSize
    }

    return result.toTypedArray()
}

fun validateWithPartialTree(partialTree: Array<HashOrder>, leaf: Hash) : Boolean {
    var current = leaf
    for(p in partialTree.dropLast(1))
        current = p.hashOnto(current)
    return partialTree.last().hash == current
}

sealed class HashOrder(val hash: Hash) {
    abstract fun hashOnto(onto: Hash) : Hash

    class Left( hash: Hash) : HashOrder(hash) {
        override fun hashOnto(onto: Hash) = Hash(onto, hash)
    }
    class Right( hash: Hash) : HashOrder(hash) {
        override fun hashOnto(onto: Hash) = Hash(hash, onto)
    }
    class Lonely( hash: Hash) : HashOrder(hash) {
        override fun hashOnto(onto: Hash): Hash = onto
    }

    override fun equals(other: Any?) = when (other) {
        this === other -> true
        is Hash -> hash == other
        is HashOrder -> hash == other.hash
        else -> false
    }
    override fun hashCode() = hash.hashCode()
    override fun toString() = "[${javaClass.name} - $hash]"
}

fun partialToBytes(partial: Array<HashOrder>) =
    partial.map {
        it.hash.getHash() + byteArrayOf(when(it) {
            is HashOrder.Lonely -> 0
            is HashOrder.Left -> -1
            is HashOrder.Right -> 1
        })
    }.reduce {sum, element -> sum + element} // very slow, requires a lot of copying, while only one allocation is required

fun bytesToPartial(bytes: ByteArray) : Array<HashOrder> {
    // very slow, because chunked uses lists
    return bytes.toList().chunked(33) { chunk ->
        val hash = rawHash(chunk.toByteArray().copyOf(32))
        when(chunk.last().toInt()) {
            0 -> HashOrder.Lonely(hash)
            -1 -> HashOrder.Left(hash)
            else -> HashOrder.Right(hash)
        }
    }.toTypedArray()
}



//class MerkleTree(vararg leafs: Hash) {
//    private val tree = generateMerkleTree(leafs.map {Node(it, null) }).filterNotNull().toTypedArray()
//
//    private fun<T> traverse(leaf: Hash, actionSelector: (Node) -> Boolean = {true}, action: (Node) -> T) =
//            traverse(tree.find { it.hash == leaf }!!, actionSelector, action)
//
//    private fun<T> traverse(n: Node, actionSelector: (Node) -> Boolean = {true}, action: (Node) -> T) : T? {
//        var cur = n.next
//        while (cur != null) {
//            if(actionSelector(cur))
//                return action(cur)
//            cur = cur.next
//        }
//        return null
//    }
//
//    fun validateIncludedFull(leaf: Hash, leafIndex: Int, merkleRoot:Hash) =
//            leaf == tree[leafIndex].hash && getRoot() == merkleRoot
//
//    fun getPartialVerificationTreeFor(leafIndex: Int) : Array<Hash> {
//        val result = ArrayList<Hash>(log2(tree.size.toDouble()).toInt())
//        var last:Node? = null
//        if(leafIndex % 2 == 0) {
//            last = tree[leafIndex+1]
//            result.add(last.hash)
//        } else {
//            last = tree[leafIndex-1]
//            result.add(last.hash)
//        }
//    }
//
//    fun getRoot(): Hash = traverse(tree[0], {it.isRoot()}, {it.hash})!!
//
//    override fun hashCode(): Int {
//        return getRoot().hashCode()
//    }
//    override fun equals(other: Any?): Boolean {
//        if (this === other) return true
//        if (javaClass != other?.javaClass) return false
//        other as MerkleTree
//        return getRoot() == other.getRoot()
//    }
//
//    //to-do add partial verification methods - i.e. given a merkle tree and a hash(or a tx) and an index verify that the hash is a leaf in the merkle tree
//    //to-do: partial merkle tree??
//    //to-do: serialization methods - to send the merkle tree without the txs
//
//    fun toBytes
//}
//private fun generateMerkleTree(base: List<Node>): List<Node?> {
////    println("generateMerkleTree - base = [${base}]")
//    if(base.size == 1) return base
//
//    val leafs = if(base.size % 2 != 0) { // i.e. odd number of leafs, only called at the base level
//        base + Node(Hash(byteArrayOf(1)), null) // content does not matter, but it cannot be random
//    } else
//        base
//
//    val nextNodes:ArrayList<Node> = ArrayList(leafs.size/2)
//    for(i in 0 until leafs.size step 2) {
//        val leaf1 = leafs[i]
//        val leaf2 = leafs[i+1]
//
//        nextNodes.add(Node(Hash(leaf1.hash.getHash(), leaf2.hash.getHash()), null))
//    }
////    println("generateMerkleTree - nextNodes = [${nextNodes}]")
//    val nextLevelResults =  generateMerkleTree(nextNodes)
////    println("generateMerkleTree - nextLevelResults = [${nextLevelResults}]")
//    for(i in 0 until leafs.size step 2) {
//        leafs[i].next = nextLevelResults[i/2]
//        leafs[i+1].next = nextLevelResults[i/2]
//    }
////    println("generateMerkleTree after - leafs = [${leafs}]")
//    return leafs
//}
//
//private data class Node(val hash: Hash, var next: Node?) {
//    fun isRoot() = next == null
//}