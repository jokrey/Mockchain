package jokrey.mockchain.application.examples.sharedrandomness

import jokrey.mockchain.Mockchain
import jokrey.mockchain.storage_classes.*
import jokrey.mockchain.visualization.VisualizableApp
import jokrey.utilities.SHA1PRNG
import jokrey.utilities.base64Decode
import jokrey.utilities.base64Encode
import jokrey.utilities.decodeAndVerifyKeyPair
import jokrey.utilities.network.link2peer.util.P2LFuture
import jokrey.utilities.transparent_storage.bytes.non_persistent.ByteArrayStorage
import java.security.KeyPair
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.Comparator
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

/**
 * Concept of shared randomness.
 * Idea:
 *      Multiple parties desire to agree on randomness.
 *      Each want to be sure that the final result is actually random and not skewed by a single party.
 *
 * Multiple parties, each identified by a pubK - can send contributions of randomness, thereby creating or contributing to a randomness challenge.
 * A RandomnessChallenge contains:
 *    the desired length of the final randomness
 *    a list of pubKs that need to contribute.  {<= maxNum}
 *      where the first item is always the own, alleged pubK
 * A Contribution includes:
 *    a randomness challenge
 *    a string of randomness - n random bytes
 *    the alleged own public key
 *    the alleged own name (not verified only used for the UIs)
 *    a signature of the contribution(signed with the privkey of the contribution creator)
 *
 * Todo: PROBLEM: it is possible to influence/decide the outcome of a challenge if (and only if) one is the last contributor
 *                Since the last contributor can know all previous contributions, it can retry feeding the shared-prng with different contributions until an acceptable result is output.
 *                  This requires knowledge of what a desired result is and depending on the shared-prng and the complexity of the result considerable computing power
 *                Possible solution:
 *                  - Challenge Timeout: Can be used to give the attacker less time to calculate the manipulated contribution
 *                      Possible implementation: The challenge creator closes the challenge after a fixed timeout with another transaction
 *                      If not all txs are available by that time, the challenge is discarded
 *                  - To increase the difficulty of manipulation:
 *                      use a more complex shared-prng
 *                      increase the desired number of shared-random result bytes
 *                  - Context wise: Make it difficult to predict what a desired result could be
 *
 *
 * @author jokrey
 */

open class SharedRandomness(private val maxContributors:Int = 11, private val maxContributionLength: Int = 128, val maxResultRandomnessLength: Int = 128, private val waitForTxToBePersistedBeforeRegardingThemAsNew: Boolean = true) : VisualizableApp {
    private val activeChallenges = HashMap<RandomnessChallenge, MutableList<ChallengeContribution>>()

    override fun preMemPoolVerify(instance: Mockchain, tx: Transaction): RejectionReason.APP_VERIFY? {
        val (newContribution, signature) = ChallengeContribution.fromTx(tx)
        val challenge = newContribution.challenge

        if(newContribution.contribution.isEmpty())
            return RejectionReason.APP_VERIFY("contribution cannot be empty")
        if(challenge.size <= 1)
            return RejectionReason.APP_VERIFY("need two or more parties for a randomness challenge - 1 or less given")
        if(challenge.pubKs.map { base64Encode(it) }.hasDuplicate()) //todo - VERY inefficient
            return RejectionReason.APP_VERIFY("public keys are not unique")
        if(newContribution.contributorPubK !in challenge)
            return RejectionReason.APP_VERIFY("contributor public key not in challenge keys")
        if(! newContribution.verify(signature))
            return RejectionReason.APP_VERIFY("could not verify signature")

        //verify the maxima
        if(challenge.resultRandomnessLength > maxResultRandomnessLength)
            return RejectionReason.APP_VERIFY("challenge.resultRandomnessLength > maxResultRandomnessLength")
        if(challenge.pubKs.size > maxContributors)
            return RejectionReason.APP_VERIFY("challenge.numberOfContributors > maxContributionLength")
        if(newContribution.contribution.size > maxContributionLength)
            return RejectionReason.APP_VERIFY("newContribution.contribution.size > maxContributionLength")

        return null //accepted
    }

