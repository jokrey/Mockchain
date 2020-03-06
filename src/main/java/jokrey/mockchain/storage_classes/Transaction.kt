package jokrey.mockchain.storage_classes

import jokrey.utilities.encoder.as_union.li.bytes.LIbae
import jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.type.transformer.LITypeToBytesTransformer

/**
 * The smallest part of a block chain. Transactions are in their core byte arrays that can be committed to the chain.
 *
 * For the purposes of the squash concept dependencies where added.
 *
 * Additionally a transaction has received a blockId field that is automatically filled when the transaction is added to the chain and cannot be mutated after the fact.
 *
 * Consider Immutable. Do not mutate the content array and it is.
 */
class Transaction {
    private constructor(content: ByteArray, blockId:Int, bDependencies:List<Dependency>) {
        this.content = content.clone()
        this.blockId = blockId
        this.bDependencies = bDependencies
        hash = TransactionHash(this)
    }
    private constructor(content: ByteArray, blockId: Int, bDependencies: List<Dependency>, hash:TransactionHash) {
        this.content=content;this.blockId=blockId;this.bDependencies=bDependencies;this.hash=hash
    }
    private constructor(content: ByteArray, blockId:Int, vararg bDependencies:Dependency) : this(content, blockId, bDependencies.toList())

    /**
     * Creates a fresh transaction with a given content and a list of dependencies
     * The hash is automatically generated.
     */
    constructor(content: ByteArray, bDependencies:List<Dependency>) : this(content, -1, bDependencies)
    /**
     * Creates a fresh transaction with a given content and a list of dependencies
     * The hash is automatically generated.
     */
    constructor(content: ByteArray, vararg bDependencies:Dependency) : this(content, -1, bDependencies.toList())


    /**
     * Returns the raw content of the transaction. The result array should not be mutated
     */
    val content: ByteArray
//        get() = content.clone() //likely too slow, but would technically be required for immutability

    /**
     * The blockId in which the transaction is stored. If the transaction is yet to be stored in a block the blockId will be -1 or less.
     */
    val blockId: Int

    /**
     * The backward dependencies of the transaction to be verified and squashed by the squash algorithm.
     */
    val bDependencies:List<Dependency>

    /**
     * The calculated hash of the transaction, by which the transaction can be referenced
     */
    val hash: TransactionHash


    /**
     * Copies the transaction only mutating the blockId. If the blockId is already correct this transaction is returned(no copy occurs)
     * Used by the chain when persisting the transaction, should not be used anywhere else.
     */
    fun withBlockId(blockId: Int): Transaction {
        if(this.blockId == blockId) return this
//        if(this.blockId >= 0) throw IllegalStateException("attempting to mutate valid block id(${this.blockId} is valid)")
        return Transaction(content, blockId, bDependencies, hash)
    }
    /**
     * Copies the transaction removing all dependencies and mutating the content.
     * Used by the chains reintroduction functions, should not be used anywhere else
     */
    fun changeContentAndRemoveDependencies(newContent: ByteArray) = Transaction(newContent, blockId)
    /**
     * Copies the transaction mutating only the dependencies
     * Used by the chains reintroduction functions, should not be used anywhere else
     */
    fun changeDependencies(newDependencies: Array<Dependency>) = Transaction(content, blockId, newDependencies.toList(), hash)


    override fun toString(): String {
        return "[Transaction: hash: $hash, content: ${content.toList()} depOn("+bDependencies.size+"): "+bDependencies+"]"
    }
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Transaction
        return content.contentEquals(other.content) && bDependencies == other.bDependencies
    }
    override fun hashCode() = hash.hashCode()



    companion object {
        /**
         * Creates a Transaction from the raw contents of the given byte array
         * The byte array should have been previously created using the {@link encode} method.
         */
        fun decode(raw: ByteArray, invalidateBlockId: Boolean = false) : Transaction {
            val transformer = LITypeToBytesTransformer()
            val decoder = LIbae(raw).iterator()
            val content = decoder.next()
            val blockId = transformer.detransform_int(decoder.next())
            val bDependencies = ArrayList<Dependency>()
            while(decoder.hasNext()) {
                val hash = Hash(decoder.next(), true)
                val ordinal = transformer.detransform_int(decoder.next())
                val type = DependencyType.values()[ordinal]
                bDependencies.add(Dependency(TransactionHash(hash), type))
            }
            return Transaction(content, if(invalidateBlockId) -1 else blockId, bDependencies)
        }
    }
    /**
     * Encodes a transaction so that it can be persisted into memory.
     * Can be decoded using the decode function.
     */
    fun encode() : ByteArray {
        val transformer = LITypeToBytesTransformer()
        val encoder = LIbae(content.size + bDependencies.size*32*2 + 100 + 4)
        encoder.encode(content)
        encoder.encode(transformer.transform(blockId))
        for(dep in bDependencies)
            encoder.encode(dep.txp.getHash()).encode(transformer.transform(dep.type.ordinal))
        return encoder.encodedBytes
    }
}

/**
 * Able to generate the appropriate hash from a given transaction.
 *
 * IMMUTABLE
 */
class TransactionHash : Hash {
    constructor(from: ByteArray, isRaw: Boolean = false) : super(from, isRaw)
    constructor(from_tx: Transaction) : this(from_tx.content, false)
    constructor(raw: Hash) : this(raw.getHash(), true)
}