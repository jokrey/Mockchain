package jokrey.mockchain.consensus

import jokrey.mockchain.Mockchain
import jokrey.mockchain.squash.SquashAlgorithmState
import jokrey.mockchain.storage_classes.*
import jokrey.utilities.base64Decode
import jokrey.utilities.base64Encode
import jokrey.utilities.bitsandbytes.BitHelper
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Adaptable proof of work consensus algorithm - proposing new blocks on its own.
 *
 * Transaction selection can be overridden.
 * Not all transactions in the mem pool need to be selected - Additionally, certain transactions can be added without being broadcast within the mem pool.
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
     * Can be overridden to recalculate difficulty and whether to request a squash
     */
    override fun notifyNewLatestBlockPersisted(newBlock: Block) {
        reselectTxsToBuild()
    }

    private val lock = ReentrantLock()
    private val condition = lock.newCondition()
    private var stopped = false

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
            val changes = removeAllRejectedTransactionsFrom(
                blockCreatorIdentity = minerIdentity,
                proposedTransactions = selectedTxs
            ) //VERY IMPORTANT LINE
            newSquashStateInCaseOfApproval = changes.first
            selectedTxs = changes.second
            merkleRootOfSelectedTxs = MerkleTree(*selectedTxs.map { it.hash }.toTypedArray()).getRoot()
            currentNonce = 0

            condition.signal()
        }
    }

    override fun resume() {
        super.resume()
        lock.withLock {
            condition.signal()
        }
    }

    override fun stop() {
        stopped = true
        resume() //signal
    }


    final override fun run() {
        while(!stopped) {
            lock.withLock {
                while (!stopped && (selectedTxs.isEmpty() || isPaused))
                    condition.await()
                if(stopped) return@withLock

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

                    try {
                        createAndAddLocalBlock(newSquashStateInCaseOfApproval, selectedTxs, latestBlockHash, proof, if(requestSquash) -1 else 0, merkleRootOfSelectedTxs)
                        reselectTxsToBuild()
                    } catch (e: IllegalStateException) {e.printStackTrace()} //can occur in case it was paused in between
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

    final override fun extractRequestSquashNumFromProof(proof: Proof) = if(proof[0] == 1.toByte()) -1 else 0
    final override fun extractBlockCreatorIdentityFromProof(proof: Proof): ByteArray = proof.raw.copyOfRange(5, proof.size - Hash.length())
    final override fun getLocalIdentity(): ByteArray = minerIdentity
    final override fun validateJustReceivedProof(proof: Proof, previousBlockHash: Hash?, merkleRoot: Hash): Boolean {
        val givenSolve = ByteArray(Hash.length())
        System.arraycopy(proof.raw, proof.raw.size-givenSolve.size, givenSolve, 0, givenSolve.size)
        val proofToSolve = proof.raw.copyOfRange(0, proof.size - givenSolve.size)

        return verifySolveAttempt(givenSolve, difficulty) && givenSolve.contentEquals(calculateSolve(previousBlockHash, merkleRoot, proofToSolve).raw)
    }
    override fun allowFork(forkIndex: Int, ownBlockHeight: Int, remoteBlockHeight: Int) = true

    override fun getCreator() = SimpleProofOfWorkConsensusCreator(difficulty, minerIdentity)
}

class SimpleProofOfWorkConsensusCreator(private val difficulty: Int, private val minerIdentity: ByteArray) : ConsensusAlgorithmCreator {
    override fun create(instance: Mockchain) = ProofOfWorkConsensus(instance, difficulty, minerIdentity)

    override fun getEqualFreshCreator(): () -> ConsensusAlgorithmCreator = { SimpleProofOfWorkConsensusCreator(difficulty, minerIdentity) }
    override fun createNewInstance(vararg params: String) = SimpleProofOfWorkConsensusCreator(params[0].toInt(), base64Decode(params[1]))
    override fun getCreatorParamNames() = arrayOf("difficulty (int)", "minerIdentity (base64 array)")
    override fun getCurrentParamContentForEqualCreation() = arrayOf(difficulty.toString(), base64Encode(minerIdentity))
}