    override fun blockVerify(instance: Mockchain, blockCreatorIdentity:ByteArray, vararg txs: Transaction): List<Pair<Transaction, RejectionReason.APP_VERIFY>> {
        val denied = ArrayList<Pair<Transaction, RejectionReason.APP_VERIFY>>()

        val virtualActiveChallengesVerify = HashMap<RandomnessChallenge, MutableList<ChallengeContribution>>()
        for(entry in activeChallenges.entries)
            virtualActiveChallengesVerify[entry.key] = entry.value.toTypedArray().toMutableList() //creates a copy

        for(tx in txs) {
            val (newContribution, _) = ChallengeContribution.fromTx(tx)
            val challenge = newContribution.challenge

            val list = virtualActiveChallengesVerify.computeIfAbsent(challenge) { ArrayList() }
            //verify that it does not replace an existing challenge contribution
            if(list.contains(newContribution)) {
                denied.add(Pair(tx, RejectionReason.APP_VERIFY("contribution already received for challenge <duplicate contribution>")))
                continue
            }
            list.add(newContribution)
            if(list.size == challenge.size)
                virtualActiveChallengesVerify.remove(challenge)
        }

        return denied
    }



    private val unpersistedActiveChallenges = HashMap<RandomnessChallenge, MutableList<ChallengeContribution>>()
    private fun updateUnpersistedActiveChallenges() {
        if(waitForTxToBePersistedBeforeRegardingThemAsNew) return
        synchronized(unpersistedActiveChallenges) {
            unpersistedActiveChallenges.clear()
            for (entry in activeChallenges.entries)
                unpersistedActiveChallenges[entry.key] = entry.value.toTypedArray().toMutableList() //creates a copy
        }
    }
    override fun newTxInMemPool(instance: Mockchain, tx: Transaction) {
        if(! waitForTxToBePersistedBeforeRegardingThemAsNew) {
            synchronized(unpersistedActiveChallenges) {
                val (newContribution, _) = ChallengeContribution.fromTx(tx)
                val challenge = newContribution.challenge

                for (ncl in newContributionListeners) ncl(newContribution)

                val list = unpersistedActiveChallenges.computeIfAbsent(challenge) { ArrayList() }
                list.add(newContribution)
                if (list.size == 1)
                    for (ncl in newChallengeListeners)
                        ncl(newContribution)

                if (list.size == challenge.size) {
                    unpersistedActiveChallenges.remove(challenge)
                    for (ccimpl in rawChallengeCompleteInMemPoolListeners)
                        ccimpl(challenge, list)
                }

                println("unpersistedActiveChallenges = $unpersistedActiveChallenges")
            }
        }
    }

    override fun newBlock(instance: Mockchain, block: Block, newTransactions: List<Transaction>) {
        for(tx in newTransactions) {
            val (newContribution, _) = ChallengeContribution.fromTx(tx)

            if(waitForTxToBePersistedBeforeRegardingThemAsNew)
                for(ncl in newContributionListeners) ncl(newContribution)

            val challenge = newContribution.challenge
            val list = activeChallenges.computeIfAbsent(challenge) { ArrayList() }
            list.add(newContribution)
            if(list.size == 1) {
                if(waitForTxToBePersistedBeforeRegardingThemAsNew) {
                    for (ncl in newChallengeListeners) ncl(newContribution)
                }
            }
            if(list.size == challenge.size) {
                for (ccl in rawChallengeCompleteListeners)
                    ccl(challenge, list)
                activeChallenges.remove(challenge)
            }
        }

        updateUnpersistedActiveChallenges()
    }

    override fun txRemoved(instance: Mockchain, oldHash: TransactionHash, oldTx: Transaction, txWasPersisted: Boolean) { }
    override fun txAltered(instance: Mockchain, oldHash: TransactionHash, oldTx: Transaction, newHash: TransactionHash, newTx: Transaction, txWasPersisted: Boolean) {}
    override fun txRejected(instance: Mockchain, oldHash: TransactionHash, oldTx: Transaction, reason: RejectionReason) {}

    override fun shortDescriptor(tx: Transaction) = ChallengeContribution.fromTx(tx).first.shortDescriptor()
    override fun longDescriptor(tx: Transaction) = ChallengeContribution.fromTx(tx).first.longDescriptor()
    override fun shortStateDescriptor() = exhaustiveStateDescriptor()
    override fun exhaustiveStateDescriptor() = "TODO"

    override fun next(instance: Mockchain, step: Long, random: Random):Optional<Transaction> = Optional.empty()

