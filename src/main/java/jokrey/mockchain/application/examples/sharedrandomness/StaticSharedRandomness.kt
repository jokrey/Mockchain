package jokrey.mockchain.application.examples.sharedrandomness

import jokrey.mockchain.Mockchain
import jokrey.mockchain.Nockchain
import jokrey.mockchain.consensus.ConsensusAlgorithmCreator
import jokrey.mockchain.consensus.ProofOfStaticStake
import jokrey.mockchain.consensus.TriggeredProofOfStaticStake
import jokrey.mockchain.consensus.TriggeredProofOfStaticStakeConsensusCreator
import jokrey.mockchain.storage_classes.*
import jokrey.utilities.contains
import jokrey.utilities.network.link2peer.P2LNode
import jokrey.utilities.network.link2peer.P2Link
import java.lang.UnsupportedOperationException
import java.security.KeyPair

/**
 *  Required to use get instance methods to create the actual chain instance
 *     We need access to the consensus and we need it to be something specific.
 *
 * @author jokrey
 */
class StaticSharedRandomness(val ownName: String, val ownKeyPair: KeyPair, val participants: List<StaticSharedRandomnessParticipant>, maxContributionLength: Int = 128, maxResultRandomnessLength: Int = 128) : SharedRandomness(participants.size, maxContributionLength, maxResultRandomnessLength, false) {
    fun getConsensusCreator() = TriggeredProofOfStaticStakeConsensusCreator(participants.sortedBy { it.name }.map { it.publicKey }, ownKeyPair)

    fun getInstance(store: StorageModel = NonPersistentStorage()): Mockchain {
        val instance = Mockchain(this, store, getConsensusCreator())
        consensus = instance.consensus as TriggeredProofOfStaticStake
        return instance
    }
    fun getInstance(p2lNode: P2LNode, store: StorageModel = NonPersistentStorage()) :Nockchain {
        val instance = Nockchain(this, p2lNode, store, getConsensusCreator())
        consensus = instance.consensus as TriggeredProofOfStaticStake
        return instance
    }
    fun getInstance(selfLink: P2Link, store: StorageModel = NonPersistentStorage()) :Nockchain {
        val instance = Nockchain(this, selfLink, store, getConsensusCreator())
        consensus = instance.consensus as TriggeredProofOfStaticStake
        return instance
    }

    private var consensus : TriggeredProofOfStaticStake? = null
    init {
        if(! participants.any { it.publicKey.contentEquals(ownKeyPair.public.encoded) }) throw IllegalArgumentException("own key pair public key is not included in given list of participants")

        addRawChallengeCompletedInMemPoolListener { _, _ ->
            println("test")
            consensus?.proposeBlockIfMyTurn()
        }
    }

    override fun preMemPoolVerify(instance: Mockchain, tx: Transaction): RejectionReason.APP_VERIFY? {
        val rejectionReason = super.preMemPoolVerify(instance, tx)
        if(rejectionReason==null) {
            val (newContribution, signature) = ChallengeContribution.fromTx(tx)
            val challenge = newContribution.challenge
            if(!participants.matchesAll(challenge.pubKs))
                return RejectionReason.APP_VERIFY("not all ${participants.size} participants included in challenge")
        }
        return rejectionReason
    }

    fun startChallenge(instance: Mockchain, contribution: ByteArray, resultLength: Int = maxResultRandomnessLength) =
        super.startChallenge(instance, RandomnessChallenge(resultLength, participants.map { it.publicKey }), ownKeyPair, ownName, contribution)
    fun registerAutoAnswerContribution(instance: Mockchain, contributionCreator: () -> ByteArray = this::generateContribution) {
        addNewChallengeForMeListener(ownKeyPair.public.encoded) { newChallenge, _ ->
            contributeToChallenge(instance, newChallenge, ownKeyPair, ownName, contributionCreator())
        }
    }
}

private fun Collection<StaticSharedRandomnessParticipant>.matchesAll(pubKs: Collection<ByteArray>) =
    this.size == pubKs.size && this.containsAll(pubKs) { v1, v2 -> v1.publicKey.contentEquals(v2) }

fun <E, T> Collection<E>.containsAll(elements: Collection<T>, matcher: (E, T) -> Boolean) = elements.all { this.contains(it, matcher) }

class StaticSharedRandomnessParticipant(val name: String, val publicKey: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as StaticSharedRandomnessParticipant
        return name == other.name && publicKey.contentEquals(other.publicKey)
    }
    override fun hashCode() = 31 * name.hashCode() + publicKey.contentHashCode()
}