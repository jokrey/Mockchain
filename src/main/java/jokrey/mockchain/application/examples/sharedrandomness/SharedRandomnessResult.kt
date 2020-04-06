package jokrey.mockchain.application.examples.sharedrandomness

import jokrey.utilities.Dice
import jokrey.utilities.DiceConfiguration

data class SharedRandomnessResult(val by: List<Pair<String, ByteArray>>, val bytes: ByteArray) {
    val size: Int
        get() = bytes.size
    operator fun get(index: Int) = bytes[index]

    fun asDiceResult(config: DiceConfiguration) = config.getRandomResults(bytes)
    fun asDiceResult(dice: Dice) = asDiceResult(DiceConfiguration(dice))


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SharedRandomnessResult
        if (by != other.by) return false
        if (!bytes.contentEquals(other.bytes)) return false
        return true
    }
    override fun hashCode() = 31 * by.hashCode() + bytes.contentHashCode()
}