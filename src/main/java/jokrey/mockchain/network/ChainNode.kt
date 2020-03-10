package jokrey.mockchain.network

import jokrey.mockchain.Nockchain
import jokrey.mockchain.squash.SquashAlgorithmState
import jokrey.mockchain.storage_classes.*
import jokrey.utilities.encoder.as_union.li.bytes.MessageEncoder
import jokrey.utilities.network.link2peer.P2LMessage
import jokrey.utilities.network.link2peer.P2LNode
import jokrey.utilities.network.link2peer.P2Link
import jokrey.utilities.network.link2peer.node.core.P2LConversation
import jokrey.utilities.network.link2peer.util.P2LFuture
import jokrey.utilities.network.link2peer.util.P2LThreadPool
import jokrey.utilities.network.link2peer.util.TimeoutException
import java.lang.Exception
import java.lang.IllegalStateException
import java.lang.Integer.min
import java.net.SocketAddress
import java.util.concurrent.RejectedExecutionException

private const val TX_BROADCAST_TYPE:Short = 1
private const val NEW_BLOCK_TYPE:Int = 2
private const val TX_REQUEST:Int = 3
private const val CATCH_ME_UP_IF_YOU_CAN: Int = 4

/**
 * This class is the connection to the peer to peer network.
 * It supplies consensus, mem pool and chain with remote input and broadcasts transactions and blocks.
 *
 * Its usage is purely internal. Only connecting requires user input, please use the wrapper methods for that.
 */
