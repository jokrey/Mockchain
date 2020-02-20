package jokrey.mockchain.storage_classes

/**
 * Generic class representing the proof of a block in the form of a byte array
 *
 * IMMUTABLE -> Thread Safe
 */
open class Proof(from: ByteArray) : ImmutableByteArray(from)