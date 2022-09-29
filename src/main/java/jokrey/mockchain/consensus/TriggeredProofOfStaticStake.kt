package jokrey.mockchain.consensus

import jokrey.mockchain.Mockchain
import jokrey.mockchain.storage_classes.*
import jokrey.utilities.*
import jokrey.utilities.bitsandbytes.BitHelper
import jokrey.utilities.misc.RSAAuthHelper
import java.security.KeyPair

/**
 * Most naive proof of stake implementation.
 * A set of approved and fixed public key identities of the allowed block proposers is given, the order has to be identical on all nodes.
 *     It can be reasonable to write these identities into the genesis block.
 *
 *
 * @author jokrey
 */
class TriggeredProofOfStaticStake(instance: Mockchain, val preApprovedIdentitiesPKs: List<ByteArray>, val ownKeyPair: KeyPair) : ConsensusAlgorithm(instance) {
    private var currentIndex = 0
    private val ownIndex = preApprovedIdentitiesPKs.findIndex(ownKeyPair.public.encoded)
    init {
        if (ownIndex == -1)
            throw IllegalArgumentException("own key pair not included in pre approved identities list")
        println("preApprovedIdentitiesPKs = ${preApprovedIdentitiesPKs.map { it.toList() }}")
    }

    override fun run() {}

    fun proposeBlockIfMyTurn(requestSquashNum: Int = 0) {
        println("ownIndex = $ownIndex")
        println("currentIndex = $currentIndex")
        if(ownIndex == currentIndex)
            proposeBlock(requestSquashNum)
    }
    //will not return before notifyNewLatestBlockPersisted - which resets the parameters
    fun proposeBlock(requestSquashNum: Int = 0) {
        if(isPaused) return

        val selectedTxs = instance.memPool.getTransactions().toMutableList()
        if(selectedTxs.isEmpty()) return //todo - should this be done differently? - this leads to multiple parties instantly proposing when the new tx is finally available to the mem pool

        val (newSquashState, selectedSortedTransactions) = removeAllRejectedTransactionsFrom(
            blockCreatorIdentity = ownKeyPair.public.encoded,
            proposedTransactions = selectedTxs
        ) //VERY IMPORTANT LINE
        val merkleRootOfSelectedTxs = MerkleTree(*selectedSortedTransactions.map { it.hash }.toTypedArray()).getRoot()
        val latestHash = instance.chain.getLatestHash()

        val proofData = BitHelper.getBytes(0) + ownKeyPair.public.encoded
        val signature = RSAAuthHelper.sign(calculateMessageToSign(latestHash, merkleRootOfSelectedTxs, proofData), ownKeyPair.private)
        val proof = Proof(proofData + signature)

        createAndAddLocalBlock(newSquashState, selectedSortedTransactions, latestHash, proof, requestSquashNum, merkleRoot = merkleRootOfSelectedTxs)
    }

    private fun calculateMessageToSign(previousBlockHash: Hash?, merkleRoot: Hash, proofData: ByteArray): ByteArray {
        return previousBlockHash?.raw ?: (byteArrayOf() + merkleRoot.raw + proofData)
    }

    override fun notifyNewLatestBlockPersisted(newBlock: Block) {
        currentIndex = plusInBounds(preApprovedIdentitiesPKs.findIndex(extractBlockCreatorIdentityFromProof(newBlock.proof)), 1, preApprovedIdentitiesPKs.size)
    }

    override fun notifyNewTransactionInMemPool(newTx: Transaction) {  }

    override fun extractRequestSquashNumFromProof(proof: Proof) = BitHelper.getInt32From(proof.raw, 0)
    override fun extractBlockCreatorIdentityFromProof(proof: Proof): ByteArray = proof.raw.copyOfRange(4, proof.size - RSAAuthHelper.signatureLength())
    override fun getLocalIdentity(): ByteArray = ownKeyPair.public.encoded
    override fun validateJustReceivedProof(proof: Proof, previousBlockHash: Hash?, merkleRoot: Hash): Boolean {
        val allegedIdentity = extractBlockCreatorIdentityFromProof(proof)

        val receivedIdentityIndex = preApprovedIdentitiesPKs.findIndex(allegedIdentity)
        if(receivedIdentityIndex == -1) return false
        val isCorrectIdentity = receivedIdentityIndex == currentIndex
        if(!isCorrectIdentity) return false

        val signature = ByteArray(RSAAuthHelper.signatureLength())
        System.arraycopy(proof.raw, proof.raw.size-signature.size, signature, 0, signature.size)
        val messageToSign = calculateMessageToSign(previousBlockHash, merkleRoot, proof.raw.copyOfRange(0, proof.size - signature.size))

        return RSAAuthHelper.verify(messageToSign, signature, allegedIdentity)
    }
    override fun allowFork(forkIndex: Int, ownBlockHeight: Int, remoteBlockHeight: Int) = true

    override fun getCreator() = TriggeredProofOfStaticStakeConsensusCreator(preApprovedIdentitiesPKs, ownKeyPair)
}

class TriggeredProofOfStaticStakeConsensusCreator(private val preApprovedIdentitiesPKs: List<ByteArray>, private val ownKeyPair: KeyPair) : ConsensusAlgorithmCreator {
    override fun create(instance: Mockchain) = TriggeredProofOfStaticStake(instance, preApprovedIdentitiesPKs, ownKeyPair)

    override fun getEqualFreshCreator(): () -> ConsensusAlgorithmCreator = { TriggeredProofOfStaticStakeConsensusCreator(preApprovedIdentitiesPKs, ownKeyPair) }
    override fun createNewInstance(vararg params: String) = TriggeredProofOfStaticStakeConsensusCreator(decodePublicKeys(params[1]).toList(), decodeKeyPair(params[2]))
    override fun getCreatorParamNames() = arrayOf("public keys of all peers (key0[base64], key1[base64], <etc..>)", "ownKeyPair (pubkey[base64], privkey[base64]")
    override fun getCurrentParamContentForEqualCreation() = arrayOf(encodePublicKeys(preApprovedIdentitiesPKs), encodeKeyPair(ownKeyPair))
}