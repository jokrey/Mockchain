package jokrey.mockchain.storage_classes

import java.security.MessageDigest
import java.util.*

/**
 * Generic class representing the hash of a byte array
 *
 * By default the sha-256 hashing algorithm is used.
 *
 * Immutable
 */
open class Hash : ImmutableByteArray {
    /**
     * Creates a new hash from the given byte array.
     * If the byte array is marked as 'raw' it is not hashed, but instead directly used as the hash itself.
     */
    constructor(from: ByteArray, isRaw: Boolean = false) :
            super(if(isRaw) from else getHash(from)) {
        if(raw.size != length())
            throw IllegalArgumentException("hash does not have ${length()} bytes")
    }
    /**
     * Creates a new hash from all bytes in the given byte arrays by concatenating them
     */
    constructor(vararg froms: ByteArray) : this(froms.reduce { sum, element -> sum + element })
    /**
     * Creates a new hash from all bytes in the given hashes by concatenating their raw hash arrays
     */
    constructor(vararg froms: ImmutableByteArray) : this(froms.map { it.raw }.reduce { sum, element -> sum + element })

    /**
     * Clones the array to retain immutability.
     */
    fun getHash(): ByteArray {
        return super.getRaw()
    }

    companion object {
        fun length() : Int = 32
    }
}

//keeping an instance of sha is not possible for reasons of thread safety
fun getHash(toHash: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256")!!.digest(toHash)

fun rawHash(hash: ByteArray) = Hash(hash, true)