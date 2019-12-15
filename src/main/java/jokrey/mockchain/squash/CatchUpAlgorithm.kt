package jokrey.mockchain.squash

import jokrey.mockchain.application.Application
import jokrey.mockchain.storage_classes.*
import java.util.*

/**
 * TODO THIS IS ONLY AN OUTLINING PROTOTYPE
 */

//arg0 = block id, arg1 = tx index, return = tx at specified indices
typealias TxQuerier = (Int, Int) -> Transaction
//arg0 = block id, return = block-size at specified index
typealias BlockSizeQuerier = (Int) -> Int
//arg0 = block id, return = block at specified index
typealias BlockQuerier = (Int) -> Array<Transaction>

fun getRandomBlockSizeQuerier(queriers: List<Pair<TxQuerier, BlockSizeQuerier>>) =
        queriers[Random().nextInt(queriers.size)].second
fun getRandomTxQuerier(queriers: List<Pair<TxQuerier, BlockSizeQuerier>>) =
        queriers[Random().nextInt(queriers.size)].first
fun getRandomBlockQuerier(queriers: List<BlockQuerier>) =
        queriers[Random().nextInt(queriers.size)]


fun addBlockFrom(store: StorageModel, previousBlockHash: Hash?, blockTxs: Array<Transaction>) {
    for(btx in blockTxs)
        store[btx.hash] = btx
    store.add(Block(previousBlockHash, blockTxs.map { it.hash }.toTypedArray()))
}


/**
 * Algorithm assumes that no external changes to block store and tx store are done
 *
 * With these assumptions it is possible to even query tx from different sources, making it even more secure.
 *     Additionally there is even less load on individual queriers
 *
 * The for-loop can be parallelized
 *
 * @param newBlocksReceiver access to an asynchronous queue that caches the results of the consensus operations
 */
fun catchUpWithoutSquash(queriers: List<Pair<TxQuerier, BlockSizeQuerier>>, catchUpToBlockId: Int,
                         store: StorageModel, newBlocksReceiver: () -> List<Array<Transaction>>) {
    if(store.highestBlockId() >= catchUpToBlockId) return

    val newBlocks = ArrayList<Array<Transaction>>()

    var previousBlockHash: Hash? = null
    for(bi in store.highestBlockId() until catchUpToBlockId) {
        val queriedBlockSize = getRandomBlockSizeQuerier(queriers)(bi)

        val blockTxs = Array(queriedBlockSize) { txi -> getRandomTxQuerier(queriers)(bi, txi) }

        addBlockFrom(store, previousBlockHash, blockTxs)
        previousBlockHash = store.getLatestHash()

        newBlocks.addAll(newBlocksReceiver())
    }

    newBlocks.forEach { addBlockFrom(store, previousBlockHash, it) }

    //resume normal chain consensus operations
}


/**
 *
 *
 * @param newBlocksReceiver access to an asynchronous queue that caches the results of the consensus operations - has to contain every consensus result after catchUpToBlockId
 */
fun catchUpWithSquash(freshApp: Application, catchUpToBlockId: Int,
                      store: StorageModel,
                      queriers: List<BlockQuerier>, newBlocksReceiver: () -> List<Array<Transaction>>) {
    if(store.highestBlockId() >= catchUpToBlockId) return

    val newBlocks = ArrayList<Array<Transaction>>()

    var previousBlockHash: Hash? = null
    for(bi in store.highestBlockId() until catchUpToBlockId) {
            //: PROBLEM - at this point it cannot be assumed that bi is still valid - blocks can be deleted
            //   can be solved with not deleting, but just emptying block
        val queriedBlock = getRandomBlockQuerier(queriers)(bi)
        //      the block may then be altered on the querier, by dependencies that will end up in newBlocks
        //          i.e. the squash algorithm run in the end will do those same operation again
        //      the other option is that a received block is altered prior to being send
        //          i.e. newBlocks contains dependency-changes that have already been run on the data

//        val blockTxs = Array(queriedBlockSize) { KEEP COMMENT!!!!!!!!!
//            //TODO: PROBLEM 1 - at this point it cannot be assumed that bi is still valid
//            //TODO: PROBLEM 2 - at this point it cannot be assumed that queriedBlockSize is still correct - or that the indices correspond to different tx
//            //TODO: - even if using txp's (querying txps of block up top, then requesting a hash here) this causes a problem
//            //TODO: - in the meantime a txp may be deleted or, MUCH WORSE, added(but not visible from here - i.e. the result is missing data)
//
//            //TODO: IDEA: instead of querying tx lonely, query an entire block at once - if a querier receives such a request it reads the entire block, at once(!), and sends it
//            //          TODO: do a version of the squash algorithm that can handle this
//            txi -> getRandomTxQuerier(queriers)(bi, txi)
//        }

        //cannot assume that no changes have been made to past blocks - previousBlockHash may be wrong for that reason
        //    after a squash the hashes should be updated - and at that point a verify should be done
        //    previous hash is nonetheless updated, because it is correct for the first n unaltered blocks
        addBlockFrom(store, previousBlockHash, queriedBlock)
        previousBlockHash = store.getLatestHash()
    }

    newBlocks.addAll(newBlocksReceiver())

    newBlocks.forEach { addBlockFrom(store, previousBlockHash, it) }

    //todo run special, permissive squash algorithm (missing dependencies should simply be ignored - and the hash algorithm continue, even in
    //todo    a problem could be squash handling - since the blocks in blockStore may now contain equal hashes
    //todo        it is NOT possible to simply assume the earlier tx to be the valid/earlier hash - due to partial-replace


    val chain: Chain = chainFromExistingData(freshApp, store)
    val resultValid = chain.validateHashChain()
    if(!resultValid)
        throw IllegalStateException("could not gather a consistent state - try again")

    //from now chain should work as normal - resuming chain consensus operations
}
