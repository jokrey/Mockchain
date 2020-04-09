package jokrey.mockchain.storage_classes

/**
 * A no longer mutable byte array. For example hash or proof extend from this class.
 * @author jokrey
 */
open class ImmutableByteArray(internal val raw: ByteArray) : Comparable<ImmutableByteArray> {
    val size: Int = raw.size

    /**
     * Clones the array to retain immutability.
     */
    fun getRaw(): ByteArray {
        return raw.clone()
    }


    /**
     * Creates a java hash code over the internal hash array
     */
    override fun hashCode(): Int {
        return raw.contentHashCode()
    }
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ImmutableByteArray) return false
        return raw.contentEquals(other.raw)
    }
    override fun compareTo(other: ImmutableByteArray): Int {
        for(i in 0..raw.size) {
            val res = raw[i].compareTo(other.raw[i])
            if(res != 0) return res
        }
        return 0
    }
    override fun toString(): String {
        if(raw.size <= 6)
            return raw.toList().toString()

        val builder = StringBuilder(100)
        builder.append("[")
        for(i in 0..1)
            builder.append(raw[i]).append(",")
        builder.append("...")
        for(i in 2 downTo 1)
            builder.append(",").append(raw[raw.size-i])
        builder.append("]")
        return builder.toString()
    }
    operator fun get(i: Int) = raw[i]
}