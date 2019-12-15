package storage_classes

import java.security.MessageDigest
import java.util.*

/**
 * Generic class representing the hash of a byte array
 *
 * By default the sha-256 hashing algorithm is used.
 *
 * Immutable
 */
open class Hash : Comparable<Hash> {
    private val hash: ByteArray
    /**
     * Creates a new hash from the given byte array.
     * If the byte array is marked as 'raw' it is not hashed, but instead directly used as the hash itself.
     */
    constructor(from: ByteArray, isRaw: Boolean = false) {
        hash = if(isRaw) from else getHash(from)
        if(hash.size != hashLength())
            throw IllegalArgumentException("hash does not have ${hashLength()} bytes")
    }
    /**
     * Creates a new hash from all bytes in the given byte arrays by concatenating them
     */
    constructor(vararg froms: ByteArray) : this(froms.reduce { sum, element -> sum + element })
    /**
     * Creates a new hash from all bytes in the given hashes by concatenating their raw hash arrays
     */
    constructor(vararg froms: Hash) : this(froms.map { it.hash }.reduce { sum, element -> sum + element })

    /**
     * Clones the array to retain immutability.
     */
    fun getHash(): ByteArray {
        return hash.clone()
    }


    /**
     * Creates a java hash code over the internal hash array
     */
    override fun hashCode(): Int {
        return Arrays.hashCode(hash)
    }
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Hash) return false
        return hash.contentEquals(other.hash)
    }
    override fun compareTo(other: Hash): Int {
        for(i in 0..hash.size) {
            val res = hash[i].compareTo(other.hash[i])
            if(res != 0) return res
        }
        return 0
    }
    override fun toString(): String {
        return hash.drop(28).toList().toString()
    }
}
fun hashLength() : Int = 32

private val SHA256_INSTANCE =  MessageDigest.getInstance("SHA-256")!!
fun getHash(toHash: ByteArray) : ByteArray = SHA256_INSTANCE.digest(toHash)

fun rawHash(hash: ByteArray) = Hash(hash, true)