    override fun getEqualFreshCreator(): () -> VisualizableApp = { SharedRandomness(maxContributors, maxContributionLength, maxResultRandomnessLength) }
    override fun getCreatorParamNames():Array<String> = arrayOf("(int) maxContributors", "(int) maxContributionLength", "(int) maxResultRandomnessLength")
    override fun getCurrentParamContentForEqualCreation():Array<String> = arrayOf(maxContributors.toString(), maxContributionLength.toString(), maxResultRandomnessLength.toString())
    override fun createNewInstance(vararg params: String) = SharedRandomness(params[0].toInt(), params[1].toInt(), params[2].toInt())
    override fun createTxFrom(input: String): Transaction {
        try {
            val split = input.split(",").map { it.trim() }

            val contributorName = split[0]
            val contribution = base64Decode(split[1])
            val ownKeyPair = decodeAndVerifyKeyPair(split[2]) ?: throw IllegalArgumentException("key pair is invalid")
            val furtherContributionKeys = split.subList(3, split.size).map { base64Decode(it) }

            val challengeContribution = ChallengeContribution(ownKeyPair.public.encoded, contributorName, RandomnessChallenge(maxResultRandomnessLength, furtherContributionKeys + ownKeyPair.public.encoded), contribution)
            return challengeContribution.toTx(ownKeyPair.private.encoded)
        } catch (t: Throwable) {
            t.printStackTrace()
            throw IllegalArgumentException("please provide in the form: contributorName[string], contribution[base64], ownPubkey[base64]:ownPrivkey[base64], contribKey0[base64], contribKey1[base64], <etc..>")
        }
    }

    override fun cleanUpAfterForkInvalidatedThisState() {} //NO NEED TO DO ANYTHING SINCE THE GC WILL TAKE CARE OF IT


    /**
     * Algorithm used by the default UIs to generate a contribution.
     * CAN BE OVERRIDDEN TO PRODUCE CONTRIBUTIONS OF DIFFERENT LENGTHS OR USE A DIFFERENT ALGORITHM ALL TOGETHER
     * Should produce secure randomness of some sort. The actual algorithm used for that does not matter and can be different between instance. Can be truly random.
     */
    open fun generateContribution() : ByteArray {
        val secRand = SecureRandom()
        val bytes = ByteArray(maxContributionLength)
        secRand.nextBytes(bytes)
        return bytes
    }
    /**
     * Algorithm to combine the individual contributions to a shared randomness.
     * CAN BE OVERRIDDEN TO USE A DIFFERENT ALGORITHM
     * However it is required to be the same algorithm between all instances of the blockchain.
     * Cannot be truly random. I.e. for the given parameters the same result has to be calculated each time.
     */
    open fun combineContributionsToFinalResult(desiredLength: Int, contributions: List<ChallengeContribution>) : SharedRandomnessResult {
        val randomnessContributions = contributions.map { it.contribution }
        randomnessContributions.sortedWith(Comparator { o1, o2 ->
            if(o1.size == o2.size) {
                for (i in 0..o1.size)
                    if(o1[i] < o2[i])
                        return@Comparator 1
                    else if(o1[i] > o2[i])
                        return@Comparator -1
                return@Comparator 0
            } else {
                return@Comparator 1
            }
        })
        val seed = randomnessContributions.spread()
        val prng = SHA1PRNG(seed)
        return SharedRandomnessResult(contributions.map { Pair(it.contributorName, it.contributorPubK) }, prng.nextBytes(desiredLength))
    }




    //actual app accessible functionality:
    private val rawChallengeCompleteListeners = CopyOnWriteArrayList<(RandomnessChallenge, List<ChallengeContribution>) -> Unit>()
    /** Can be used to allow callers some level of self validation. For example that their own transaction was included. Useful if the caller does not control the instance, for example through the web. */
    fun addRawChallengeCompletedListener(callback: (RandomnessChallenge, List<ChallengeContribution>) -> Unit) = rawChallengeCompleteListeners.add(callback)
    fun removeRawChallengeCompletedListener(callback: (RandomnessChallenge, List<ChallengeContribution>) -> Unit) = rawChallengeCompleteListeners.remove(callback)
    fun clearRawChallengeCompletedListener() {
        rawChallengeCompleteListeners.clear()
        registerDefaultChallengeCompleteListener()
    }

    private val challengeCompleteListeners = CopyOnWriteArrayList<(RandomnessChallenge, SharedRandomnessResult) -> Unit>()
    fun addChallengeCompletedListener(callback: (RandomnessChallenge, SharedRandomnessResult) -> Unit) = challengeCompleteListeners.add(callback)
    fun removeChallengeCompletedListener(callback: (RandomnessChallenge, SharedRandomnessResult) -> Unit) = challengeCompleteListeners.remove(callback)
    fun clearChallengeCompletedListeners() = challengeCompleteListeners.clear()
    init {
        registerDefaultChallengeCompleteListener()
    }
    private fun registerDefaultChallengeCompleteListener() {
        addRawChallengeCompletedListener { randomnessChallenge, list ->
            for(challengeCompletedListener in challengeCompleteListeners)
                challengeCompletedListener(randomnessChallenge, combineContributionsToFinalResult(randomnessChallenge.resultRandomnessLength, list))
        }
    }

