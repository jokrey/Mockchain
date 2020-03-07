package jokrey.mockchain

import jokrey.mockchain.application.Application
import jokrey.mockchain.consensus.ConsensusAlgorithmCreator
import jokrey.mockchain.consensus.SimpleProofOfWorkConsensusCreator
import jokrey.mockchain.network.ChainNode
import jokrey.mockchain.storage_classes.*
import jokrey.utilities.network.link2peer.P2LNode
import jokrey.utilities.network.link2peer.P2Link
import java.lang.IllegalArgumentException
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write


/**
 * mockchain, but with proper consensus and network. So it is not really a 'mock'chain anymore..
 * more 'not a mockchain' or nockchain.. I know it sucks as a name, but what do you have? A better one. Whoo.., I doubt it.
 *
 * Should be used exactly like the Mockchain. To the user it is only extended by the connect methods that allow connecting to peers by a link.
 * Additionally it is discouraged to use the ManualConsensusAlgorithm.
 */
class Nockchain(app: Application,
                val selfLink: P2Link,
                store: StorageModel = NonPersistentStorage(),
                consensus: ConsensusAlgorithmCreator = SimpleProofOfWorkConsensusCreator(5, selfLink.bytesRepresentation)) : Mockchain(app, store, consensus) {
    val blockRecorder = BlockRecorder(this)
    internal val node = ChainNode(selfLink, 10, this)

    /** @see P2LNode.establishConnections */
    fun connect(vararg links: P2Link, catchup: Boolean = false) {
        if(links.isEmpty()) return
        node.connect(*links)

        if(catchup)
            node.catchMeUpTo(*links)
    }
    /** @see P2LNode.recursiveGarnerConnections */
    fun recursiveConnect(connectionLimit:Int, vararg links: P2Link, catchup: Boolean = false) {
        val successfulConnects = node.recursiveConnect(connectionLimit, *links)

        if(catchup)
            node.catchMeUpTo(*successfulConnects.toTypedArray())
    }

    override fun commitToMemPool(tx: Transaction, local: Boolean) {
        super.commitToMemPool(tx, local)

        if(local)
            node.broadcastTx(tx)
    }

    override fun notifyNewLocalBlockAdded(block: Block) {
        super.notifyNewLocalBlockAdded(block)
        node.relayValidBlock(block)

        log("nockchain - new local block added = $block")
    }
    fun notifyNewRemoteBlockAdded(block: Block) {
        super.notifyNewLocalBlockAdded(block)

        log("nockchain - new remote block added = $block")
    }


    internal var isInPausedRecordMode: Boolean = false
    private val rwLock = ReentrantReadWriteLock()
    fun<T> requireNonPaused(action: ()->T) = rwLock.read { action() }
    /**
     * Pauses: consensus algorithm, chain node block receival(recorded)
     */
    fun<T> pauseAndRecord(action: ()->T) = rwLock.write {
        consensus.pause()
        isInPausedRecordMode = true

        try {
            action()
        } finally {
            blockRecorder.applyRecordedBlocks()

            consensus.resume()
            isInPausedRecordMode = false
        }
    }

    override fun log(s: String) {
        System.err.println("$selfLink - $s")
    }

    override fun close() {
        super.close()
        node.p2lNode.close()
    }
}