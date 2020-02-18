package jokrey.mockchain.consensus

import jokrey.mockchain.Mockchain
import jokrey.mockchain.storage_classes.*

/**
 * Manual consensus algorithm in which all transaction in the mem pool will be attempted to be added to the chain upon a manual, external command.
 * The proof is minimal and does not contain any validations in itself nor the identity of the proposer.
 *
 * Should generally only be used for simple testing in a mockchain environment.
 *
 * @author jokrey
 */
class ManualConsensusAlgorithm(instance: Mockchain, var squashEveryNRounds: Int = -1) : ConsensusAlgorithm(instance) {
    /**
     * Manual command to initiate proposing a new block.
     */
    fun performConsensusRound(requestSquash: Boolean) {
        val proposedTransactions = instance.memPool.getTransactions().toMutableList()
        attemptCreateAndAddLocalBlock(proposedTransactions, Proof(byteArrayOf(if(requestSquash) 1 else 0)), requestSquash = requestSquash)
    }


    //allows usage in a network, though that, naturally, is discouraged and should be used with care even for testing
    override fun extractRequestSquashFromProof(proof: Proof) = proof[0] == 1.toByte()
    override fun extractBlockCreatorIdentityFromProof(proof: Proof) = ImmutableByteArray(ByteArray(0))
    override fun getLocalIdentity() = ImmutableByteArray(ByteArray(0))
    override fun validateJustReceivedProof(receivedBlock: Block)
            = receivedBlock.proof.size == 1 && (receivedBlock.proof[0] == 0.toByte() || receivedBlock.proof[0] == 1.toByte())

    override fun run() {}
    override fun notifyNewLatestBlockPersisted(newBlock: Block) {}
    override fun notifyNewTransactionInMemPool(newTx: Transaction) {}
}
class ManualConsensusAlgorithmCreator(private val squashEveryNRounds: Int = -1) : ConsensusAlgorithmCreator {
    override fun create(instance: Mockchain) = ManualConsensusAlgorithm(instance, squashEveryNRounds)
}