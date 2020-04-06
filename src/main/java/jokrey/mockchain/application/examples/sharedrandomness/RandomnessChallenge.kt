package jokrey.mockchain.application.examples.sharedrandomness

class RandomnessChallenge(val resultRandomnessLength: Int, val pubKs: Collection<ByteArray>) {
    val size: Int
        get() = pubKs.size
    operator fun contains(toFind: ByteArray) : Boolean {
        for(pubK in pubKs)
            if(pubK.contentEquals(toFind))
                return true
        return false
    }
    fun iteratorWithout(except: ByteArray): Iterator<ByteArray> = pubKs.filterNot { it.contentEquals(except) }.iterator()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as RandomnessChallenge
        if (hashCode() != other.hashCode()) return false
        for(pubK in pubKs)
            if(pubK !in other)
                return false
        return resultRandomnessLength == other.resultRandomnessLength
    }

//    FNV hash
    override fun hashCode() : Int {
        var h = 2166136261.toInt()
        for(pubK in pubKs)
            h *= (16777619 xor pubK.contentHashCode())
        h += resultRandomnessLength * 13
        return h
    }

    override fun toString(): String {
        return "RandomnessChallenge(resultRandomnessLength=$resultRandomnessLength, pubKs=${pubKs.joinToString { it.copyOfRange(50, 53).contentToString() }})"
    }
}