package jokrey.mockchain.consensus

import jokrey.mockchain.Mockchain
import jokrey.mockchain.squash.SquashAlgorithmState
import jokrey.mockchain.storage_classes.*
import jokrey.utilities.bitsandbytes.BitHelper
import java.lang.Thread
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
open class ProofOfWorkConsensus(instance: Mockchain, protected var difficulty: Int, val minerIdentity: ImmutableByteArray) : ConsensusAlgorithm(instance), Runnable {
    /**
     * Used to query the transactions to put into the new block once the proof is finished. Will be called for every new attempt.
     */
    open fun selectTransactions() = instance.memPool.getTransactions().toMutableList()
    /**
     * Whether a squash is requested with the new block.
     */
    open fun requestSquash() = false
    /**
     * Allows recalculation of difficulty. Result has to be written into the field.
     */
    open fun recalculateDifficulty() {}


    private val lock = ReentrantLock()
    private val condition = lock.newCondition()
    final override fun runConsensusLoopInNewThread() {
        Thread(this).start()
    }

    //only access in synchronization blocks
    private var newSquashStateInCaseOfApproval: SquashAlgorithmState? = null
    private var latestBlockHash: Hash? = null
    private var selectedTxs : List<Transaction> = emptyList()
    private var merkleRootOfSelectedTxs : Hash = Hash(ByteArray(Hash.length()))
    private var squashRequestedWithBlockToProof = false
    private var currentNonce = 0
    private fun reselectTxsToBuild() {
        lock.withLock {
            latestBlockHash = instance.chain.getLatestHash()
            selectedTxs = selectTransactions()
            newSquashStateInCaseOfApproval = removeAllRejectedTransactionsFrom(minerIdentity, selectedTxs as MutableList<Transaction>) //VERY IMPORTANT LINE
            merkleRootOfSelectedTxs = MerkleTree(*selectedTxs.map { it.hash }.toTypedArray()).getRoot()
            squashRequestedWithBlockToProof = requestSquash()
            currentNonce = 0

            recalculateDifficulty()

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
                    proofToSolve[0] = if(squashRequestedWithBlockToProof) 1 else 0
                    System.arraycopy(minerIdentity.raw, 0, proofToSolve, 5, minerIdentity.size)

                BitHelper.writeInt32(proofToSolve, 1, currentNonce)

                val solve = Hash(proofToSolve)
                val accepted = verifySolveAttempt(solve.raw, difficulty)

                if(accepted) {
                    val proofBuilder = proofToSolve + solve.raw
                    val proof = Proof(proofBuilder)

                    createAndAddLocalBlock(newSquashStateInCaseOfApproval, selectedTxs, latestBlockHash, proof, squashRequestedWithBlockToProof, merkleRootOfSelectedTxs)

                    reselectTxsToBuild()
                } else
                    currentNonce++
            }
        }
    }

    private fun verifySolveAttempt(solve: ByteArray, difficulty: Int): Boolean {
        for(i in 0..difficulty)
            if(solve[i] != 0.toByte())
                return false
        return true
    }

    final override fun notifyNewLatestBlock(newBlock: Block) {
        reselectTxsToBuild()
    }
    final override fun notifyNewTransactionInMemPool(newTx: Transaction) {
        lock.withLock {
            if(selectedTxs.size < 8) {
                reselectTxsToBuild()
            }
        }
    }

    final override fun extractRequestSquashFromProof(proof: Proof) = proof[0] == 1.toByte()
    final override fun extractBlockCreatorIdentityFromProof(proof: Proof) = ImmutableByteArray(proof.raw.copyOfRange(5, proof.size - 20))
    final override fun getLocalIdentity() = minerIdentity
    final override fun validateProof(receivedBlock: Block): Boolean {
        val proofToValidate = receivedBlock.proof
        val givenSolve = ByteArray(Hash.length())
        System.arraycopy(proofToValidate.raw, proofToValidate.raw.size-givenSolve.size, givenSolve, 0, givenSolve.size)

        return verifySolveAttempt(givenSolve, difficulty)
    }
}

class SimpleProofOfWorkConsensusCreator(private val difficulty: Int, private val minerIdentity: ImmutableByteArray) : ConsensusAlgorithmCreator {
    override fun create(instance: Mockchain) = ProofOfWorkConsensus(instance, difficulty, minerIdentity)
}