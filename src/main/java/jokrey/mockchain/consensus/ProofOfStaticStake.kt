package jokrey.mockchain.consensus

import jokrey.mockchain.Mockchain
import jokrey.mockchain.storage_classes.*
import jokrey.mockchain.visualization.util.UserAuthHelper
import jokrey.utilities.simple.data_structure.stack.ConcurrentStackTest.sleep
import java.security.KeyPair
import java.util.*
import kotlin.math.roundToInt

/**
 * Most naive proof of stake implementation.
 * A set of approved and fixed public key identities of the allowed block proposers is given, the order has to be identical on all nodes.
 *     It can be reasonable to write these identities into the genesis block.
 * So is the time at which proposers propose.
 * Since time is impossible in a distributed context they can do so whenever(not checked), but are supposed to only after 'fixedBlockIntervalMs'.
 * They may only propose if it is their turn. It starts at 1 and goes around.
 *     Additionally an instance will allow blocks that come from the next proposer after fixedBlockIntervalMs*1.66.
 *     ((todo - There should be a mechanism to penalize repeatedly missing a proposal))
 *
 * @author jokrey
 */
class ProofOfStaticStake(instance: Mockchain, private val fixedBlockIntervalMs: Int, val preApprovedIdentitiesPKs: Array<ByteArray>, val ownKeyPair: KeyPair) : ConsensusAlgorithm(instance) {
    var currentIndex = 0
    var lastBlockAddedTimestamp: Long = -1
    init {
        findIdentityIndex(ownKeyPair.public.encoded)
    }

    private fun findIdentityIndex(identity: ByteArray): Int {
        for((i, v) in preApprovedIdentitiesPKs.withIndex())
            if(v.contentEquals(identity))
                return i
        throw IllegalArgumentException("identity unrecognised")
    }

    fun confidenceMinDirectSuccessor() = 0.8
    fun confidenceMinAllowNext() = 0.61

    override fun run() {
        while(true) {
            if(lastBlockAddedTimestamp == -1L) {
                if(findIdentityIndex(ownKeyPair.public.encoded) == 0)
                    proposeBlock()
            } else {
                val timePassedSinceLast = System.currentTimeMillis() - lastBlockAddedTimestamp
                if(timePassedSinceLast > whenAmIAllowed(ownKeyPair.public.encoded))
                    proposeBlock()
            }

            sleep(fixedBlockIntervalMs.toLong() / 10)
        }
    }

    private fun whenAmIAllowed(identity: ByteArray): Int {
        val diffToCurrent = diffInBounds(currentIndex, findIdentityIndex(identity), preApprovedIdentitiesPKs.size)
        return (confidenceMinDirectSuccessor()*fixedBlockIntervalMs + (confidenceMinAllowNext()*fixedBlockIntervalMs) * diffToCurrent).roundToInt()
    }


    //will not return before notifyNewLatestBlockPersisted - which resets the parameters
    private fun proposeBlock() {
        if(isPaused) return

        val selectedTxs = instance.memPool.getTransactions().toMutableList()
        if(selectedTxs.isEmpty()) return //todo - should this be done differently? - this leads to multiple parties instantly proposing when the new tx is finally available to the mem pool

        val newSquashState = removeAllRejectedTransactionsFrom(ownKeyPair.public.encoded, selectedTxs) //VERY IMPORTANT LINE
        val merkleRootOfSelectedTxs = MerkleTree(*selectedTxs.map { it.hash }.toTypedArray()).getRoot()
        val latestHash = instance.chain.getLatestHash()

        val proofData = byteArrayOf(0) + ownKeyPair.public.encoded
        val signature = UserAuthHelper.sign(calculateMessageToSign(latestHash, merkleRootOfSelectedTxs, proofData), ownKeyPair.private)
        val proof = Proof(proofData + signature)

        createAndAddLocalBlock(newSquashState, selectedTxs, latestHash, proof, requestSquash = false, merkleRoot = merkleRootOfSelectedTxs)
    }

    private fun calculateMessageToSign(previousBlockHash: Hash?, merkleRoot: Hash, proofData: ByteArray): ByteArray {
        return previousBlockHash?.raw ?: byteArrayOf() + merkleRoot.raw + proofData
    }

    override fun notifyNewLatestBlockPersisted(newBlock: Block) {
        currentIndex = plusInBounds(findIdentityIndex(extractBlockCreatorIdentityFromProof(newBlock.proof)), 1, preApprovedIdentitiesPKs.size)
        lastBlockAddedTimestamp = System.currentTimeMillis()
    }

    private fun plusInBounds(orig: Int, plus: Int, bounds: Int): Int {
        val res = (orig + plus) % bounds
//        println("ProofOfStaticStake.plusInBounds - orig = [${orig}], plus = [${plus}], bounds = [${bounds}]  ===>>> res = [$res]")
        return if(res < 0) res + bounds else res
    }
    private fun diffInBounds(orig: Int, elsewhere: Int, bounds: Int) : Int {
        if(orig<0 || orig>=bounds || elsewhere<0 || elsewhere>=bounds) throw IllegalArgumentException()
        if(elsewhere >= orig) return elsewhere-orig
        return (bounds - elsewhere) + orig
    }

