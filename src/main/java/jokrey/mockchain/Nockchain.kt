package jokrey.mockchain

import jokrey.mockchain.application.Application
import jokrey.mockchain.consensus.ConsensusAlgorithmCreator
import jokrey.mockchain.consensus.SimpleProofOfWorkConsensusCreator
import jokrey.mockchain.network.ChainNode
import jokrey.mockchain.storage_classes.*
import jokrey.utilities.network.link2peer.P2LNode
import jokrey.utilities.network.link2peer.P2Link
import jokrey.utilities.network.link2peer.node.core.P2LConnection
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write


/**
 * mockchain, but with proper consensus and network. So it is not really a 'mock'chain anymore...
 * more 'not a mockchain' or nockchain... I know it sucks as a name, but what do you have? A better one. Whooo..., I doubt it.
 *
 * Should be used exactly like the Mockchain. To the user it is only extended by the connect methods that allow connecting to peers by a link.
 * Additionally, it is discouraged to use the ManualConsensusAlgorithm.
 *
 *
 * Not one chain to rule them all. For each problem a different chain.
 *   In certain(granted: rare) situations it can even be reasonable to run 2 chains from within the same program
 *   It can allow fine-grained control for txs throughput and security. I.e. different tx, different stakeholder(different security clearances, some vote on this some vote on that)
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
    val node: ChainNode
    val selfLink: P2Link

    fun connect(links: List<P2Link>, catchup: Boolean = false) {
        connect(links = *links.toTypedArray(), catchup = catchup)
    }
    /** @see P2LNode.establishConnections
     * */
    fun connect(vararg links: P2Link, catchup: Boolean = false): Boolean {
        if(links.isEmpty()) return false
        node.connect(*links)

        if(catchup)
            return node.catchMeUpTo(*links)
        return true
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


    private val rwLock = ReentrantReadWriteLock()
    fun<T> requireNonPaused(action: ()->T) = rwLock.read { action() }
    fun<T> requireNonPausedOr(action: ()->T, backupAction:()->T) : T {
        val rl = rwLock.readLock()
        return if(rl.tryLock()) {
            try {
                action()
            } finally {
                rl.unlock()
            }
        } else {
            backupAction()
        }
    }
    /**
     * Pauses: consensus algorithm, chain node block receival(recorded)
     */
    fun<T> pauseAndRecord(action: ()->T) = rwLock.write {
        consensus.pause()

        try {
            action()
        } finally {
            //todo this is a problem
            //applyRecordedBlocks can call this again, which causes overly long loops which do nothing good...
            blockRecorder.applyRecordedBlocks()

            consensus.resume()
        }
    }

    override fun log(s: String) {
        System.err.println("$selfLink - $s")
    }

    override fun close() {
        super.close()
        node.p2lNode.close()
    }

    fun getActiveConnections(): Array<P2LConnection> = node.p2lNode.establishedConnections
}