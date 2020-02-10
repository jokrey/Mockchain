package jokrey.mockchain.consensus

import jokrey.mockchain.application.Application
import jokrey.mockchain.squash.SquashAlgorithmState
import jokrey.mockchain.storage_classes.*
import jokrey.utilities.debug_analysis_helper.AverageCallTimeMarker

/**
 * Will propose blocks to the chain based on some internal configuration from it's own thread.
 *
 * Will interface with chain(for storage), mempool(for querying txs) and application(for tx verification)
 *
 * Will receive data from the network (remotely proposed blocks)
 *
 * todo mempool must also receive data from the network
 */
abstract class ConsensusAlgorithm(private val app:Application,
                                  private val chain: Chain,
                                  private val memPool: MemPool) {

    internal var consensusRoundCounter = 0
    private var priorSquashState: SquashAlgorithmState? = null

    /**
     * Will commit the Mempool to the 'permanent' chain, under the verify and squash rules
     */
    open fun performConsensusRound(forceSquash: Boolean = false) {
        consensusRoundCounter++

        val proposedTransactions = memPool.getTransactions().toMutableList()

        for (tx in proposedTransactions)
            memPool.remove(tx.hash)

        //VERIFICATION
        //Verified by popular vote within application(s) - actual distributed consensus omitted from this prototype
        var newSquashState: SquashAlgorithmState? = null
        var rejectedTxCount: Int
        do {
            rejectedTxCount=0
            //app verify needs to be before squash verify - otherwise if a tx is rejected here it no longer exists, but squash verify did not know that
            AverageCallTimeMarker.mark_call_start("app verify")
            val appRejectedTransactions = app.verify(chain, *proposedTransactions.toTypedArray())
            proposedTransactions.removeAll(appRejectedTransactions.map { it.first })
            rejectedTxCount += appRejectedTransactions.size
            handleRejection(proposedTransactions, appRejectedTransactions)
            AverageCallTimeMarker.mark_call_end("app verify")

            if(newSquashState!=null && (proposedTransactions.isEmpty() || rejectedTxCount == 0))
                break // if app does not reject anything after a squash run, then squash is not required to be run again - because nothing changed

            AverageCallTimeMarker.mark_call_start("squash verify")
            newSquashState = jokrey.mockchain.squash.findChanges(chain, app.getPartialReplaceSquashHandler(), app.getBuildUponSquashHandler(), app.getSequenceSquashHandler(),
                    priorSquashState, proposedTransactions.toTypedArray())
            handleRejection(proposedTransactions, newSquashState.deniedTransactions)
            proposedTransactions.removeAll(newSquashState.deniedTransactions.map { it.first })
            rejectedTxCount += newSquashState.deniedTransactions.size
            AverageCallTimeMarker.mark_call_end("squash verify")
        } while(proposedTransactions.isNotEmpty() && (rejectedTxCount > 0 || newSquashState!!.deniedTransactions.isNotEmpty()))

        newSquashState as SquashAlgorithmState

        if (proposedTransactions.isEmpty()) {
            LOG.info("all transactions have been rejected - not creating a new block")
            assert(memPool.isEmpty())
        }

        chain.appendVerifiedNewBlock(forceSquash, newSquashState, proposedTransactions, Proof(ByteArray(0)))

        priorSquashState = newSquashState
    }

    private fun handleRejection(proposed: List<Transaction>, rejected: List<Pair<Transaction, RejectionReason>>) {
        for ((rejectedTransaction, reason) in rejected) {
            if(rejectedTransaction !in proposed) {
                if(rejectedTransaction.hash !in chain)
                    throw IllegalStateException("$reason rejected unknown transaction(hash=${rejectedTransaction.hash})")
                else
                    throw IllegalStateException("$reason rejected persisted transaction(hash=${rejectedTransaction.hash})")
            }
            memPool.remove(rejectedTransaction.hash)
            app.txRejected(chain, rejectedTransaction.hash, rejectedTransaction, reason)
            LOG.info("$reason rejected: $rejectedTransaction")
        }
    }

    abstract fun runConsensusLoop()

    abstract fun remoteBlockReceived(block: Block)
        //validate proof
        //validate all tx - if one tx is invalid -> entire block is rejected (possibly some internal penalty to the proposing party - if the proof in the block contains that info)
        // rebroadcast block



    /*
    Ways for a tx into the local chain:
    1.  ALL LOCAL
        - tx committed locally
        - tx added to local mempool
        - tx selected into staged block        /by local consensus instance
        - tx among verified in staged block    /by local consensus instance
        - tx in local proposed block
        (- local proposed block broadcast to peers
        - proposed block introduced into the local chain
    2.  REMOTE COMMIT - LOCAL PROPOSAL
        - tx committed remotely
        - tx received via mempool synchronization
        - tx added to local mempool
        --- ETC. SEE 1.
    3.  REMOTE COMMIT - REMOTE PROPOSAL
        - tx committed remotely
        - tx added to remote mempool
        - tx selected into staged block        /by remote consensus instance
        - tx among verified in staged block    /by remote consensus instance
        - tx in remote proposed block
        - remote proposed block broadcast to peers
        - remote proposed block received by local mockchain instance
        - proposed block proof verified        /by local consensus instance
        - ALL txs proposed of block verified   /by local consensus instance
        (- accepted block further broadcast to peers {OR DIRECTLY AFTER PROOF VERIFY FOR SPEED??}
        - proposed block introduced into the local chain
    4.  LOCAL COMMIT - REMOTE PROPOSAL
        - trivial
    FORK BLOCK RECEIVED NOT HANDLED YET....
     */
}

class ManualConsensusAlgorithm(app: Application, chain: Chain, memPool: MemPool, var squashEveryNRounds: Int = -1) : ConsensusAlgorithm(app, chain, memPool) {
    override fun performConsensusRound(forceSquash: Boolean) {
        super.performConsensusRound(forceSquash  || (squashEveryNRounds > 0 && consensusRoundCounter % squashEveryNRounds == 0))
    }
    override fun runConsensusLoop() {}
    override fun remoteBlockReceived(block: Block) {}
}
class ManualConsensusAlgorithmCreator(private val squashEveryNRounds: Int = -1) : ConsensusAlgorithmCreator {
    override fun create(app: Application, chain: Chain, memPool: MemPool) = ManualConsensusAlgorithm(app, chain, memPool, squashEveryNRounds)
}