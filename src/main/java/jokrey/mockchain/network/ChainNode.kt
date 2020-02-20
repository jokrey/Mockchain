package jokrey.mockchain.network

import jokrey.mockchain.Mockchain
import jokrey.mockchain.Nockchain
import jokrey.mockchain.storage_classes.*
import jokrey.utilities.network.link2peer.P2LMessage
import jokrey.utilities.network.link2peer.P2LNode
import jokrey.utilities.network.link2peer.P2Link
import jokrey.utilities.network.link2peer.node.core.ConversationAnswererChangeThisName
import jokrey.utilities.network.link2peer.util.P2LFuture
import jokrey.utilities.network.link2peer.util.P2LThreadPool
import jokrey.utilities.network.link2peer.util.TimeoutException
import java.net.SocketAddress
import java.util.concurrent.RejectedExecutionException

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
                private val instance: Nockchain) {
    internal val p2lNode = P2LNode.create(selfLink, peerLimit)
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

            instance.log("received unconfirmed block = $receivedBlock")

            val proofValid = instance.consensus.validateJustReceivedProof(receivedBlock)
            if(! proofValid) {
                instance.log("proof invalid, rejected block = $receivedBlock")
                return@ConversationAnswererChangeThisName
            }

            val latestLocalBlockHash = instance.chain.getLatestHash()
            if(latestLocalBlockHash != receivedBlock.previousBlockHash) {//todo - allow forks! - allow catch up!
                instance.log("latest block hash invalid, rejected block = $receivedBlock")
                return@ConversationAnswererChangeThisName
            }

            //ensure all tx available (i.e. in own mempool)
            val transactionQueries = pool.execute(
                receivedBlock.map {txp ->
                    P2LThreadPool.ProvidingTask {
                        try {
                            instance.memPool.getUnsure(txp) ?: requestTxFrom(txp, convo.peer/*p2lNode.establishedConnections.random()*/)
                        } catch (t: Throwable) {
                            t.printStackTrace()
                            null
                        }
                    }
                }
            )
            val queriedTransactions = P2LFuture.oneForAll(transactionQueries).get().filterNotNull()
            if(receivedBlock.size > queriedTransactions.size) {
                instance.log("failed to query x=${receivedBlock.size - queriedTransactions.size} missing txs, rejected block = $receivedBlock")
                return@ConversationAnswererChangeThisName
            }

            try {
                val allTxValid = instance.consensus.attemptVerifyAndAddRemoteBlock(receivedBlock, queriedTransactions.asTxResolver())
                if(! allTxValid) {
                    instance.log("not all tx of remote block valid, rejected block = $receivedBlock")
                    return@ConversationAnswererChangeThisName
                }

                instance.notifyNewRemoteBlockAdded(receivedBlock)
                relayValidBlock(receivedBlock, convo.peer)
            } catch (e: RejectedExecutionException) {
                instance.log("concurrent block added, rejected block = $receivedBlock")
            }

        }

        p2lNode.registerConversationFor(NEW_BLOCK_TYPE, newBlockConversation)

        p2lNode.registerConversationFor(TX_REQUEST) { convo, m0 ->
            val requestedTxp = TransactionHash(Hash(m0.asBytes(), true))
//            instance.log("received request for $requestedTxp")
            val queriedTx = instance.getUnsure(requestedTxp) //may already be persisted, so check chain too
//            instance.log("queriedTx = $queriedTx")
            if(queriedTx == null)
                convo.answerClose(ByteArray(0))
            else
               convo.answerClose(queriedTx.encode())
        }
    }

    internal fun relayValidBlock(block: Block, exception: SocketAddress? = null) {
        pool.executeThreadedSuccessCounter(p2lNode.establishedConnections.map { peer ->
            P2LThreadPool.Task {
                val to = peer.socketAddress
                if(to != exception) {
                    val convo = p2lNode.convo(NEW_BLOCK_TYPE, to)
                    convo.setMaxAttempts(3)

                    val encoded = block.encode()
                    convo.initClose(encoded)
                }
            }
        })
    }

    private fun requestTxFrom(txp: TransactionHash, vararg links: SocketAddress) : Transaction? {
        for(link in links) {
            try {
                val convo = p2lNode.convo(TX_REQUEST, link)
//                instance.log("requesting $txp from $link")
                val receivedRaw = convo.initExpectClose(txp.raw)
//                instance.log("receivedRaw = ${receivedRaw.toList()}")
                if(receivedRaw.isEmpty()) return null
                return Transaction.decode(receivedRaw, true)
            } catch (e: TimeoutException) {
                return null
            }
        }
        return null
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