    override fun notifyNewTransactionInMemPool(newTx: Transaction) {  }

    override fun extractRequestSquashFromProof(proof: Proof) = proof[0] == 1.toByte()
    override fun extractBlockCreatorIdentityFromProof(proof: Proof): ByteArray = proof.raw.copyOfRange(1, proof.size - UserAuthHelper.signatureLength())
    override fun getLocalIdentity(): ByteArray = ownKeyPair.public.encoded
    override fun validateJustReceivedProof(proof: Proof, previousBlockHash: Hash?, merkleRoot: Hash): Boolean {
        val allegedIdentity = extractBlockCreatorIdentityFromProof(proof)

        val receivedIdentityIndex = findIdentityIndex(allegedIdentity)
        if(receivedIdentityIndex == -1) return false
        val isCorrectIdentity = receivedIdentityIndex == currentIndex
        if(!isCorrectIdentity) {
            val timePassedSinceLast = System.currentTimeMillis() - lastBlockAddedTimestamp
            return timePassedSinceLast > whenAmIAllowed(allegedIdentity)
        }

        val signature = ByteArray(UserAuthHelper.signatureLength())
        System.arraycopy(proof.raw, proof.raw.size-signature.size, signature, 0, signature.size)
        val messageToSign = calculateMessageToSign(previousBlockHash, merkleRoot, proof.raw.copyOfRange(0, proof.size - signature.size))

        return UserAuthHelper.verify(messageToSign, signature, allegedIdentity)
    }
    override fun allowFork(forkIndex: Int, ownBlockHeight: Int, remoteBlockHeight: Int) = true

    override fun getCreator() = ProofOfStaticStakeConsensusCreator(fixedBlockIntervalMs, preApprovedIdentitiesPKs, ownKeyPair)
}

class ProofOfStaticStakeConsensusCreator(private val fixedBlockIntervalMs: Int, private val preApprovedIdentitiesPKs: Array<ByteArray>, private val ownKeyPair: KeyPair) : ConsensusAlgorithmCreator {
    override fun create(instance: Mockchain) = ProofOfStaticStake(instance, fixedBlockIntervalMs, preApprovedIdentitiesPKs, ownKeyPair)

    override fun getEqualFreshCreator(): () -> ConsensusAlgorithmCreator = { ProofOfStaticStakeConsensusCreator(fixedBlockIntervalMs, preApprovedIdentitiesPKs, ownKeyPair) }
    override fun createNewInstance(vararg params: String) = ProofOfStaticStakeConsensusCreator(params[0].toInt(), decodePublicKeys(params[1]), decodeKeyPair(params[2]))
    override fun getCreatorParamNames() = arrayOf("fixedBlockIntervalMs (int)", "public keys of all peers (key0[base64], key1[base64], <etc..>)", "ownKeyPair (pubkey[base64], privkey[base64]")
    override fun getCurrentParamContentForEqualCreation() = arrayOf(fixedBlockIntervalMs.toString(), encodePublicKeys(preApprovedIdentitiesPKs), encodeKeyPair(ownKeyPair))
}

fun generateKeyPairs(num: Int) = Array(num) { UserAuthHelper.generateKeyPair() }
fun encodeKeyPair(pair: KeyPair) = base64Encode(pair.public.encoded)+":"+ base64Encode(pair.private.encoded)
fun decodeKeyPair(encodedPair: String): KeyPair {
    val split = encodedPair.trim().split(":")
    return UserAuthHelper.readKeyPair(base64Decode(split[0]), base64Decode(split[1]))
}
fun encodeKeyPairs(pairs: Array<KeyPair>) = pairs.joinToString(",") { encodeKeyPair(it) }
fun decodeKeyPairs(encodedPairs: String) = encodedPairs.split(",").map { decodeKeyPair(it) }.toTypedArray()
fun extractPublicKeys(pairs: Array<KeyPair>) = pairs.map { it.public.encoded }.toTypedArray()
fun encodePublicKeys(pairs: Array<KeyPair>) = encodePublicKeys(extractPublicKeys(pairs))
fun encodePublicKeys(publicKeys: Array<ByteArray>) = publicKeys.joinToString(",") { base64Encode(it) }
fun decodePublicKeys(encodedKeys: String) = encodedKeys.split(",").map { base64Decode(it.trim()) }.toTypedArray()
fun extractKeyPair(encodedPairs: String, index: Int) = decodeKeyPairs(encodedPairs)[index]

fun base64Encode(a: ByteArray): String = Base64.getEncoder().encodeToString(a)
fun base64Decode(s: String): ByteArray = Base64.getDecoder().decode(s)