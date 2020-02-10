package jokrey.mockchain.storage_classes

import java.util.*


/**
 * Generic class representing the proof of a block in the form of a byte array
 *
 * Immutable
 */
open class Proof(from: ByteArray) : Comparable<Proof> {
    private val proof: ByteArray = from

    /**
     * Clones the array to retain immutability.
     */
    fun getRaw(): ByteArray {
        return proof.clone()
    }


    /**
     * Creates a java hash code over the internal hash array
     */
    override fun hashCode(): Int {
        return proof.contentHashCode()
    }
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Proof) return false
        return proof.contentEquals(other.proof)
    }
    override fun compareTo(other: Proof): Int {
        for(i in 0..proof.size) {
            val res = proof[i].compareTo(other.proof[i])
            if(res != 0) return res
        }
        return 0
    }
    override fun toString(): String {
        return proof.toString()
    }
}