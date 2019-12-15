package storage_classes

import jokrey.utilities.encoder.as_union.li.bytes.LIbae
import java.util.*

/**
 * A block is the container for transactions in the context of blockchain.
 * Since a block always contains the hash for its previous block the order on block is strictly defined and a number of block build a hashchain.
 *
 * IMMUTABLE
 */
open class Block: Iterable<TransactionHash> {
    /**
     * Creates a new block from the previous hash and a number of transaction hashes contained in the new block.
     *
     * It also builds the Merkle tree of the transaction hashes.
     */
    constructor(previousBlockHash: Hash?, txs: Array<TransactionHash>) {
        this.previousBlockHash = previousBlockHash
        this.txs = txs.clone()
        this.merkleRoot = rebuildMerkleRoot()
    }
    constructor(previousBlockHash: Hash?, txs: Collection<TransactionHash>) : this(previousBlockHash, txs.toTypedArray())

    /**
     * Internal use only.
     */
    private constructor(previousBlockHash: Hash?, txs: Array<TransactionHash>, merkleRoot: Hash) {
        this.previousBlockHash = previousBlockHash
        this.txs = txs
        this.merkleRoot = merkleRoot
    }

    /**
     * Creates a Block instance from its given, serialized form that was previous created using {@link encode}.
     * It will create a Block with the exact same contents that the block had before it was encoded.
     */
    constructor(encoded: ByteArray) {
        val decoder = LIbae(encoded).iterator()
        val previousBlockHashRaw = decoder.next()
        previousBlockHash = if(previousBlockHashRaw.size==32) Hash(previousBlockHashRaw, true) else null
        merkleRoot = Hash(decoder.next(), true)
        txs = if(decoder.hasNext())
            decoder.next(-1).map { TransactionHash(Hash(it, true)) }.toTypedArray()
        else
            emptyArray()
    }
    /**
     * Encodes the current blocks contents to be persisted by the storage model.
     * The encoding can be reveres using the appropiate constructor.
     */
    fun encode() : ByteArray {
        val encoder = LIbae()
        encoder.encode(previousBlockHash?.getHash() ?: byteArrayOf(1), merkleRoot.getHash(), *txs.map { it.getHash() }.toTypedArray())
        return encoder.encodedBytes
    }


    /**
     * Hash of the previous block or null if this block is the first.
     */
    val previousBlockHash: Hash?
    /**
     * Merkle root of the transactions
     */
    val merkleRoot: Hash

    /**
     * Generates and returns the block hash, i.e. the hash of the header of this block.
     * This hash should be used as the previousBlockHash when creating the next block.
     */
    open fun getHeaderHash() = if(previousBlockHash==null) merkleRoot else Hash(previousBlockHash, merkleRoot) //question: cache this value??


    private val txs:Array<TransactionHash>

    /**
     * Returns the number of transactions in the block
     */
    val size: Int
        get() = txs.size
    /**
     * Returns whether there are any transactions in the block
     */
    val isEmpty: Boolean
        get() = size == 0
    /**
     * Returns the transaction hash at the given index or throws an IndexOutOfBoundariesException
     */
    operator fun get(index: Int): TransactionHash = txs[index]
    /**
     * Provides an iterator over the transaction hashes in this block
     */
    override fun iterator(): Iterator<TransactionHash> = txs.iterator()



    //Immutability retaining change functionality
    /**
     *
     */
    fun rebuildWithMutated(previousBlockHash: Hash?, oldHash: TransactionHash, newHash: TransactionHash) : Block {
        val cloned = txs.clone()
        val i = cloned.indexOf(oldHash)
        cloned[i] = newHash
        return Block(previousBlockHash, cloned)
    }
    fun rebuildWithout(previousBlockHash: Hash?, oldHash: TransactionHash) = Block(previousBlockHash, txs.toList().filter { it != oldHash }.toTypedArray())
    fun rebuildWithDeletionsAndChanges(previousBlockHash: Hash?, changes: LinkedList<Pair<TransactionHash, TransactionHash>>, deletions: LinkedList<TransactionHash>): Block {
        val cloned = txs.toList().filter { !deletions.contains(it) }.toTypedArray()
        for((before, after) in changes)
            cloned[cloned.indexOf(before)] = after
        return Block(previousBlockHash, cloned)
    }
    fun changePreviousHash(newPreviousHash: Hash?): Block {
        return Block(newPreviousHash, txs, merkleRoot) // does without cloning txs or recalculating merkle root - as fast as can be
    }


    //validation
    /**
     * Builds a Merkle Tree from the transactions in this block
     */
    fun buildMerkleTree() = MerkleTree(*txs)
    fun rebuildMerkleRoot() = if(txs.isEmpty()) Hash(byteArrayOf(1)) else buildMerkleTree().getRoot()
    /**
     * Validates that the given 'previous' block is the previous block of this block, by comparing previousBlockHash with the header hash of the given block
     */
    fun validatePrevious(previous: Block) = previous.getHeaderHash() == previousBlockHash
    /**
     * Returns whether this block has a previous block
     */
    fun isBlock0() = previousBlockHash == null


    //equals, hashCode, toString
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Block
        return txs.contentEquals(other.txs) && previousBlockHash == other.previousBlockHash && merkleRoot == other.merkleRoot
    }
    override fun hashCode(): Int = txs.contentHashCode()
    override fun toString(): String = "[Block(previous=$previousBlockHash, merkle=$merkleRoot) : ${Arrays.toString(txs)}"

}