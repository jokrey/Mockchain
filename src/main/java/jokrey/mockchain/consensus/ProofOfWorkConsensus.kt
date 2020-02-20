package jokrey.mockchain.consensus

import jokrey.mockchain.Mockchain
import jokrey.mockchain.squash.SquashAlgorithmState
import jokrey.mockchain.storage_classes.*
import jokrey.utilities.bitsandbytes.BitHelper
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Adaptable proof of work consensus algorithm - proposing new blocks on its own.
 *
 * Transaction selection can be overridden.
 * Not all transactions in the mem pool need to be selected - additionally certain transactions can be added without being broadcast within the mem pool.
 * In the example of many real world PoW applications this would be an incentive for mining in which a miner can generate currency for themselves.
 *
 * Difficulty can be changed, though this currently has to occur globally, otherwise previous proofs will appear invalid.
 *
 *
 *
 * Proof layout:
 *    byte[0]: whether a squash is requested
 *    byte[1-4]: nonce
 *    byte[5-x/(end-1-20)]: minerIdentity
 *    byte[(x+1)-end/(x+1+20)]: solve
 *
 * @author jokrey
 */
open class ProofOfWorkConsensus(instance: Mockchain, var difficulty: Int, var minerIdentity: ByteArray, var requestSquash: Boolean = false) : ConsensusAlgorithm(instance), Runnable {
    /**
     * Used to query the transactions to put into the new block once the proof is finished. Will be called for every new attempt.
     */
    open fun selectTransactions() = instance.memPool.getTransactions().toMutableList()

    /**
     * Can be overriden to recalculate difficulty and whether to request a squash
     */
    override fun notifyNewLatestBlockPersisted(newBlock: Block) {
        reselectTxsToBuild()
    }

    private val lock = ReentrantLock()
    private val condition = lock.newCondition()

    //only access in synchronization blocks
    private var newSquashStateInCaseOfApproval: SquashAlgorithmState? = null
    private var latestBlockHash: Hash? = null
    private var selectedTxs : List<Transaction> = emptyList()
    private var merkleRootOfSelectedTxs : Hash = Hash(ByteArray(Hash.length()))
    private var currentNonce = 0
    private fun reselectTxsToBuild() {
        lock.withLock {
            latestBlockHash = instance.chain.getLatestHash()
            selectedTxs = selectTransactions()
            newSquashStateInCaseOfApproval = removeAllRejectedTransactionsFrom(minerIdentity, selectedTxs as MutableList<Transaction>) //VERY IMPORTANT LINE
            merkleRootOfSelectedTxs = MerkleTree(*selectedTxs.map { it.hash }.toTypedArray()).getRoot()
            currentNonce = 0

            condition.signal()
        }
    }


    final override fun run() {
        while(true) {
            lock.withLock {
                while (selectedTxs.isEmpty())
                    condition.await()

                //could be cached
                    val proofToSolve = ByteArray(1 + 4 + minerIdentity.size)
                    proofToSolve[0] = if(requestSquash) 1 else 0
                    System.arraycopy(minerIdentity, 0, proofToSolve, 5, minerIdentity.size)

                BitHelper.writeInt32(proofToSolve, 1, currentNonce)

                val solve = calculateSolve(latestBlockHash, merkleRootOfSelectedTxs, proofToSolve)
                val accepted = verifySolveAttempt(solve.raw, difficulty)

                if(accepted) {
                    val proofBuilder = proofToSolve + solve.raw
                    val proof = Proof(proofBuilder)

                    createAndAddLocalBlock(newSquashStateInCaseOfApproval, selectedTxs, latestBlockHash, proof, requestSquash, merkleRootOfSelectedTxs)

                    reselectTxsToBuild()
                } else
                    currentNonce++
            }
        }
    }

    private fun calculateSolve(latestBlockHash: Hash?, merkleRootOfSelectedTxs: Hash, proofToSolve: ByteArray) =
        Hash(latestBlockHash?.raw ?: byteArrayOf(), merkleRootOfSelectedTxs.raw, proofToSolve)


    private fun verifySolveAttempt(solve: ByteArray, difficulty: Int): Boolean {
        //todo - more complex difficulty interpretation
        for(i in 0..difficulty)
            if(solve[i] != 0.toByte())
                return false
        return true
    }

    final override fun notifyNewTransactionInMemPool(newTx: Transaction) {
        lock.withLock {
            if(selectedTxs.size < 8) {
                reselectTxsToBuild()
            }
        }
    }

    final override fun extractRequestSquashFromProof(proof: Proof) = proof[0] == 1.toByte()
    final override fun extractBlockCreatorIdentityFromProof(proof: Proof): ByteArray = proof.raw.copyOfRange(5, proof.size - Hash.length())
    final override fun getLocalIdentity(): ByteArray = minerIdentity
    final override fun validateJustReceivedProof(receivedBlock: Block): Boolean {
        val proofToValidate = receivedBlock.proof
        val givenSolve = ByteArray(Hash.length())
        System.arraycopy(proofToValidate.raw, proofToValidate.raw.size-givenSolve.size, givenSolve, 0, givenSolve.size)
        val proofToSolve = proofToValidate.raw.copyOfRange(0, proofToValidate.size - givenSolve.size)

        return verifySolveAttempt(givenSolve, difficulty) && givenSolve.contentEquals(calculateSolve(receivedBlock.previousBlockHash, receivedBlock.merkleRoot, proofToSolve).raw)
    }

    override fun getCreator() = SimpleProofOfWorkConsensusCreator(difficulty, minerIdentity)
}

class SimpleProofOfWorkConsensusCreator(private val difficulty: Int, private val minerIdentity: ByteArray) : ConsensusAlgorithmCreator {
    override fun create(instance: Mockchain) = ProofOfWorkConsensus(instance, difficulty, minerIdentity)

    override fun getEqualFreshCreator(): () -> ConsensusAlgorithmCreator = { SimpleProofOfWorkConsensusCreator(difficulty, minerIdentity) }
    override fun createNewInstance(vararg params: String) = SimpleProofOfWorkConsensusCreator(params[0].toInt(), base64Decode(params[1]))
    override fun getCreatorParamNames() = arrayOf("difficulty (int)", "minerIdentity (base64 array)")
    override fun getCurrentParamContentForEqualCreation() = arrayOf(difficulty.toString(), base64Encode(minerIdentity))
}