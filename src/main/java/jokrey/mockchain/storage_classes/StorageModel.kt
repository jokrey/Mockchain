package jokrey.mockchain.storage_classes

import jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.type.transformer.LITypeToBytesTransformer
import org.iq80.leveldb.DB
import org.iq80.leveldb.Options
import org.iq80.leveldb.WriteBatch
import org.iq80.leveldb.impl.Iq80DBFactory
import java.io.File
import java.util.*
import kotlin.NoSuchElementException
import kotlin.collections.ArrayList
import kotlin.collections.HashSet
import kotlin.collections.LinkedHashMap

interface WriteStorageModel : TransactionResolver {
    fun getAllPersistedTransactionsWithDependenciesOrThatAreDependedUpon(): Set<Transaction>

    fun add(key: TransactionHash, value: Transaction)
    fun add(block: Block)

    //blocks
    fun muteratorFrom(index: Int): BlockChainStorageIterator
    //transactions
    fun replace(oldKey: TransactionHash, newKey: TransactionHash, newValue: Transaction)
    fun replace(key: TransactionHash, newValue: Transaction)
    fun remove(key: TransactionHash)
    //fork
    fun deleteAllToBlockIndex(index: Int)

    //for all
    fun blockCommit()
}

/**
 * All mutating operations are cached or just done uncommitted, to be executed to the readable state on 'commit'.
 *
 * NOT! Thread Safe
 */
interface StorageModel : WriteStorageModel {
    fun close()

    //for all
    fun byteSize(): Long

    //blocks
    fun getLatestHash(): Hash?
    fun numberOfBlocks():Int
    fun numberOfTx(): Int
    fun highestBlockId() = numberOfBlocks()-1
    operator fun iterator(): Iterator<Block> = muteratorFrom(0)
    //transactions
    fun getBlockIdFor(key: TransactionHash) : Int?
    fun queryBlockHash(id: Int): Hash
    fun queryBlock(id: Int): Block
    /** This function can be slow as it is only used on a re squash after a chain is restarted */
    fun txIterator(): Iterator<Transaction>

    fun addCommittedChangeListener(changeOccurredCallback: () -> Unit)
    fun fireChangeCommitted()

    fun createIsolatedFrom(forkIndex: Int): IsolatedStorage
}

interface IsolatedStorage : WriteStorageModel {
    fun cancel()
    fun writeChangesToDB()
}

interface BlockChainStorageIterator: Iterator<Block> {
    override fun hasNext(): Boolean
    override fun next(): Block
    fun set(element: Block)
    fun skip()
}

/**
 * Meant to mimic the behaviour of persistent storage, however it does some additional hash verifications for debugging purposes.
 *
 * Additionally some things like getLatestHash may have a subtly different behaviour
 *
 * Does a few un required things that mimic Persistent Behaviour for testing
 *  (only a few mem copies more so whatever...
 */
class NonPersistentStorage : StorageModel {
    internal val committedTXS = LinkedHashMap<TransactionHash, Transaction>()
        internal val uncommittedTXS = LinkedHashMap<TransactionHash, Transaction>()
    internal val committedBLOCKS = ArrayList<Block>()
        internal val uncommittedBLOCKS = ArrayList<Block>()

    override fun byteSize(): Long =
        committedTXS.map { it.key.getHash().size + it.value.bDependencies.map { 1 + it.txp.getHash().size }.sum() + it.value.content.size }.sum().toLong() +
        committedBLOCKS.map { it.merkleRoot.getHash().size + (it.previousBlockHash?.getHash()?.size ?: 1) + it.map { it.getHash().size }.sum() }.sum().toLong()

    override fun getLatestHash(): Hash? = if(committedBLOCKS.isEmpty()) null else committedBLOCKS.last().getHeaderHash()
    override fun numberOfBlocks() = committedBLOCKS.size
    override fun numberOfTx() = committedTXS.size

    override fun get(hash: TransactionHash): Transaction = committedTXS[hash]!!
    override fun getUnsure(hash: TransactionHash): Transaction? = committedTXS[hash]
    override fun contains(hash: TransactionHash) = hash in committedTXS
    override fun getBlockIdFor(key: TransactionHash) = committedTXS[key]?.blockId
    override fun queryBlockHash(id: Int): Hash = committedBLOCKS[id].getHeaderHash()
    override fun queryBlock(id: Int) = committedBLOCKS[id]

