package jokrey.mockchain.network

import jokrey.mockchain.Nockchain
import jokrey.mockchain.storage_classes.*
import jokrey.utilities.encoder.as_union.li.bytes.MessageEncoder
import jokrey.utilities.network.link2peer.P2LMessage
import jokrey.utilities.network.link2peer.P2LNode
import jokrey.utilities.network.link2peer.P2Link
import jokrey.utilities.network.link2peer.node.core.ConversationAnswererChangeThisName
import jokrey.utilities.network.link2peer.node.core.P2LConversation
import jokrey.utilities.network.link2peer.util.P2LFuture
import jokrey.utilities.network.link2peer.util.P2LThreadPool
import jokrey.utilities.network.link2peer.util.TimeoutException
import java.lang.IllegalStateException
import java.lang.Integer.max
import java.lang.Integer.min
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

        p2lNode.registerConversationFor(TX_REQUEST) { convo, m0 ->
            val requestedTxp = TransactionHash(m0.asBytes(), true)
//            instance.log("received request for $requestedTxp")
            val queriedTx = instance.getUnsure(requestedTxp) //may already be persisted, so check chain too
//            instance.log("queriedTx = $queriedTx")
            if(queriedTx == null)
                convo.answerClose(ByteArray(0))
            else
                convo.answerClose(queriedTx.encode())
        }

        val newBlockConversation = ConversationAnswererChangeThisName { convo, m0 ->
//            convo.close()
//            val receivedBlock = Block(m0.asBytes())
//            val previousBlockHash = receivedBlock.previousBlockHash
//          TODO - potentially it should be up the consensus algorithm to penalize peers that frequently propose invalid blocks.
//               - though note that it is only possible to penalize signed blocks(i.e. blocks in which the creator is verifiably known)

//            println("m0 = ${m0.asBytes().toList()}")
            val newBlockHash = Hash.raw(m0.nextVariable())
            val previousBlockHashRaw = m0.nextVariable()
            val previousBlockHash = if(previousBlockHashRaw.isEmpty()) null else Hash.raw(previousBlockHashRaw)
            val newBlockHeight = m0.nextInt()
            instance.log("received unconfirmed block header part = (newBlockHash=$newBlockHash, previousBlockHash=$previousBlockHash, newBlockHeight=$newBlockHeight)")

            val latestLocalBlockHash = instance.chain.getLatestHash()
            if(latestLocalBlockHash != previousBlockHash) {
                val ownBlockHeight = instance.chain.blockCount()
                if(newBlockHeight < ownBlockHeight) {
                    instance.log("latest block hash differnt(fork or someone is behind), rejected block($newBlockHeight) hash= $newBlockHash")
                    convo.answerClose(byteArrayOf(BLOCK_HEIGHT_TOO_LOW))
                    return@ConversationAnswererChangeThisName
                } else {
                    forkProtocolClient(convo, newBlockHeight)
                    return@ConversationAnswererChangeThisName
                }
            }


            val m1_proof = convo.answerExpectMsg(byteArrayOf(CONTINUE))
            val proof = Proof(m1_proof.nextVariable())
            val merkleRoot = Hash.raw(m1_proof.nextVariable())

            val proofValid = instance.consensus.validateJustReceivedProof(proof, previousBlockHash, merkleRoot)
            if(! proofValid) {
                instance.log("proof invalid, rejected block($newBlockHeight) hash= $newBlockHash")
                convo.answerClose(byteArrayOf(PROOF_INVALID))
                return@ConversationAnswererChangeThisName
            }


            val m2_txps = convo.answerExpectMsg(byteArrayOf(CONTINUE))
            val txps = m2_txps.asBytes().split(Hash.length()).map { TransactionHash(it, true) }
//        todo    convo.pause() //confirm received latest message, but wait for me to send another now - which may take longer, essentially wait for me to compute.

            //ensure all tx available (i.e. in own mempool)
            val queriedTransactions = queryAllTx(txps, instance.memPool, convo.peer) //do not allow query from chain here.. potential dos exploitable performance leak
            if(txps.size > queriedTransactions.size) {
                instance.log("failed to query x=${txps.size - queriedTransactions.size} missing txs, rejected block($newBlockHeight) hash= $newBlockHash")
                convo.answerClose(byteArrayOf(COULD_NOT_QUERY_ALL_TXP_IN_BLOCK))
                return@ConversationAnswererChangeThisName
            }

            val receivedBlock = Block(previousBlockHash, proof, merkleRoot, txps.toTypedArray())

            try {
                val newBlockId = instance.consensus.attemptVerifyAndAddRemoteBlock(receivedBlock, queriedTransactions.asTxResolver())
                if(newBlockId == -1) {
                    instance.log("not all tx of remote block valid, rejected block = $receivedBlock")
                    convo.answerClose(byteArrayOf(TX_VERIFICATION_FAILED))
                    return@ConversationAnswererChangeThisName
                }

                convo.answerClose(byteArrayOf(SUCCESS_THANK_YOU))

                instance.notifyNewRemoteBlockAdded(receivedBlock)
                relayValidBlock(receivedBlock, convo.peer)
            } catch (e: RejectedExecutionException) {
                instance.log("concurrent block added, rejected block = $receivedBlock")
                convo.answerClose(byteArrayOf(CONCURRENT_BLOCK_ADDED))
            }

        }
        p2lNode.registerConversationFor(NEW_BLOCK_TYPE, newBlockConversation)
    }


    //BLOCK PROTOCOL CODES
    val BLOCK_HEIGHT_TOO_LOW = (-1).toByte()
    val PROOF_INVALID = (-2).toByte()
    val COULD_NOT_QUERY_ALL_TXP_IN_BLOCK = (-3).toByte()
    val TX_VERIFICATION_FAILED = (-4).toByte()
    val CONCURRENT_BLOCK_ADDED = (-5).toByte()
    val CONTINUE = 1.toByte()
    val SUCCESS_THANK_YOU = 2.toByte()
    val ACCEPT_FORK = (3).toByte()
    val FORK_CONTINUE = (31).toByte()
    val FORK_FOUND = (32).toByte()
    val FORK_NEXT_BLOCK_PLEASE = (33).toByte()
    val FORK_COMPLETE_THANKS = (34).toByte()
    val FORK_DENIED_BY_CONSENSUS = (-31).toByte()
    val FORK_DENIED_BY_TOO_FEW_BLOCKS = (-32).toByte()
    val FORK_DENIED_BY_UNKNOWN = (-33).toByte()

    internal fun relayValidBlock(block: Block, exception: SocketAddress? = null) {
        pool.executeThreadedSuccessCounter(p2lNode.establishedConnections.map { peer ->
            P2LThreadPool.Task {
                val to = peer.socketAddress
                if(to != exception) {
                    val convo = p2lNode.convo(NEW_BLOCK_TYPE, to)
                    var result = CONTINUE
                    convo.setMaxAttempts(3)
                    convo.setA(100)
//                    val encoded = block.encode()
//                    convo.initClose(encoded)

                    val newBlockHeight = instance.chain.blockCount()
                    val en_m0 = convo.encode(block.getHeaderHash().raw, block.previousBlockHash?.raw ?: byteArrayOf(), newBlockHeight)
//                    println("en_m0 = ${en_m0.asBytes().toList()}")
                    result = convo.initExpectMsg(en_m0).nextByte()
                    if(result == ACCEPT_FORK) {
                        forkProtocolServer(convo, newBlockHeight)
                        return@Task // do not continue relay of the newest block, fork should be already doing that..
                    } else if(result != CONTINUE) { handleBlockRelayError(result);return@Task }

                    result = convo.answerExpectMsg(convo.encode(block.proof.raw, block.merkleRoot.raw)).nextByte()
                    if(result != CONTINUE) { handleBlockRelayError(result);return@Task }

                    convo.setA(1000) //can take a while - even longer than this even, which is why we propose the next line amendment to the conversation protocol:
                                     //todo  with pause we receive a receipt for our message, but then continue to wait until a certain - much longer - timeout - until we receive the actual computed message
                    result = convo.answerExpectMsg/*AfterPause*/(block.map { it.raw }.spread(block.size, Hash.length())/*, timeout = 10000*/).nextByte()
                    if(result != SUCCESS_THANK_YOU) { handleBlockRelayError(result);return@Task }

                    //NO PROBLEM
                    convo.close()
                }
            }
        })
    }

    private fun forkProtocolClient(convo: P2LConversation, remoteBlockHeight: Int) {
        //after realising fork is required and remote chain is higher (preliminary accept based on incomplete data)

//        instance.pauseAndRecord() //todo pause functionality
        val ownBlockHeight = instance.chain.blockCount()  // requery after pausing the instance
        try {
            var hashChainQueryAnswerResult = convo.answerExpectMsg(byteArrayOf(ACCEPT_FORK))
            var remoteBlocksIndex = remoteBlockHeight

            while(remoteBlocksIndex>0) {
                val receivedBlockHashes = hashChainQueryAnswerResult.getContent().split(Hash.length()).map { Hash.raw(it) }
                remoteBlocksIndex -= receivedBlockHashes.size
                val forkIndex = findForkIndex(instance.chain, ownBlockHeight, receivedBlockHashes.asReversed(), remoteBlocksIndex, remoteBlockHeight)

                if(forkIndex < 0) {
                    hashChainQueryAnswerResult = convo.answerExpectMsg(byteArrayOf(FORK_CONTINUE))
                } else {
                    //todo - secondary check based on validated data
                    if(false) {
                        convo.answerClose(byteArrayOf(FORK_DENIED_BY_CONSENSUS))
                    } else {

                        //begin transfer of actual blocks, incrementally validate and query missing txs
                        var blockQueryResult = convo.answerExpect(convo.encode(FORK_FOUND, forkIndex))

                        instance.chain.store.deleteAllToBlockIndex(forkIndex)
                        instance.consensus.alertForkIncomingFrom(forkIndex)

                        for (i in forkIndex until remoteBlockHeight) {
                            if (blockQueryResult.isEmpty()) {
                                //todo - all blocks received - next phase
                                throw IllegalStateException("error not all blocks received")
                            } else {
                                val latestReceivedBlock = Block(blockQueryResult)

                                //todo - question: query missing tx asynchronously or as part of the protocol??
                                val txsInBlock = queryAllTx(latestReceivedBlock, instance.memPool.combineWith(instance.chain), convo.peer)

                                val newBlockId = instance.consensus.attemptVerifyAndAddRemoteBlockForked(latestReceivedBlock, txsInBlock.asTxResolver()) //forked version does not commit...
                                if(newBlockId != i) {
                                    instance.log("not all tx of remote block valid or wrong id(should be $i, was $newBlockId), rejected block = $latestReceivedBlock")
                                    convo.answerClose(byteArrayOf(TX_VERIFICATION_FAILED))
                                    throw IllegalStateException("error during fork - PENALIZE HEAVILY THROUGH CONSENSUS - BREAK CONNECTION WITH REMOTE ETC..")
                                }

                                if (forkIndex != remoteBlockHeight - 1) {
                                    blockQueryResult = convo.answerExpect(byteArrayOf(FORK_NEXT_BLOCK_PLEASE))
                                } else {
                                    convo.answerClose(byteArrayOf(FORK_COMPLETE_THANKS))

                                    instance.chain.store.cleanAllTxThatAreNotInBlocks() //done at the end, so that
                                    instance.chain.store.commit()

                                    return
                                }
                            }
                        }

                        convo.answerClose(byteArrayOf(FORK_DENIED_BY_TOO_FEW_BLOCKS))
                        throw IllegalStateException("should not occur, received too few blocks from remote")
                    }
                }
            }

            convo.answerClose(byteArrayOf(FORK_DENIED_BY_UNKNOWN))
            throw IllegalStateException("should not occur, fork index invalid or not found or else")
        } finally {
//            instance.resume()//todo pause functionality for mockchain
        }
    }

    private fun forkProtocolServer(convo: P2LConversation, blockCountUpTop: Int) {
        //after result was == ACCEPT_FORK
        var hashChainDownCounter = blockCountUpTop
        while(hashChainDownCounter >= 0) {
            val maxHashesTransferablePerPackage = convo.maxPayloadSizePerPackage / Hash.length()
            val hashesToTransferInThisPackage = max(hashChainDownCounter, maxHashesTransferablePerPackage)
            val encoder = MessageEncoder(convo.headerSize, convo.maxPayloadSizePerPackage)
            for (i in 1..hashesToTransferInThisPackage) {
                val blockHash = instance.chain.queryBlockHash(hashChainDownCounter)
                encoder.encodeFixed(blockHash.raw)
                hashChainDownCounter--
            }
            val resultMsg = convo.answerExpectMsg(encoder)
            var result = resultMsg.nextByte()
            if(result == FORK_CONTINUE) {
                continue
            } else if(result == FORK_FOUND) {
                val forkIndex = resultMsg.nextInt()
                convo.setA(1000) //other side will requery tx which can take a while - todo implement pause convo functionality on remote

                //start sending blocks from fork index until start block count (do not send too many blocks, they will have likely been recorded by the forked remote and would cause issues
                for(i in forkIndex until blockCountUpTop) {
                    val blockAtI = instance.chain.queryBlock(i)
                    result = convo.answerExpectMsg(blockAtI.encode()).nextByte()
                    if(result == FORK_NEXT_BLOCK_PLEASE) continue
                    else if(result == FORK_COMPLETE_THANKS) {
                        if(i == blockCountUpTop-1)
                            return
                        else
                            handleBlockRelayError(result) //not an error code, BUT TOO EARLY
                    } else handleBlockRelayError(result)
                }
            } else {
                handleBlockRelayError(result)
            }
        }



    }

    private fun handleBlockRelayError(errorCode: Byte) {
        //todo - NOTE THAT THIS IS IN A NON BLOCKING THREAD AND OTHER BLOCK RELAY'S MAY STILL CONTINUE CONCURRENTLY
        instance.log("error relaying block: code=$errorCode")
    }


    private fun queryAllTx(txps: Iterable<TransactionHash>, txResolver: TransactionResolver = instance.memPool, vararg allowedRemoteFallbacks: SocketAddress): List<Transaction> {
        val transactionQueries = pool.execute(
                txps.map {txp ->
                    P2LThreadPool.ProvidingTask {
                        try {
                            txResolver.getUnsure(txp) ?:
                                if(allowedRemoteFallbacks.isEmpty())
                                    null
                                else
                                    requestTxFrom(txp, allowedRemoteFallbacks.random())
                        } catch (t: Throwable) {
                            t.printStackTrace()
                            null
                        }
                    }
                }
        )
        return P2LFuture.oneForAll(transactionQueries).get().filterNotNull()
    }
    private fun requestTxFrom(txp: TransactionHash, link: SocketAddress) : Transaction? {
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

/**
 * 0..remoteBlocksIndex..receivedBlockHashes.length..remoteBlockHeight
 * 0..ownBlockHeight
 */
fun findForkIndex(chain: Chain, ownBlockHeight: Int, receivedBlockHashes: List<Hash>, remoteBlocksIndex: Int, remoteBlockHeight: Int): Int {
//    println("chainsHashes = [${chain.getBlocks().map { it.getHeaderHash() }}], ownBlockHeight = [${ownBlockHeight}], \nremoteHashes = [${receivedBlockHashes}], remoteBlocksIndex = [${remoteBlocksIndex}], remoteBlockHeight = [${remoteBlockHeight}]")
    if(remoteBlocksIndex >= ownBlockHeight) return -1 //fork point is not in given hashes (because it is out of range)

    val own = chain.queryBlockHash(remoteBlocksIndex)
    if(own != receivedBlockHashes[0])
        return -1 //fork point is not in given hashes, because the first hash already does not equal
    else {
        //YES; we already know the hash is in bounds, we just have to find it
        var stepSize = min(receivedBlockHashes.size, ownBlockHeight-remoteBlocksIndex)/2
        var current = stepSize
        while(true) {
//            println("current = ${current}")
//            println("stepSize = ${stepSize}")
            if(chain.queryBlockHash(min(remoteBlocksIndex + current, ownBlockHeight - 1)) == receivedBlockHashes[min(current, receivedBlockHashes.size-1)]) {
                if(stepSize<=1) {
//                    println("stepSize = ${stepSize}")
//                    println("current = ${current}")
//                    println("chain.queryBlockHash(remoteBlocksIndex + current) = ${chain.queryBlockHash(min(remoteBlocksIndex + current, ownBlockHeight - 1))}")
//                    println("receivedBlockHashes[current] = ${receivedBlockHashes[min(current, receivedBlockHashes.size-1)]}")

                    return min(remoteBlocksIndex + current, ownBlockHeight - 1)
                }
                current += stepSize
            } else {
                current -= stepSize
            }
            stepSize /= 2
        }
//        throw IllegalStateException("how was step size not 1?")
    }
}


private fun Iterable<ByteArray>.spread(partCount:Int, partLength: Int): ByteArray {
    val result = ByteArray(partCount * partLength)
    var counter = 0
    for(b in this) {
        System.arraycopy(b, 0, result, counter, b.size)
        counter += b.size
    }
    return result
}

private fun ByteArray.split(partLength: Int) : List<ByteArray> {
    val list = ArrayList<ByteArray>(this.size / partLength)
    for(i in this.indices step partLength) {
        val part = ByteArray(partLength)
        System.arraycopy(this, i, part, 0, partLength)
        list.add(part)
    }
    return list
}
