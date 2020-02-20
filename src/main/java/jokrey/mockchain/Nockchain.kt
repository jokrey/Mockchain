package jokrey.mockchain

import jokrey.mockchain.application.Application
import jokrey.mockchain.consensus.ConsensusAlgorithmCreator
import jokrey.mockchain.consensus.SimpleProofOfWorkConsensusCreator
import jokrey.mockchain.network.ChainNode
import jokrey.mockchain.storage_classes.Block
import jokrey.mockchain.storage_classes.NonPersistentStorage
import jokrey.mockchain.storage_classes.StorageModel
import jokrey.mockchain.storage_classes.Transaction
import jokrey.utilities.network.link2peer.P2LNode
import jokrey.utilities.network.link2peer.P2Link


/**
 * mockchain, but with proper consensus and network. So it is not really a 'mock'chain anymore..
 * more 'not a mockchain' or nockchain.. I know it sucks as a name, but what do you have? A better one. Whoo.., I doubt it.
 *
 * Should be used exactly like the Mockchain. To the user it is only extended by the connect methods that allow connecting to peers by a link.
 * Additionally it is discouraged to use the ManualConsensusAlgorithm.
 *
 * TODO - missing fork and catch up algorithm. I.e. the chain currently has to be synchronous between all nodes - adding new nodes on the fly is not possible.
 */
class Nockchain(app: Application,
                val selfLink: P2Link,
                store: StorageModel = NonPersistentStorage(),
                consensus: ConsensusAlgorithmCreator = SimpleProofOfWorkConsensusCreator(5, selfLink.bytesRepresentation)) : Mockchain(app, store, consensus) {
    internal val node = ChainNode(selfLink, 10, this)

    /** @see P2LNode.establishConnections */
    fun connect(vararg links: P2Link, catchup: Boolean = false) {
        node.connect(*links)
    }
    /** @see P2LNode.recursiveGarnerConnections */
    fun recursiveConnect(connectionLimit:Int, vararg links: P2Link, catchup: Boolean = false) {
        node.recursiveConnect(connectionLimit, *links)
    }

    override fun commitToMemPool(tx: Transaction, local: Boolean) {
        super.commitToMemPool(tx, local)

        if(local)
            node.broadcastTx(tx)
    }

    override fun notifyNewLocalBlockAdded(block: Block) {
        super.notifyNewLocalBlockAdded(block)
        node.relayValidBlock(block)
    }
    fun notifyNewRemoteBlockAdded(block: Block) {
        super.notifyNewLocalBlockAdded(block)
    }


    override fun log(s: String) {
        System.err.println("$selfLink - $s")
    }
}