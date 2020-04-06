package jokrey.mockchain.application.examples.sharedrandomness

import jokrey.mockchain.storage_classes.Transaction
import jokrey.utilities.bitsandbytes.BitHelper
import jokrey.utilities.encoder.as_union.li.bytes.LIbae
import jokrey.utilities.misc.RSAAuthHelper
import jokrey.utilities.transparent_storage.bytes.non_persistent.ByteArrayStorage
import kotlin.math.min

class ChallengeContribution(val contributorPubK: ByteArray, val contributorName:String, val challenge: RandomnessChallenge, val contribution: ByteArray) {
    fun verify(signature: ByteArray) = RSAAuthHelper.verify(toBytesWithoutSignature(), signature, contributorPubK)
    fun toBytesWithSignature(privateKey: ByteArray) : ByteArray {
        val bytesWithoutSignature = toBytesWithoutSignature()
        return bytesWithoutSignature + BitHelper.getBytes(challenge.resultRandomnessLength) + RSAAuthHelper.sign(bytesWithoutSignature, privateKey)
    }
    fun toTx(privateKey: ByteArray) = Transaction(toBytesWithSignature(privateKey))
    private fun toBytesWithoutSignature() : ByteArray {
        val encoder = LIbae()
        encoder.encode(contributorName.toByteArray(Charsets.UTF_8))
        encoder.encode(contribution)
        encoder.encode(contributorPubK)
        for(pubK in challenge.iteratorWithout(contributorPubK))
            encoder.encode(pubK)
        return encoder.encodedBytes
    }

    fun shortDescriptor() = "(by=$contributorName-${contributorPubK.copyOfRange(50, 53).contentToString()}, all=$challenge)=${contribution.copyOfRange(0, min(contribution.size, 3)).contentToString()})"
    fun longDescriptor() = "Contribution${shortDescriptor()}"

    companion object {
        fun fromTx(tx: Transaction) = fromBytes(tx.content)
        fun fromBytes(bytes: ByteArray): Pair<ChallengeContribution, ByteArray> {
            val signature = bytes.copyOfRange(bytes.size - RSAAuthHelper.signatureLength(), bytes.size)
            val resultRandomnessLength = BitHelper.getInt32From(bytes, bytes.size - RSAAuthHelper.signatureLength() - 4)
            val decoder = LIbae(ByteArrayStorage(true, bytes, bytes.size - RSAAuthHelper.signatureLength() - 4)).iterator()
            val contributorName = decoder.next().toString(Charsets.UTF_8)
            val contribution = decoder.next()
            val contributorPubK = decoder.next()
            val pubKs = ArrayList<ByteArray>()
            pubKs.add(contributorPubK)
            for(pubK in decoder)
                pubKs.add(pubK)

            return Pair(ChallengeContribution(contributorPubK, contributorName, RandomnessChallenge(resultRandomnessLength, pubKs), contribution), signature)
        }
    }


    //two contributions are equal if they belong to the same challenge and are by the same contributor.
    //   the contribution itself does not matter.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ChallengeContribution

        if (!contributorPubK.contentEquals(other.contributorPubK)) return false
        if (challenge != other.challenge) return false

        return true
    }
    override fun hashCode(): Int {
        var result = contributorPubK.contentHashCode()
        result = 31 * result + challenge.hashCode()
        return result
    }

    override fun toString() = longDescriptor()
}