    override fun getAllPersistedTransactionsWithDependenciesOrThatAreDependedUpon(): Set<Transaction> {
        val set = HashSet<Transaction>()
        for (tx in committedTXS.values) {
            if(tx.bDependencies.isNotEmpty()) {
                set.add(tx)
                set.addAll(tx.bDependencies.map { committedTXS[it.txp]!! })
            }
        }
        return set
    }
    override fun txIterator(): Iterator<Transaction> = committedTXS.values.iterator()


    override fun add(block: Block) {
        uncommittedBLOCKS.add(block)
    }
    override fun muteratorFrom(index: Int): BlockChainStorageIterator {
        val listIterator = uncommittedBLOCKS.listIterator(index)
        return object : BlockChainStorageIterator {
            override fun hasNext() = listIterator.hasNext()
            override fun next() = listIterator.next()
            override fun set(element: Block) = listIterator.set(element)
            override fun skip() {
                next()
            }
        }
    }

    override fun add(key: TransactionHash, value: Transaction) {
        if(value.blockId < 0) throw IllegalStateException("attempt to persist tx with illegal block id")
        if(key in uncommittedTXS && uncommittedTXS[key]!!.blockId != value.blockId ) throw IllegalStateException("hash($key) known")
        uncommittedTXS[key] = value
    }
    override fun replace(oldKey: TransactionHash, newKey: TransactionHash, newValue: Transaction) {
        val old = uncommittedTXS.remove(oldKey)!!
        if(old.blockId != newValue.blockId) throw IllegalStateException("attempting to replace with different block id")
        if(newKey in uncommittedTXS ) throw IllegalStateException("hash known") //this check might require removal in fringe cases it could still work despite this check failing
        uncommittedTXS[newKey] = newValue
    }
    override fun replace(key: TransactionHash, newValue: Transaction) {
        uncommittedTXS[key] = newValue
    }
    override fun remove(key: TransactionHash) { uncommittedTXS.remove(key) }


    override fun deleteAllToBlockIndex(index: Int) {
        if(index+1 < uncommittedBLOCKS.size) {
            val subView = uncommittedBLOCKS.subList(index+1, uncommittedBLOCKS.size)
            for(b in subView)
                uncommittedTXS.removeAll(b)
            subView.clear()
        }
    }

    override fun blockCommit() {
//        for(uncommitted in uncommittedTXS)
//            if(uncommitted.value.bDependencies.any { it.txp !in uncommittedTXS })
//                throw IllegalStateException("unresolved dependency on $uncommitted")

        committedTXS.clear()
        committedTXS.putAll(uncommittedTXS)
        committedBLOCKS.clear()
        committedBLOCKS.addAll(uncommittedBLOCKS)

        fireChangeCommitted()
    }

    private val changeOccurredCallbacks = LinkedList<() -> Unit>()
    override fun addCommittedChangeListener(changeOccurredCallback: () -> Unit) {
        changeOccurredCallbacks.add(changeOccurredCallback)
    }
    override fun fireChangeCommitted() {
        for(c in changeOccurredCallbacks) c()
    }

    override fun close() {
        committedTXS.clear()
        committedBLOCKS.clear()
        uncommittedTXS.clear()
        uncommittedBLOCKS.clear()
    }

