package jokrey.mockchain

import jokrey.mockchain.application.Application
import jokrey.mockchain.consensus.ConsensusAlgorithmCreator
import jokrey.mockchain.consensus.SimpleProofOfWorkConsensusCreator
import jokrey.mockchain.network.ChainNode
import jokrey.mockchain.storage_classes.*
import jokrey.utilities.network.link2peer.P2LNode
import jokrey.utilities.network.link2peer.P2Link
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write


/**
 * mockchain, but with proper consensus and network. So it is not really a 'mock'chain anymore..
 * more 'not a mockchain' or nockchain.. I know it sucks as a name, but what do you have? A better one. Whoo.., I doubt it.
 *
 * Should be used exactly like the Mockchain. To the user it is only extended by the connect methods that allow connecting to peers by a link.
 * Additionally it is discouraged to use the ManualConsensusAlgorithm.
 *
 *
 * Not one chain to rule them all. For each problem a different chain.
 *   In certain(granted: rare) situations it can even be reasonable to run 2 chains from within the same program
 *   It can allow fine grained control for txs throughput and security. I.e. different tx, different stakeholder(different security clearances, some vote on that some vote on that)
 *
 *
 * TODO -
 *   test
 *   test
 *   test
 *     (actually distributed testing - I know that is annoying but it should kinda, mostly work now and we need to verify that)
 */
class Nockchain : Mockchain {
    constructor(app: Application,
                p2lNode: P2LNode,
                store: StorageModel = NonPersistentStorage(),
                consensus: ConsensusAlgorithmCreator = SimpleProofOfWorkConsensusCreator(5, p2lNode.selfLink.toBytes())) : super(app, store, consensus) {
        node = ChainNode(p2lNode, this)
        this.selfLink = p2lNode.selfLink
    }
    constructor(app: Application,
                selfLink: P2Link,
                store: StorageModel = NonPersistentStorage(),
                consensus: ConsensusAlgorithmCreator = SimpleProofOfWorkConsensusCreator(5, selfLink.toBytes())) : super(app, store, consensus) {
        node = ChainNode(selfLink, 10, this)
        this.selfLink = selfLink
    }

    val blockRecorder = BlockRecorder(this)
    internal val node: ChainNode
    val selfLink: P2Link

    fun connect(links: List<P2Link>, catchup: Boolean = false) {
        connect(links = *links.toTypedArray(), catchup = catchup)
    }
    /** @see P2LNode.establishConnections */
    fun connect(vararg links: P2Link, catchup: Boolean = false) {
        if(links.isEmpty()) return
        node.connect(*links)

        if(catchup)
            node.catchMeUpTo(*links)
    }
    /** @see P2LNode.recursiveGarnerConnections */
    fun recursiveConnect(connectionLimit:Int, vararg links: P2Link, catchup: Boolean = false) {
        val successfulConnects = node.recursiveConnect(connectionLimit, *links).get(10000)

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