package jokrey.mockchain

import jokrey.mockchain.application.Application
import jokrey.mockchain.consensus.ConsensusAlgorithm
import jokrey.mockchain.consensus.ConsensusAlgorithmCreator
import jokrey.mockchain.consensus.ManualConsensusAlgorithmCreator
import jokrey.mockchain.storage_classes.*

/**
 * Blockchain implementation with no network. I.e. a mockchain.
 * It mocks certain application relevant blockchain behaviour such as transactions and blocks.
 *
 * Can be used to tests applications. The visualization engine can be used to display and simulate and evolving application state as represented in the blockchain.
 *
 * The default consensus algorithm is manual and local. I.e. consensus rounds are initiated by simulation or the user and there is no 'proof' of authorization.
 * However the consensus algorithm already includes the squash feature that allows applications to selectively minimize their data usage after the fact.
 */

class Mockchain {
    private val app: Application
    val consensus: ConsensusAlgorithm
    val memPool = MemPool()
    val chain: Chain
    constructor(app: Application, consensusAlgorithm: ConsensusAlgorithmCreator = ManualConsensusAlgorithmCreator()) {
        this.app = app
        this.chain = Chain(app)
        this.consensus = consensusAlgorithm.create(app, chain, memPool)
    }
    constructor(app: Application, store: StorageModel, consensusAlgorithm: ConsensusAlgorithmCreator = ManualConsensusAlgorithmCreator()) {
        this.app = app
        this.chain = Chain(app, store)
        this.consensus = consensusAlgorithm.create(app, chain, memPool)
    }

    /**
     * Commits the given transaction to the internal Mempool.
     * If the hash of the transaction is known to the chain it will be rejected and the application will be notified.
     *
     * The given transaction has to have an unset blockId, i.e. one that is smaller than 0. Otherwise the chain could not override that field.
     */
    fun commitToMemPool(tx: Transaction) {
        if(tx.hash in chain || tx.hash in memPool) {
            // - this 'fix' does not work in a distributed environment, it does! txs are guaranteed to be equal, memPool should also be synchronized
            app.txRejected(chain, tx.hash, tx, RejectionReason.PRE_MEM_POOL("hash(${tx.hash} already known to the chain - try adding a timestamp field"))
            throw IllegalArgumentException("hash already known to chain this is illegal for now, due to the hash uniqueness problem - tx: $tx")
        }
        if(tx.blockId >= 0)
            throw IllegalArgumentException("block id is not decided by application. chain retains that sovereignty")
        LOG.info("tx committed to mem pool = $tx")
        memPool[tx.hash] = tx

//        node?.broadcastTx(tx)
    }

//    fun performConsensus()



    /**
     * Returns true if the hash is known to the chain, false if it is not resolvable
     */
    operator fun contains(aHash: TransactionHash) = memPool.contains(aHash) || chain.isPersisted(aHash)

    /**
     * Adds the current size of the permanent storage and the Mempool to roughly calculate the current size of the chain
     */
    fun calculateStorageRequirementsInBytes() : Long {
        return chain.calculateStorageRequirementsInBytes() +
                memPool.byteSize()
    }
}




/**
 * Function to create a chain after a shutdown or crash.
 * Loads the given storage model into a newly created chain and applies a replay to the given, freshly created application.
 */
fun chainFromExistingData(freshApp: Application, store: StorageModel): Mockchain {
    val node = Mockchain(freshApp, store)
    node.chain.applyReplayTo(freshApp)
    return node
}