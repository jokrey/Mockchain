package jokrey.mockchain.storage_classes

import jokrey.mockchain.Nockchain
import java.net.InetSocketAddress

class BlockRecorder(val instance: Nockchain, var maxBlocksToStore:Int = 200) {
    private val store = HashMap<Int, RecordedBlock>()
    private var latestBlockIdStored = -1

    fun applyRecordedBlocks() = synchronized(this) {
        try {
            for ((index, rb) in store.toSortedMap().entries) {
                if (index >= instance.chain.blockCount()) {
                    if (index > instance.chain.blockCount()) {
                        instance.node.catchMeUpTo(rb.from)
                        return@synchronized
                    } else {
                        addRecordedBlock(index, rb)
                    }
                } else {
                    //Do nothing - the block is behind
                }
            }
        } finally {
            store.clear()
        }
    }

    private fun addRecordedBlock(index:Int, rb: RecordedBlock) {
        val fallbacks = instance.node.p2lNode.establishedConnections.map { it.address }.filter { it != rb.from }.shuffled().toTypedArray()
        val txs = instance.node.queryAllTx(rb.b, instance.memPool, rb.from, *fallbacks)
        if (txs.size != rb.b.size) {
            store.clear()
            throw IllegalStateException("failed to query all tx for recorded block(=${rb.b}, from=${rb.from})")
        }

        val at = instance.consensus.attemptVerifyAndAddRemoteBlock(rb.b, txs.asTxResolver(), true)
        if(at == -1) throw IllegalStateException("could not add block - tx(s) rejected")
        else if(at != index) throw IllegalStateException("problem: actual added index($at) different to expected index($index)")
    }

    fun addNew(atHeight: Int, receivedBlock: Block, from: InetSocketAddress): Boolean = synchronized(this) {
        if (atHeight < instance.chain.blockCount())
            return false

        //todo - it would be potentially reasonable to start txRequests here and asynchronously tunnel them into a recorded mem-pool, up to a certain size of course

        val previous = store[atHeight]?.b
        return if (previous != null) {
            previous.getHeaderHash() == receivedBlock.getHeaderHash()
        } else {
            if((latestBlockIdStored == -1 || atHeight == latestBlockIdStored+1) &&
                    store.size + 1 <= maxBlocksToStore) {
                store[atHeight] = RecordedBlock(receivedBlock, from)
                true
            } else {
                false
            }
        }
    }

    data class RecordedBlock(val b: Block, val from : InetSocketAddress)
}