    override fun createIsolatedFrom(forkIndex: Int): IsolatedStorage {
        val nonPers = NonPersistentStorage()
        for(i in 0..forkIndex) {
            val blockAtI = committedBLOCKS[i]
            nonPers.uncommittedBLOCKS.add(blockAtI)
            for(txp in blockAtI) {
                nonPers.uncommittedTXS[txp] = committedTXS[txp]!!
            }
            nonPers.blockCommit()
        }
        return object : IsolatedStorage {
            //gc will clean rest
            override fun cancel() {
                nonPers.committedTXS.clear()
                nonPers.uncommittedTXS.clear()
                nonPers.committedBLOCKS.clear()
                nonPers.uncommittedBLOCKS.clear()
            }
            override fun writeChangesToDB() {
                this@NonPersistentStorage.uncommittedTXS.clear()
                this@NonPersistentStorage.uncommittedBLOCKS.clear()
                this@NonPersistentStorage.uncommittedTXS.putAll(nonPers.uncommittedTXS)
                this@NonPersistentStorage.uncommittedBLOCKS.addAll(nonPers.uncommittedBLOCKS)
                this@NonPersistentStorage.blockCommit()
            }

            override fun getAllPersistedTransactionsWithDependenciesOrThatAreDependedUpon() =
                nonPers.getAllPersistedTransactionsWithDependenciesOrThatAreDependedUpon()
            override fun get(hash: TransactionHash) = nonPers.get(hash)
            override fun getUnsure(hash: TransactionHash) = nonPers.getUnsure(hash)
            override fun contains(hash: TransactionHash) = nonPers.contains(hash)
            override fun add(key: TransactionHash, value: Transaction) = nonPers.add(key, value)
            override fun add(block: Block) = nonPers.add(block)
            override fun muteratorFrom(index: Int) = nonPers.muteratorFrom(index)
            override fun replace(oldKey: TransactionHash, newKey: TransactionHash, newValue: Transaction) =
                nonPers.replace(oldKey, newKey, newValue)
            override fun replace(key: TransactionHash, newValue: Transaction) = nonPers.replace(key, newValue)
            override fun remove(key: TransactionHash) = nonPers.remove(key)
            override fun deleteAllToBlockIndex(index: Int) = nonPers.deleteAllToBlockIndex(index)
            override fun blockCommit() = nonPers.blockCommit()
        }
    }
}


class PersistentStorage(val file: File, clean: Boolean) : StorageModel {
    private val levelDBStore: DB

    private var latestHash: Hash? = null
    private var numberOfBlocks: Int = 0
    private var numberOfTxs: Int = 0

    private var currentBatch: WriteBatch
    private var latestHash_uncommitted: Hash? = null
    private var numberOfBlocks_uncommitted: Int = 0
    private var numberOfTxs_uncommitted: Int = 0

    private val transformer = LITypeToBytesTransformer()
    init {
        if(clean) {
            if(!file.exists() || file.deleteRecursively()) {
                levelDBStore = Iq80DBFactory.factory.open(file, Options())
            } else {
                throw IllegalArgumentException("file($file) was supposed to be deleted prior to usage, but could not be")
            }
        } else {
            levelDBStore = Iq80DBFactory.factory.open(file, Options())
            var latestBlock: Map.Entry<ByteArray, ByteArray>? = null
            var latestBlockNumber = 0
            var txCounter = 0
            levelDBStore.forEach {
                if(isBlockKey(it.key)) {
                    val id = fromBlockKey(it.key)
                    if(id > latestBlockNumber) {
                        latestBlockNumber = id
                        latestBlock = it
                    }
                } else { //isTxKey(it.key)
                    txCounter++
                }
            }
            val encodedLatestBlock = latestBlock?.value
            if(encodedLatestBlock != null)
                latestHash_uncommitted = Block(encodedLatestBlock).getHeaderHash()
            numberOfBlocks_uncommitted = latestBlockNumber
            numberOfTxs_uncommitted = txCounter
        }


        latestHash = latestHash_uncommitted
        numberOfBlocks = numberOfBlocks_uncommitted
        numberOfTxs = numberOfTxs_uncommitted
        currentBatch = levelDBStore.createWriteBatch()
    }


    override fun byteSize() = file.listFiles().map { it.length() }.sum()


    override fun getLatestHash(): Hash? = latestHash
    override fun numberOfBlocks() = numberOfBlocks
    override fun numberOfTx() = numberOfTxs

    override fun get(hash: TransactionHash) = getUnsure(hash)!!
    override fun getUnsure(hash: TransactionHash): Transaction? {
        val raw = levelDBStore[toTxsKey(hash)]
        return if(raw == null) null else Transaction.decode(raw)
    }
    override fun contains(hash: TransactionHash) = levelDBStore.get(toTxsKey(hash)) != null
    override fun getBlockIdFor(key: TransactionHash) = getUnsure(key)?.blockId //todo - decoding the entire transaction here is inefficient, especially for large tx
    override fun queryBlockHash(id: Int) = Block(levelDBStore[toBlockKey(id)]).getHeaderHash() //todo - decoding the entire block here is inefficient, especially for large blocks
    override fun queryBlock(id: Int) = Block(levelDBStore[toBlockKey(id)])

