package jokrey.mockchain.storage_classes

/**
 * Generic class representing the proof of a block in the form of a byte array
 *
 * Immutable
 */
open class Proof(from: ByteArray) : ImmutableByteArray(from)