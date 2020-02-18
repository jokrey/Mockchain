package jokrey.mockchain.consensus

import jokrey.mockchain.Mockchain
import jokrey.mockchain.storage_classes.*
import jokrey.mockchain.visualization.util.UserAuthHelper
import jokrey.utilities.simple.data_structure.stack.ConcurrentStackTest.sleep
import java.lang.IllegalArgumentException
import java.security.KeyPair
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
        val selectedTxs = instance.memPool.getTransactions().toMutableList()
        val newSquashState = removeAllRejectedTransactionsFrom(ImmutableByteArray(ownKeyPair.public.encoded), selectedTxs) //VERY IMPORTANT LINE
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
        currentIndex = plusInBounds(findIdentityIndex(extractBlockCreatorIdentityFromProof(newBlock.proof).raw), 1, preApprovedIdentitiesPKs.size)
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
    override fun extractBlockCreatorIdentityFromProof(proof: Proof) = ImmutableByteArray(proof.raw.copyOfRange(1, proof.size - UserAuthHelper.signatureLength()))
    override fun getLocalIdentity() = ImmutableByteArray(ownKeyPair.public.encoded)
    override fun validateJustReceivedProof(receivedBlock: Block): Boolean {
        val proofToValidate = receivedBlock.proof
        val allegedIdentity = extractBlockCreatorIdentityFromProof(proofToValidate)

        val receivedIdentityIndex = findIdentityIndex(allegedIdentity.raw)
        if(receivedIdentityIndex == -1) return false
        val isCorrectIdentity = receivedIdentityIndex == currentIndex
        if(!isCorrectIdentity) {
            val timePassedSinceLast = System.currentTimeMillis() - lastBlockAddedTimestamp
            return timePassedSinceLast > whenAmIAllowed(allegedIdentity.raw)
        }

        val signature = ByteArray(UserAuthHelper.signatureLength())
        System.arraycopy(proofToValidate.raw, proofToValidate.raw.size-signature.size, signature, 0, signature.size)
        val messageToSign = calculateMessageToSign(receivedBlock.previousBlockHash, receivedBlock.merkleRoot, proofToValidate.raw.copyOfRange(0, proofToValidate.size - signature.size))

        return UserAuthHelper.verify(messageToSign, signature, allegedIdentity.raw)
    }
}

class ProofOfStaticStakeConsensusCreator(private val fixedBlockIntervalMs: Int, private val preApprovedIdentitiesPKs: Array<ByteArray>, private val ownKeyPair: KeyPair) : ConsensusAlgorithmCreator {
    override fun create(instance: Mockchain) = ProofOfStaticStake(instance, fixedBlockIntervalMs, preApprovedIdentitiesPKs, ownKeyPair)
}