    private val newContributionListeners = CopyOnWriteArrayList<(ChallengeContribution) -> Unit>()
    fun addNewContributionListener(callback: (ChallengeContribution) -> Unit) = newContributionListeners.add(callback)
    fun removeNewContributionListener(callback: (ChallengeContribution) -> Unit) = newContributionListeners.remove(callback)
    fun clearNewContributionListeners() = newContributionListeners.clear()

    private val newChallengeListeners = CopyOnWriteArrayList<(ChallengeContribution) -> Unit>()
    fun addNewChallengeListener(callback: (ChallengeContribution) -> Unit) = newChallengeListeners.add(callback)
    fun removeNewChallengeListener(callback: (ChallengeContribution) -> Unit) = newChallengeListeners.remove(callback)
    fun clearNewChallengeListeners() {
        newChallengeListeners.clear()
        registerDefaultNewChallengeForMeListener()
    }

    private var newChallengeForMeListeners = ConcurrentHashMap<ImmutableByteArray, CopyOnWriteArrayList<(RandomnessChallenge, String) -> Unit>>()
    fun addNewChallengeForMeListener(myEncodedPublicKey: ByteArray, callback: (RandomnessChallenge, String) -> Unit) {
        newChallengeForMeListeners.computeIfAbsent(ImmutableByteArray(myEncodedPublicKey)) { CopyOnWriteArrayList() }.add(callback)
    }
    fun removeNewChallengeForMeListener(myEncodedPublicKey: ByteArray, callback: (RandomnessChallenge, String) -> Unit) {
        newChallengeForMeListeners.computeIfPresent(ImmutableByteArray(myEncodedPublicKey)) { k, list ->
            list.remove(callback)
            if(list.isEmpty()) null else list
        }
    }
    fun clearNewChallengeForMeListeners() = newChallengeForMeListeners.clear()
    init {
        registerDefaultNewChallengeForMeListener()
    }
    private fun registerDefaultNewChallengeForMeListener() {
        addNewChallengeListener { newContribution ->
            for (pk in newContribution.challenge.iteratorWithout(newContribution.contributorPubK)) {
                val newChallengeForMeListeners = newChallengeForMeListeners[ImmutableByteArray(pk)]
                if (newChallengeForMeListeners != null)
                    for (ncfml in newChallengeForMeListeners)
                        ncfml(newContribution.challenge, newContribution.contributorName)
            }
        }
    }

    private val rawChallengeCompleteInMemPoolListeners = CopyOnWriteArrayList<(RandomnessChallenge, List<ChallengeContribution>) -> Unit>()
    /** Can be used to allow callers some level of self validation. For example that their own transaction was included. Useful if the caller does not control the instance, for example through the web. */
    fun addRawChallengeCompletedInMemPoolListener(callback: (RandomnessChallenge, List<ChallengeContribution>) -> Unit) = rawChallengeCompleteInMemPoolListeners.add(callback)
    fun removeRawChallengeCompletedInMemPoolListener(callback: (RandomnessChallenge, List<ChallengeContribution>) -> Unit) = rawChallengeCompleteInMemPoolListeners.remove(callback)
    fun clearRawChallengeCompletedInMemPoolListener() {
        rawChallengeCompleteInMemPoolListeners.clear()
    }


    fun startChallenge(instance: Mockchain, challenge: RandomnessChallenge, myKeyPair: KeyPair, myName: String, contribution: ByteArray) =
            contributeToChallenge(instance, challenge, myKeyPair, myName, contribution)
    fun contributeToChallenge(instance: Mockchain, challenge: RandomnessChallenge, myKeyPair: KeyPair, myName: String, contribution: ByteArray) : P2LFuture<SharedRandomnessResult> {
        val future = P2LFuture<SharedRandomnessResult>()
        var callback: ((RandomnessChallenge, SharedRandomnessResult) -> Unit)? = null
        callback = { completedChallenge, finalRandomness ->
            if(completedChallenge == challenge) {
                future.setCompleted(finalRandomness)
                removeChallengeCompletedListener(callback!!)
            }
        }
        addChallengeCompletedListener(callback)

        val challengeContribution = ChallengeContribution(myKeyPair.public.encoded, myName, challenge, contribution)
        instance.commitToMemPool(challengeContribution.toTx(myKeyPair.private.encoded))
        return future
    }
}


private fun <T> Iterable<T>.hasDuplicate(): Boolean {
    val set = HashSet<T>()
    for (t in this) if (!set.add(t)) return true
    return false
}

private fun Iterable<ByteArray>.spread(): ByteArray {
    val builder = ByteArrayStorage()
    for(bs in this)
        builder.append(bs)
    return builder.content
}