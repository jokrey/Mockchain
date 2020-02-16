package jokrey.mockchain.network

import jokrey.mockchain.Mockchain
import jokrey.mockchain.storage_classes.*
import jokrey.utilities.network.link2peer.P2LMessage
import jokrey.utilities.network.link2peer.P2LNode
import jokrey.utilities.network.link2peer.P2Link
import jokrey.utilities.network.link2peer.node.core.ConversationAnswererChangeThisName
import jokrey.utilities.network.link2peer.util.P2LThreadPool
import jokrey.utilities.network.link2peer.util.TimeoutException
import java.net.SocketAddress

private const val TX_BROADCAST_TYPE:Short = 1
private const val NEW_BLOCK_TYPE:Int = 2
private const val TX_REQUEST:Int = 3

/**
 * This class is the connection to the peer to peer network.
 * It supplies consensus, mem pool and chain with remote input and broadcasts transactions and blocks.
 *
 * Its usage is purely internal. Only connecting requires user input, please use the wrapper methods for that.
 */
internal class ChainNode(selfLink: P2Link, peerLimit:Int,
                private val instance: Mockchain) {
    private val p2lNode = P2LNode.create(selfLink, peerLimit)
    private val pool = P2LThreadPool(4, 32)

    /** @see P2LNode.establishConnections */
    internal fun connect(vararg links: P2Link) {
        p2lNode.establishConnections(*links).waitForIt()
    }
    /** @see P2LNode.recursiveGarnerConnections */
    internal fun recursiveConnect(connectionLimit: Int, vararg links: P2Link) {
        p2lNode.recursiveGarnerConnections(connectionLimit, *links)
    }

    init {
        p2lNode.addBroadcastListener {
            when(it.header.type) {
                TX_BROADCAST_TYPE -> {
                    val receivedTx = Transaction.decode(it.asBytes())
                    instance.log("received tx = $receivedTx - from: ${it.header.sender}")
                    instance.commitToMemPool(receivedTx, false)
                }
            }
        }

        val newBlockConversation = ConversationAnswererChangeThisName { convo, m0 ->
            convo.close() //todo - instead only request header for now answer that proof and previous hash are ok, then request entire block
            val receivedBlock = Block(m0.asBytes())

            val proofValid = instance.consensus.validateProof(receivedBlock)
            if(! proofValid) return@ConversationAnswererChangeThisName

            val latestLocalBlockHash = instance.chain.getLatestHash()
            if(latestLocalBlockHash != receivedBlock.previousBlockHash) //todo - allow forks! - allow catch up!
                return@ConversationAnswererChangeThisName

            //ensure all tx available (i.e. in own mempool)
            val numberOfUnavailableTransactions = pool.executeThreadedCounter(
                receivedBlock.filter { it !in instance.memPool }.map {txp ->
                    P2LThreadPool.ProvidingTask {
                        try {
                            val nowAvailable = requestTxFrom(txp, convo.peer/*p2lNode.establishedConnections.random()*/)
                            !nowAvailable
                        } catch (t: Throwable) {
                            t.printStackTrace()
                            true
                        }
                    }
                }
            )
            if(numberOfUnavailableTransactions.get() != 0) return@ConversationAnswererChangeThisName

            val allTxValid = instance.consensus.attemptVerifyAndAddRemoteBlock(receivedBlock)
            if(! allTxValid) return@ConversationAnswererChangeThisName

            instance.notifyNewLocalBlockAdded(receivedBlock)
        }

        p2lNode.registerConversationFor(NEW_BLOCK_TYPE, newBlockConversation)

        p2lNode.registerConversationFor(TX_REQUEST) { convo, m0 ->
            val requestedTxp = TransactionHash(Hash(m0.asBytes(), true))
            val queriedTx = instance.memPool.getUnsure(requestedTxp)
            if(queriedTx == null)
                convo.answerClose(ByteArray(0))
            else
               convo.answerClose(queriedTx.encode())
        }
    }

    internal fun relayValidBlock(block: Block) {
        pool.execute(p2lNode.establishedConnections.map { peer ->
            P2LThreadPool.Task {
                val convo = p2lNode.convo(NEW_BLOCK_TYPE, peer)
                convo.setMaxAttempts(3)

                val encoded = block.encode()
                convo.initClose(encoded)
            }
        })
    }

    private fun requestTxFrom(txp: TransactionHash, vararg links: SocketAddress) : Boolean {
        for(link in links) {
            try {
                val convo = p2lNode.convo(TX_REQUEST, link)
                val receivedRaw = convo.initExpectClose(txp.raw)
                if(receivedRaw.isEmpty()) return false
                val receivedTx = Transaction.decode(receivedRaw)
                instance.memPool[txp] = receivedTx
                return true
            } catch (e: TimeoutException) {
                return false
            }
        }
        return false
    }

    private var txBroadcastCounter = 0
    internal fun broadcastTx(tx: Transaction) {
        val numConnected = p2lNode.establishedConnections.size
        val receipts = p2lNode.sendBroadcastWithReceipts(P2LMessage.Factory.createBroadcast(p2lNode.selfLink, TX_BROADCAST_TYPE, tx.encode()))

        txBroadcastCounter++
        instance.log("broadcast-memPool-tx(id:$txBroadcastCounter): $tx")
        receipts.callMeBack {
            instance.log("success counter for broadcast-memPool-tx(id:$txBroadcastCounter): $it/$numConnected")
        }
    }
}