internal class ChainNode(selfLink: P2Link, peerLimit:Int,
                private val instance: Nockchain) {
    internal val p2lNode = P2LNode.create(selfLink, peerLimit)
    private val pool = P2LThreadPool(32, 64)

    /** @see P2LNode.establishConnections */
    internal fun connect(vararg links: P2Link) {
        p2lNode.establishConnections(*links).waitForIt()
    }
    /** @see P2LNode.recursiveGarnerConnections */
    internal fun recursiveConnect(connectionLimit: Int, vararg links: P2Link) =
        p2lNode.recursiveGarnerConnections(connectionLimit, *links)

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
                convo.closeWith(ByteArray(0))
            else
                convo.closeWith(queriedTx.encode())
        }

        p2lNode.registerConversationFor(NEW_BLOCK_TYPE, this::newBlockConversation_server_init)
        p2lNode.registerConversationFor(CATCH_ME_UP_IF_YOU_CAN, this::catchMeUp_server_init)
    }

    internal fun relayValidBlock(block: Block, exception: SocketAddress? = null) {
        pool.executeThreadedSuccessCounter(p2lNode.establishedConnections.map { peer ->
            P2LThreadPool.Task {
                val to = peer.socketAddress
                if(to != exception) {
                    newBlockConversation_client_init(to, block)
                }
            }
        })
    }



    private fun handleBlockRelayError(errorCode: Byte) {
        //NOTE THAT THIS IS IN A NON BLOCKING THREAD AND OTHER BLOCK RELAY'S MAY STILL CONTINUE CONCURRENTLY
        instance.log("error relaying block: code=$errorCode")
    }


    internal fun queryAllTx(txps: Iterable<TransactionHash>, txResolver: TransactionResolver = instance.memPool, vararg allowedRemoteFallbacks: SocketAddress): List<Transaction> {
//        val transactionQueries = pool.execute(
//                txps.map {txp ->
//                    P2LThreadPool.ProvidingTask {
//                        println("task started")
//                        try {
//                            txResolver.getUnsure(txp) ?:// do this outside of the pool execute!!!!
//                                if(allowedRemoteFallbacks.isEmpty())
//                                    null
//                                else
//                                    requestTxFrom(txp, allowedRemoteFallbacks.random())
//                        } catch (t: Throwable) {
//                            t.printStackTrace()
//                            null
//                        }
//                    }
//                }
//        )

        val transactionQueries = txps.map { txp ->
            val result = txResolver.getUnsure(txp)
            if(result != null)
                P2LFuture(result)
            else
                pool.execute(
                        P2LThreadPool.ProvidingTask {
                            try {
                                if(allowedRemoteFallbacks.isEmpty())
                                    null
                                else
                                    requestTxFrom(txp, allowedRemoteFallbacks.random())
                            } catch (t: Throwable) {
                                t.printStackTrace()
                                null
                            }
                        }
                )
        }

//        println("transactionQueries(${transactionQueries.size}) = ${transactionQueries}")
        val results = ArrayList<Transaction>(transactionQueries.size)
        for(t in transactionQueries) {
            val r = t.get()
            if(r!=null)
                results.add(r)
        }
//        val results = P2LFuture.oneForAll(transactionQueries).get()
//        println("results(${results.size}) = ${results}")
        return results
    }
    private fun requestTxFrom(txp: TransactionHash, link: SocketAddress) : Transaction? {
        try {
            val convo = p2lNode.convo(TX_REQUEST, link)
                instance.log("requesting $txp from $link")
            val receivedRaw = convo.initExpectDataClose(txp.raw)
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
//        instance.log("broadcast-memPool-tx(id:$txBroadcastCounter): $tx")
//        receipts.callMeBack {
//            instance.log("success counter for broadcast-memPool-tx(id:$txBroadcastCounter): $it/$numConnected")
//        }
    }


    /**
     * Will attempt to catchup from the given peers in that order until catchup was successful.
     * Internally uses the fork protocol, so the consensus algorithm will be asked if it allows the catchup, which can also include a fork
     */
    fun catchMeUpTo(vararg peers: P2Link) = catchMeUpTo(*peers.map {it.socketAddress}.toTypedArray())
    fun catchMeUpTo(vararg peers: SocketAddress) : Boolean {
        for(peer in peers) {
            val convo = p2lNode.convo(CATCH_ME_UP_IF_YOU_CAN, peer)
            val m0 = convo.initExpect(convo.encode(instance.chain.blockCount()))
            val result = m0.nextByte()
            val remoteBlockHeight = m0.nextInt()
            if (result == WILL_PROVIDE_CATCH_UP) {
                val m1 = convo.answerExpect(byteArrayOf(ACCEPT_FORK))
                try {
                    acceptFork_Protocol(convo, remoteBlockHeight, m1)
                    return true
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
        return false
    }

    private fun catchMeUp_server_init(convo: P2LConversation, m0: P2LMessage) {
        instance.requireNonPaused {
            val remoteBlockHeight = m0.nextInt()
            val ownBlockHeight = instance.chain.blockCount()
            if (instance.consensus.allowProvideCatchUpTo(convo.peer, ownBlockHeight, remoteBlockHeight)) {
                val result = convo.answerExpect(convo.encode(WILL_PROVIDE_CATCH_UP, ownBlockHeight)).nextByte()
                if(result == ACCEPT_FORK)
                    provideFork_Protocol(convo, instance.chain.blockCount())
                else
                    convo.close()
            } else {
                convo.answerClose(byteArrayOf(DENY_CATCH_UP))
            }
        }
    }


    //BLOCK PROTOCOL CODES
    val DENIED_YOU_ARE_BEHIND = (-1).toByte()
    val PROOF_INVALID = (-2).toByte()
    val COULD_NOT_QUERY_ALL_TXP_IN_BLOCK = (-3).toByte()
    val TX_VERIFICATION_FAILED = (-4).toByte()
    val CONCURRENT_BLOCK_ADDED = (-5).toByte()
    val ACTUAL_HASH_MISMATCH = (-6).toByte()
    val MERKLE_ROOT_INVALID = (-7).toByte()
    val ACTUAl_HEIGHT_MISMATCH = (-8).toByte()
    val BLOCK_RECORDER_REJECTED = (-9).toByte()
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
    val CONTINUE_ALL_AT_ONCE = 4.toByte()
    val WILL_PROVIDE_CATCH_UP = 5.toByte()
    val DENY_CATCH_UP = (-51).toByte()

    private fun newBlockConversation_server_init(convo: P2LConversation, m0: P2LMessage) {
//        convo.close()
//        val receivedBlock = Block(m0.asBytes())
//        val previousBlockHash = receivedBlock.previousBlockHash

//          TO-DO - potentially it should be up the consensus algorithm to penalize peers that frequently propose invalid blocks.
//               - though note that it is only possible to penalize signed blocks(i.e. blocks in which the creator is verifiably known)

//        println("m0 = ${m0.asBytes().toList()}")
        val newBlockHash = Hash.raw(m0.nextVariable())
        val previousBlockHashRaw = m0.nextVariable()
        val previousBlockHash = if (previousBlockHashRaw.isEmpty()) null else Hash.raw(previousBlockHashRaw)
        val newBlockHeight = m0.nextInt()
        instance.log("received unconfirmed block header part = (newBlockHash=$newBlockHash, previousBlockHash=$previousBlockHash, newBlockHeight=$newBlockHeight)")

        if (instance.isInPausedRecordMode) {
            val m1 = convo.answerExpect(byteArrayOf(CONTINUE_ALL_AT_ONCE))
            newBlockConversation_server_pathRecordMode(convo, newBlockHash, previousBlockHash, newBlockHeight, m1)
        } else {
            instance.requireNonPaused {
                val latestLocalBlockHash = instance.chain.getLatestHash()
                if (latestLocalBlockHash != previousBlockHash) {
                    instance.log("latest block hash different(fork or someone is behind)")
                    val ownBlockHeight = instance.chain.blockCount()
                    if (newBlockHeight <= ownBlockHeight) {
                        instance.log("rejected block($newBlockHeight) hash= $newBlockHash, remote is behind(or height is $ownBlockHeight)")
                        convo.answerClose(byteArrayOf(DENIED_YOU_ARE_BEHIND))
                    } else {
                        instance.log("remote submitted a block ahead(ourHeight=$ownBlockHeight, remoteHeight=$newBlockHeight). Beginning fork protocol")

                        val m1 = convo.answerExpect(byteArrayOf(ACCEPT_FORK))
                        acceptFork_Protocol(convo, newBlockHeight, m1)
                    }
                } else {
                    val m1 = convo.answerExpect(byteArrayOf(CONTINUE))
                    newBlockConversation_server_default(convo, newBlockHash, previousBlockHash, newBlockHeight, m1)
                }
            }
        }
    }

    private fun newBlockConversation_client_init(to: SocketAddress, block: Block) {
        val convo = p2lNode.convo(NEW_BLOCK_TYPE, to)
        var result = CONTINUE
        convo.setMaxAttempts(3)
        convo.setA(100)
//        val encoded = block.encode()
//        convo.initClose(encoded)

        val newBlockHeight = instance.chain.blockCount()
        val en_m0 = convo.encode(block.getHeaderHash().raw, block.previousBlockHash?.raw ?: byteArrayOf(), newBlockHeight)
//        println("en_m0 = ${en_m0.asBytes().toList()}")
        result = convo.initExpect(en_m0).nextByte()
        when (result) {
            ACCEPT_FORK -> provideFork_Protocol(convo, newBlockHeight)
            CONTINUE -> newBlockConversation_client_default(convo, block)
            CONTINUE_ALL_AT_ONCE -> newBlockConversation_client_blockInOne(convo, block)
            else -> handleBlockRelayError(result)
        }
    }


    private fun newBlockConversation_server_pathRecordMode(convo: P2LConversation, newBlockHash: Hash, previousBlockHash: Hash?, newBlockHeight: Int, m1: P2LMessage) {
        val proof = Proof(m1.nextVariable())
        val txps = m1.nextVariable().split(Hash.length()).map { TransactionHash(it, true) }
        val receivedBlock = Block(previousBlockHash, proof, txps.toTypedArray())
        if(receivedBlock.getHeaderHash() != newBlockHash) {
            instance.log("given block hash does not match actual block hash, rejected block = $receivedBlock")
            convo.answerClose(byteArrayOf(ACTUAL_HASH_MISMATCH))
        } else {
            if(instance.blockRecorder.addNew(newBlockHeight, receivedBlock, convo.peer))
                convo.answerClose(byteArrayOf(SUCCESS_THANK_YOU))
            else {
                instance.log("block recorder rejected given block, rejected block = $receivedBlock")
                convo.answerClose(byteArrayOf(BLOCK_RECORDER_REJECTED))
            }
        }
    }

    private fun newBlockConversation_client_blockInOne(convo: P2LConversation, block: Block) {
        val result = convo.answerExpect(convo.encode(block.proof.raw,(block.map { it.raw }.spread(block.size, Hash.length())))).nextByte()
        if(result == SUCCESS_THANK_YOU) {
            //NO PROBLEM
            convo.close()
        } else {
            convo.close()
            handleBlockRelayError(result)
        }
    }


    private fun acceptFork_Protocol(convo: P2LConversation, remoteBlockHeight: Int, m1: P2LMessage) {
        //after realising fork is required and remote chain is higher (preliminary accept based on incomplete data)

        instance.pauseAndRecord {//also has to pause the consensus algorithm
            val ownBlockHeight = instance.chain.blockCount()  // requery after pausing the instance
            var hashChainQueryAnswerResult = m1
            var remoteBlocksIndex = remoteBlockHeight

            instance.log("begin accept fork: own=$ownBlockHeight, remote=$remoteBlockHeight")

            while (remoteBlocksIndex > 0) {
                val receivedBlockHashes = hashChainQueryAnswerResult.asBytes().split(Hash.length()).map { Hash.raw(it) }
                remoteBlocksIndex -= receivedBlockHashes.size
                val forkIndex = findForkIndex(instance.chain, ownBlockHeight, receivedBlockHashes.asReversed(), remoteBlocksIndex, remoteBlockHeight)

                instance.log("forkIndex = ${forkIndex}")

                if(ownBlockHeight >= remoteBlockHeight) {
                    convo.answerClose(byteArrayOf(DENIED_YOU_ARE_BEHIND))
                    throw IllegalStateException("consensus denied fork or ownHeightChanged since last check (concurrent fork detected and denied) (ownHeight=$ownBlockHeight, remoteHeight=$remoteBlockHeight)")
                }

                if (forkIndex < 0 && remoteBlocksIndex != 0) {
//                    println("acceptFork - waiting for hash chain result")
                    hashChainQueryAnswerResult = convo.answerExpect(byteArrayOf(FORK_CONTINUE))
                } else {
                    //forkIndex can be -1 here, which is fine if the chains are completely different
                    //-  secondary check based on validated data
                    if (!instance.consensus.allowFork(forkIndex, ownBlockHeight, remoteBlockHeight)) {
                        convo.answerClose(byteArrayOf(FORK_DENIED_BY_CONSENSUS))
                        throw IllegalStateException("consensus denied fork or ownHeightChanged since last check (concurrent fork detected and denied) (ownHeight=$ownBlockHeight, remoteHeight=$remoteBlockHeight)")
                    } else {

                        //begin transfer of actual blocks, incrementally validate and query missing txs
//                        println("acceptFork - waiting for block result")
                        var blockQueryResult = convo.answerExpectData(convo.encode(FORK_FOUND, forkIndex))

                        instance.chain.store.deleteAllToBlockIndex(forkIndex)
                        val forkApp = if(forkIndex+1 == ownBlockHeight) instance.app else instance.app.newEqualInstance()
                        instance.chain.applyReplayTo(forkApp, forkIndex)
                        var forkSquashState: SquashAlgorithmState? = null

                        for (i in forkIndex + 1 until remoteBlockHeight) {
                            val isFirst = i == (forkIndex + 1)
                            val isLast = i == (remoteBlockHeight - 1)
                            if (blockQueryResult.isEmpty()) {
                                instance.chain.store.cancelUncommittedChanges()
                                throw IllegalStateException("error not all blocks received")
                            } else {
                                val latestReceivedBlock = Block(blockQueryResult)

                                instance.log("latestReceivedBlock = ${latestReceivedBlock}")

                                //to-do - question: query missing tx asynchronously as demonstrated here or as part of the protocol?? - either can be beneficial depending on the size of the tx, async better for larger tx
                                val txsInBlock = queryAllTx(latestReceivedBlock, instance.memPool.combineWith(instance.chain), convo.peer)

                                val (newSquashState, newBlockId) = instance.consensus.attemptVerifyAndAddForkedBlock(latestReceivedBlock, i, txsInBlock.asTxResolver(), forkSquashState, forkApp, isFirst) //forked version does not commit...
                                forkSquashState = newSquashState
                                if (newBlockId == -1) {
                                    instance.log("adding fork block rejected, rejected block = $latestReceivedBlock")
                                    instance.chain.store.cancelUncommittedChanges()
                                    convo.answerClose(byteArrayOf(TX_VERIFICATION_FAILED))
                                    throw IllegalStateException("SIMULTANEOUS ACCESS LIKELY")
                                } else if (newBlockId != i) {
                                    instance.log("not all tx of remote block valid or wrong id(should be $i, was $newBlockId), rejected block = $latestReceivedBlock")
                                    instance.chain.store.cancelUncommittedChanges()
                                    convo.answerClose(byteArrayOf(TX_VERIFICATION_FAILED))
                                    throw IllegalStateException("error during fork - PENALIZE HEAVILY THROUGH CONSENSUS - BREAK CONNECTION WITH REMOTE ETC..")
                                }

                                if (!isLast) {
                                    blockQueryResult = convo.answerExpectData(byteArrayOf(FORK_NEXT_BLOCK_PLEASE))
                                } else {
                                    convo.answerClose(byteArrayOf(FORK_COMPLETE_THANKS))

                                    if(instance.app !== forkApp) {
//                                 todo   instance.app.cleanUpAndDie()
                                        instance.app = forkApp
                                    }
                                    instance.chain.store.commit()

                                    return@pauseAndRecord
                                }
                            }
                        }

                        instance.chain.store.cancelUncommittedChanges()
                        convo.answerClose(byteArrayOf(FORK_DENIED_BY_TOO_FEW_BLOCKS))
                        throw IllegalStateException("should not occur, received too few blocks from remote")
                    }
                }
            }

            convo.answerClose(byteArrayOf(FORK_DENIED_BY_UNKNOWN))
            throw IllegalStateException("should not occur, fork index invalid or not found or else")
        }
    }

    private fun provideFork_Protocol(convo: P2LConversation, blockCountUpTop: Int) {
        //after result was == ACCEPT_FORK
        var hashChainDownCounter = blockCountUpTop
        while(hashChainDownCounter >= 0) {
            val maxHashesTransferablePerPackage = convo.maxPayloadSizePerPackage / Hash.length()
            val hashesToTransferInThisPackage = min(hashChainDownCounter, maxHashesTransferablePerPackage)
            val encoder = MessageEncoder(convo.headerSize, convo.maxPayloadSizePerPackage)
//            println("maxHashesTransferablePerPackage = ${maxHashesTransferablePerPackage}")
//            println("hashesToTransferInThisPackage = ${hashesToTransferInThisPackage}")
            for (i in 1..hashesToTransferInThisPackage) {
//                println("hashChainDownCounter = ${hashChainDownCounter}")
                hashChainDownCounter--
                val blockHash = instance.chain.queryBlockHash(hashChainDownCounter)
                encoder.encodeFixed(blockHash.raw)
            }
//            println("provideFork - waiting for result")
            val resultMsg = convo.answerExpect(encoder)
            var result = resultMsg.nextByte()
//            println("provideFork - result=$result")
            if(result == FORK_CONTINUE) {
                continue
            } else if(result == FORK_FOUND) {
                val forkIndex = resultMsg.nextInt()
                convo.setA(10000) //other side will requery tx which can take a while - todo implement pause convo functionality on remote

                //start sending blocks from fork index until start block count (do not send too many blocks, they will have likely been recorded by the forked remote and would cause issues
                for(i in forkIndex+1 until blockCountUpTop) {
                    val blockAtI = instance.chain.queryBlock(i)
                    result = convo.answerExpect(blockAtI.encode()).nextByte()
                    if(result == FORK_NEXT_BLOCK_PLEASE) continue
                    else if(result == FORK_COMPLETE_THANKS) {
                        if(i == blockCountUpTop-1)
                            return
                        else {
                            convo.close()
                            handleBlockRelayError(result) //not an error code, BUT TOO EARLY
                            return
                        }
                    } else {
                        convo.close()
                        handleBlockRelayError(result)
                        return
                    }
                }
            } else {
                convo.close()
                handleBlockRelayError(result)
                return
            }
        }
    }



    private fun newBlockConversation_server_default(convo: P2LConversation, newBlockHash: Hash, previousBlockHash: Hash?, newBlockHeight: Int,
                                                    m1: P2LMessage) {
        val m1_proof = m1
        val proof = Proof(m1_proof.nextVariable())
        val merkleRoot = Hash.raw(m1_proof.nextVariable())

        val proofValid = instance.consensus.validateJustReceivedProof(proof, previousBlockHash, merkleRoot)
        if(! proofValid) {
            instance.log("proof invalid, rejected block($newBlockHeight) hash= $newBlockHash")
            convo.answerClose(byteArrayOf(PROOF_INVALID))
            return
        }

        instance.log("validated proof, downloading txps of remote block")


        val m2_txps = convo.answerExpect(byteArrayOf(CONTINUE))
        val txps = m2_txps.asBytes().split(Hash.length()).map { TransactionHash(it, true) }
//        todo    convo.pause() //confirm received latest message, but wait for me to send another now - which may take longer, essentially wait for me to compute.

        //ensure all tx available (i.e. in own mempool)
        val queriedTransactions = queryAllTx(txps, instance.memPool, convo.peer) //do not allow query from chain here.. potential dos exploitable performance leak
        if(txps.size > queriedTransactions.size) {
            instance.log("failed to query x=${txps.size - queriedTransactions.size} missing txs, rejected block($newBlockHeight) hash= $newBlockHash")
            convo.answerClose(byteArrayOf(COULD_NOT_QUERY_ALL_TXP_IN_BLOCK))
            return
        }

        val receivedBlock = Block(previousBlockHash, proof, merkleRoot, txps.toTypedArray())

        if(receivedBlock.getHeaderHash() != newBlockHash) {
            instance.log("given hash does not match actual hash, rejected block = $receivedBlock")
            convo.answerClose(byteArrayOf(ACTUAL_HASH_MISMATCH))
            return
        }
        if(! receivedBlock.validateMerkleRoot()) {
            instance.log("given merkle root does not match, rejected block = $receivedBlock")
            convo.answerClose(byteArrayOf(MERKLE_ROOT_INVALID))
            return
        }

        instance.log("all txs available attempting to add new block = $receivedBlock")

        try {
            val newBlockId = instance.consensus.attemptVerifyAndAddRemoteBlock(receivedBlock, queriedTransactions.asTxResolver())
            if(newBlockId == -1) {
                instance.log("not all tx of remote block valid, rejected block = $receivedBlock")
                convo.answerClose(byteArrayOf(TX_VERIFICATION_FAILED))
                return
            } else if(newBlockId != newBlockHeight-1) {
                instance.log("new block id($newBlockId) does not match initially reported remote height($newBlockHeight), rejected block = $receivedBlock")
                convo.answerClose(byteArrayOf(ACTUAl_HEIGHT_MISMATCH))
                return
            }

            convo.answerClose(byteArrayOf(SUCCESS_THANK_YOU))

            instance.notifyNewRemoteBlockAdded(receivedBlock)
            relayValidBlock(receivedBlock, convo.peer)
        } catch (e: RejectedExecutionException) {
            instance.log("concurrent block added, rejected block = $receivedBlock")
            convo.answerClose(byteArrayOf(CONCURRENT_BLOCK_ADDED))
        }
    }

    private fun newBlockConversation_client_default(convo: P2LConversation, block: Block) {
        var result = convo.answerExpect(convo.encode(block.proof.raw, block.merkleRoot.raw)).nextByte()
        if(result != CONTINUE) {
            convo.close()
            handleBlockRelayError(result)
        } else {
            convo.setA(5000) //can take a while - even longer than this even, which is why we propose the next line amendment to the conversation protocol:
            //todo  with pause we receive a receipt for our message, but then continue to wait until a certain - much longer - timeout - until we receive the actual computed message
            result = convo.answerExpect/*AfterPause*/(block.map { it.raw }.spread(block.size, Hash.length())/*, timeout = 10000*/).nextByte() //todo this will/might be too long... - built in convo functionality for long messages
            if (result == SUCCESS_THANK_YOU) {
                //NO PROBLEM
                convo.close()
            } else {
                convo.close()
                handleBlockRelayError(result)
            }
        }
    }
}

/**
 * 0..remoteBlocksIndex..receivedBlockHashes.length..remoteBlockHeight
 * 0..ownBlockHeight
 *
 * The fork index denotes the last index at which the two chains are equal
 */
fun findForkIndex(chain: Chain, ownBlockHeight: Int, receivedBlockHashes: List<Hash>, remoteBlocksIndex: Int, remoteBlockHeight: Int): Int {
    chain.instance.log("chainsHashes = [${chain.getBlocks().map { it.getHeaderHash() }}], ownBlockHeight = [${ownBlockHeight}], \nremoteHashes = [${receivedBlockHashes}], remoteBlocksIndex = [${remoteBlocksIndex}], remoteBlockHeight = [${remoteBlockHeight}]")
    if(remoteBlocksIndex >= ownBlockHeight) return -1 //fork point is not in given hashes (because it is out of range)

    val firstHashToCompare = chain.queryBlockHash(remoteBlocksIndex)
    if(firstHashToCompare != receivedBlockHashes[0]) {
        return -1 //fork point is not in given hashes, because the first hash already does not equal
    } else {
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
            if(stepSize>1)
                stepSize /= 2
            else
                stepSize = 1
        }
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

fun ByteArray.split(partLength: Int) : List<ByteArray> {
    val list = ArrayList<ByteArray>(this.size / partLength)
//    println("split - this(len=${this.size}) = ${this.toList()}")
//    println("partLength = ${partLength}")
    for(i in this.indices step partLength) {
        val part = ByteArray(partLength)
//        println("i = ${i}")
        System.arraycopy(this, i, part, 0, partLength)
        list.add(part)
    }
    return list
}