    override fun getAllPersistedTransactionsWithDependenciesOrThatAreDependedUpon(): Set<Transaction> {
        val set = HashSet<Transaction>()
        for (entry in levelDBStore.iterator()) {
            if(isTxsKey(entry.key)) {
                val tx = Transaction.decode(entry.value)
                if (tx.bDependencies.isNotEmpty()) {
                    set.add(tx)
                    set.addAll(tx.bDependencies.map { get(it.txp) })
                }
            }
        }
        return set
    }
    override fun txIterator(): Iterator<Transaction> {
        val iterator = levelDBStore.iterator()
        return object : Iterator<Transaction> {
            var tx:Transaction? = null
            init {
                searchNextTx()
            }
            override fun hasNext(): Boolean {
                return tx != null
            }
            override fun next(): Transaction {
                return if(tx == null) {
                    throw NoSuchElementException()
                } else {
                    val prev = tx!!
                    searchNextTx()
                    prev
                }
            }
            fun searchNextTx() {
                tx = null
                while(iterator.hasNext()) {
                    val next = iterator.next()
                    if(isTxsKey(next.key)) {
                        tx = Transaction.decode(next.value)
                        break
                    }
                }
            }
        }
    }


    override fun add(block: Block) {
        currentBatch.put(toBlockKey(numberOfBlocks_uncommitted), block.encode())
        numberOfBlocks_uncommitted++
        latestHash_uncommitted = block.getHeaderHash()
    }
    override fun muteratorFrom(index: Int): BlockChainStorageIterator {
        //println("muteratorFrom: index: $index, numberOfBlocks_uncommitted: $numberOfBlocks_uncommitted")
        return object : BlockChainStorageIterator {
            var iterator = index-1
            override fun hasNext() : Boolean {
                //println("hasNext: iterator: ${iterator+1}, numberOfBlocks_uncommitted: $numberOfBlocks_uncommitted")
                return iterator+1 < numberOfBlocks_uncommitted
            }
            override fun next(): Block {
                //println("next(iterator: ${iterator+1}): "+Block(levelDBStore[toBlockKey(iterator+1)]))
                return Block(levelDBStore[toBlockKey(++iterator)])
            }
            override fun set(element: Block) {
                //println("set: element=$element")
                currentBatch.put(toBlockKey(iterator), element.encode())
                if(iterator == numberOfBlocks_uncommitted-1)
                    latestHash_uncommitted = element.getHeaderHash() // this is ok, because unless the application crashes latestHash will become permanent here
            }
            override fun skip() {
                iterator++
            }
        }
    }

    override fun add(key: TransactionHash, value: Transaction)  {
        if(value.blockId < 0) throw IllegalStateException("attempt to persist tx with illegal block id")
        currentBatch.put(toTxsKey(key), value.encode())
        numberOfTxs_uncommitted++
    }
    override fun replace(oldKey: TransactionHash, newKey: TransactionHash, newValue: Transaction) {
        currentBatch.delete(toTxsKey(oldKey))
        currentBatch.put(toTxsKey(newKey), newValue.encode())
    }
    override fun replace(key: TransactionHash, newValue: Transaction) {
        currentBatch.put(toTxsKey(key), newValue.encode())
    }
    override fun remove(key: TransactionHash) {
        currentBatch.delete(toTxsKey(key))
        numberOfTxs_uncommitted--
    }

    override fun deleteAllToBlockIndex(index: Int) {
        var deleteCounter = index+1
        while(deleteCounter < numberOfBlocks_uncommitted) {
            val b = queryBlock(deleteCounter)
            for(txp in b)
                remove(txp)
            currentBatch.delete(toBlockKey(deleteCounter))
            numberOfBlocks_uncommitted--

            deleteCounter++
        }
    }



    //prevents out of memory issues, but keeps acid for blocks
    override fun blockCommit() {
        latestHash = latestHash_uncommitted
        numberOfBlocks = numberOfBlocks_uncommitted
        numberOfTxs = numberOfTxs_uncommitted
        levelDBStore.write(currentBatch)
        currentBatch = levelDBStore.createWriteBatch()

        fireChangeCommitted()
    }

    private val changeOccurredCallbacks = LinkedList<() -> Unit>()
    override fun addCommittedChangeListener(changeOccurredCallback: () -> Unit) {
        changeOccurredCallbacks.add(changeOccurredCallback)
    }
    override fun fireChangeCommitted() {
        for(c in changeOccurredCallbacks) c()
    }


    override fun close() = levelDBStore.close()


