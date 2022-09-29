package jokrey.mockchain.consensus

import jokrey.mockchain.Mockchain
import jokrey.mockchain.storage_classes.Block
import jokrey.mockchain.storage_classes.Hash
import jokrey.mockchain.storage_classes.Proof
import jokrey.mockchain.storage_classes.Transaction
import jokrey.utilities.bitsandbytes.BitHelper
import java.util.*

/**
 * Manual consensus algorithm in which all transaction in the mem pool will be attempted to be added to the chain upon a manual, external command.
 * The proof is minimal and does not contain any validations in itself nor the identity of the proposer.
 *
 * Should generally only be used for simple testing in a mockchain environment.
 *
 * @author jokrey
 */
class ManualConsensusAlgorithm(instance: Mockchain, var squashEveryNRounds: Int = -1, var consensusEveryNTick:Int = 5) : ConsensusAlgorithm(instance) {
    private var roundCounter = 1

    /**
     * Manual command to initiate proposing a new block.
     */
    fun performConsensusRound(requestSquashNumManually: Int) {
        if(isPaused) return

        val requestSquashNum = if (squashEveryNRounds>0 && roundCounter % squashEveryNRounds == 0) -1 else requestSquashNumManually
        val memPoolTransactions = instance.memPool.getTransactions().toList()
        attemptCreateAndAddLocalBlock(memPoolTransactions, Proof(BitHelper.getBytes(requestSquashNum)), requestSquashNum = requestSquashNum)
        roundCounter++
    }

    fun performTick(tickCounter: Int) {
        if(isPaused) return

        if (consensusEveryNTick < 0) {
            if (Random().nextInt(-consensusEveryNTick) == 0)
                performConsensusRound(0)
        } else if ((tickCounter!=0 && tickCounter % consensusEveryNTick == 0) || consensusEveryNTick==1)
            performConsensusRound(0)
    }

    //allows usage in a network, though that, naturally, is discouraged and should be used with care even for testing
    override fun extractRequestSquashNumFromProof(proof: Proof) = BitHelper.getInt32From(proof.raw)
    override fun extractBlockCreatorIdentityFromProof(proof: Proof) = ByteArray(0)
    override fun getLocalIdentity() = ByteArray(0)
    override fun validateJustReceivedProof(proof: Proof, previousBlockHash: Hash?, merkleRoot: Hash)
            = proof.size == 4

    override fun allowFork(forkIndex: Int, ownBlockHeight: Int, remoteBlockHeight: Int) = true

    override fun run() {}
    override fun notifyNewLatestBlockPersisted(newBlock: Block) {}
    override fun notifyNewTransactionInMemPool(newTx: Transaction) {}

    override fun getCreator() = ManualConsensusAlgorithmCreator(squashEveryNRounds, consensusEveryNTick)
}
class ManualConsensusAlgorithmCreator(private val squashEveryNRounds: Int = -1, private val consensusEveryNTick: Int = Int.MAX_VALUE) : ConsensusAlgorithmCreator {
    override fun create(instance: Mockchain) = ManualConsensusAlgorithm(instance, squashEveryNRounds, consensusEveryNTick)

    override fun getEqualFreshCreator(): () -> ConsensusAlgorithmCreator = { ManualConsensusAlgorithmCreator(squashEveryNRounds, consensusEveryNTick) }
    override fun createNewInstance(vararg params: String) = ManualConsensusAlgorithmCreator(params[0].toInt(), params[1].toInt())
    override fun getCreatorParamNames() = arrayOf("squashEveryNRounds (int)", "consensusEveryNTick (int)")
    override fun getCurrentParamContentForEqualCreation() = arrayOf(squashEveryNRounds.toString(), consensusEveryNTick.toString())
}