    override fun createIsolatedFrom(forkIndex: Int): IsolatedStorage {
        // todo This is bad. Entire chain is put into non-pers, making it too large
        val nonPers = NonPersistentStorage()
        val iterator = muteratorFrom(0)
        for(i in 0 .. forkIndex) {
            val blockAtI = iterator.next()
            //println("initial-create blockAtI(i=$i): $blockAtI")
            nonPers.uncommittedBLOCKS.add(blockAtI)
            for(txp in blockAtI) {
                nonPers.uncommittedTXS[txp] = get(txp)
            }
        }
        nonPers.blockCommit()
        return object : IsolatedStorage {
            override fun cancel() {
                //gc will clean
            }
            override fun writeChangesToDB() {
                //println("blocks before = ${iterator().asSequence().toList()}")
                //println("forkIndex= $forkIndex")
                val isolatedMuterator = nonPers.muteratorFrom(forkIndex+1)
                val muterator = this@PersistentStorage.muteratorFrom(forkIndex+1)
                //println("isolatedMuterator content = ${nonPers.muteratorFrom(0).asSequence().toList()}")
                //println("isolatedMuterator.hasNext(): "+isolatedMuterator.hasNext())
                while(isolatedMuterator.hasNext()) {
                    //println("muterator.hasNext(): "+muterator.hasNext())
                    if(muterator.hasNext()) {
//                        val previousBlock = muterator.get()
//                        for(txp in previousBlock)
//                            this@PersistentStorage.remove(txp)
                        val block = isolatedMuterator.next()
//                        //println("block to be set after change - previous: $previousBlock")
                        //println("block to be set after change: $block")
                        muterator.skip()
                        muterator.set(block)
                        for(txp in block)
                            this@PersistentStorage.add(txp, nonPers[txp])
                        this@PersistentStorage.blockCommit()//prevents out of memory issues, but keeps acid for blocks
                    } else {
                        break
                    }
                }
                while(isolatedMuterator.hasNext()) {
                    val block = isolatedMuterator.next()
                    //println("block to be added after change: $block")
                    this@PersistentStorage.add(block)
                    for(txp in block) {
                        this@PersistentStorage.add(txp, nonPers[txp])
                    }
                    this@PersistentStorage.blockCommit()//prevents out of memory issues, but keeps acid for blocks
                }
                //println("blocks after = ${iterator().asSequence().toList()}")
            }

            override fun getAllPersistedTransactionsWithDependenciesOrThatAreDependedUpon() =
                nonPers.getAllPersistedTransactionsWithDependenciesOrThatAreDependedUpon()
            override fun get(hash: TransactionHash) = nonPers.get(hash)
            override fun getUnsure(hash: TransactionHash) = nonPers.getUnsure(hash)
            override fun contains(hash: TransactionHash) = nonPers.contains(hash)
            override fun add(key: TransactionHash, value: Transaction) = nonPers.add(key, value)
            override fun add(block: Block) = nonPers.add(block)
            override fun muteratorFrom(index: Int) = nonPers.muteratorFrom(index)
            override fun replace(oldKey: TransactionHash, newKey: TransactionHash, newValue: Transaction) =
                nonPers.replace(oldKey, newKey, newValue)
            override fun replace(key: TransactionHash, newValue: Transaction) = nonPers.replace(key, newValue)
            override fun remove(key: TransactionHash) = nonPers.remove(key)
            override fun deleteAllToBlockIndex(index: Int) = nonPers.deleteAllToBlockIndex(index)
            override fun blockCommit() = nonPers.blockCommit()
        }
    }





    operator fun Byte.plus(other: ByteArray): ByteArray {
        val arraySize = other.size
        val result = ByteArray(1 + arraySize)
        result[0] = this
        System.arraycopy(other, 0, result, 1, arraySize)
        return result
    }

    val BLOCK_STORE = 0.toByte()
    fun isBlockKey(key: ByteArray) = key[0] == BLOCK_STORE
    fun toBlockKey(i: Int) = BLOCK_STORE + transformer.transform(i)
    fun fromBlockKey(key: ByteArray) = transformer.detransform(key.copyOfRange(1, key.size), Int::class.java)
    val TXS_STORE = 1.toByte()
    fun isTxsKey(key: ByteArray) = key[0] == TXS_STORE
    fun toTxsKey(key: TransactionHash) = TXS_STORE + key.getHash()
}