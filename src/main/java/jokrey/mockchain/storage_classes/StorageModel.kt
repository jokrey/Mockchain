package jokrey.mockchain.storage_classes

import jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.type.transformer.LITypeToBytesTransformer
import org.iq80.leveldb.DB
import org.iq80.leveldb.Options
import org.iq80.leveldb.WriteBatch
import org.iq80.leveldb.impl.Iq80DBFactory
import java.io.File

/**
 * All mutating operations are cached or just done uncommitted, to be executed to the readable state on 'commit'.
 */
interface StorageModel {
    //for all
    fun byteSize(): Long

    //blocks
    fun getLatestHash(): Hash?
    fun numberOfBlocks():Int
    fun highestBlockId() = numberOfBlocks()-1
    operator fun iterator(): Iterator<Block> = muteratorFrom(0)
    //transactions
    operator fun get(key: TransactionHash): Transaction?
    operator fun contains(key: TransactionHash): Boolean
    fun getBlockIdFor(key: TransactionHash) : Int?
    /** This function can be slow as it is only used on a re squash after a chain is restarted */
    fun getAllPersistedTransactionWithDependenciesOrThatAreDependedUpon(): Set<Transaction>
    fun txIterator(): Iterator<Transaction>


    //all changes are cached until a commit
    //blocks
    fun add(block: Block)
    fun muteratorFrom(index: Int): BlockChainStorageIterator
    //transactions
    operator fun set(key: TransactionHash, value: Transaction)
    fun replace(oldKey: TransactionHash, newKey: TransactionHash, newValue: Transaction)
    fun remove(oldHash: TransactionHash)


    //for all
    fun commit()
}

interface BlockChainStorageIterator: Iterator<Block> {
    override fun hasNext(): Boolean
    override fun next(): Block
    fun set(element: Block)
}

/**
 * Meant to mimic the behaviour of persistent storage, however it does some additional hash verifications for debugging purposes.
 */
class NonPersistentStorage : StorageModel {
    private val committedTXS = LinkedHashMap<TransactionHash, Transaction>()
        private val uncommittedTXS = LinkedHashMap<TransactionHash, Transaction>()
    private val committedBLOCKS = ArrayList<Block>()
        private val uncommittedBLOCKS = ArrayList<Block>()

    override fun byteSize(): Long =
            committedTXS.map { it.key.getHash().size + it.value.bDependencies.map { 1 + it.txp.getHash().size }.sum() + it.value.content.size }.sum().toLong() +
            committedBLOCKS.map { it.merkleRoot.getHash().size + (it.previousBlockHash?.getHash()?.size ?: 1) + it.map { it.getHash().size }.sum() }.sum().toLong()

    override fun getLatestHash(): Hash? = if(committedBLOCKS.isEmpty()) null else committedBLOCKS.last().getHeaderHash()
    override fun numberOfBlocks() = committedBLOCKS.size

    override fun get(key: TransactionHash): Transaction? = committedTXS[key]
    override fun contains(key: TransactionHash) = key in committedTXS
    override fun getBlockIdFor(key: TransactionHash) = committedTXS[key]?.blockId

    override fun getAllPersistedTransactionWithDependenciesOrThatAreDependedUpon(): Set<Transaction> {
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
        }
    }

    override fun set(key: TransactionHash, value: Transaction) {
        if(value.blockId < 0) throw IllegalStateException("attempt to persist tx with illegal block id")
        if( key in uncommittedTXS && uncommittedTXS[key]!!.blockId != value.blockId ) throw IllegalStateException("hash known")
        uncommittedTXS[key] = value
    }
    override fun replace(oldKey: TransactionHash, newKey: TransactionHash, newValue: Transaction) {
        val old = uncommittedTXS.remove(oldKey)!!
        if(old.blockId != newValue.blockId) throw IllegalStateException("attempting to replace with different block id")
        if(newKey in uncommittedTXS ) throw IllegalStateException("hash known") //this check might require removal in fringe cases it could still work despite this check failing
        uncommittedTXS[newKey] = newValue
    }
    override fun remove(oldHash: TransactionHash) { uncommittedTXS.remove(oldHash) }

    override fun commit() {
//        for(uncommitted in uncommittedTXS)
//            if(uncommitted.value.bDependencies.any { it.txp !in uncommittedTXS })
//                throw IllegalStateException("unresolved dependency on $uncommitted")

        committedTXS.clear()
        committedTXS.putAll(uncommittedTXS)
        committedBLOCKS.clear()
        committedBLOCKS.addAll(uncommittedBLOCKS)
    }
}





class PersistentStorage(val file: File, clean: Boolean) : StorageModel {
    private val levelDBStore: DB
    private var currentBatch: WriteBatch

    private var latestHash: Hash? = null
    private var numberOfBlocks: Int = 0

    init {
        if(clean) {
            if(!file.exists() || file.deleteRecursively()) {
                levelDBStore = Iq80DBFactory.factory.open(file, Options())
            } else {
                throw IllegalArgumentException("file($file) was supposed to be deleted prior to usage, but could not be")
            }
        } else {
            levelDBStore = Iq80DBFactory.factory.open(file, Options())
            val max = levelDBStore.maxBy {
                if(isBlockKey(it.key)) fromBlockKey(it.key) else Int.MIN_VALUE
            }
            if (max != null) {
                numberOfBlocks = fromBlockKey(max.key)
                latestHash = Block(max.value).getHeaderHash()
            }
        }

        currentBatch = levelDBStore.createWriteBatch()
    }


    override fun byteSize() = file.listFiles().map { it.length() }.sum()


    override fun getLatestHash(): Hash? = latestHash
    override fun numberOfBlocks() = numberOfBlocks

    override fun get(key: TransactionHash): Transaction? {
        val raw = levelDBStore[toTxsKey(key)]
        return if(raw == null) null else Transaction.decode(raw)
    }
    override fun contains(key: TransactionHash) = levelDBStore.get(toTxsKey(key)) != null
    override fun getBlockIdFor(key: TransactionHash) = get(key)?.blockId

    override fun getAllPersistedTransactionWithDependenciesOrThatAreDependedUpon(): Set<Transaction> {
        val set = HashSet<Transaction>()
        for (entry in levelDBStore.iterator()) {
            if(isTxsKey(entry.key)) {
                val tx = Transaction.decode(entry.value)
                if (tx.bDependencies.isNotEmpty()) {
                    set.add(tx)
                    set.addAll(tx.bDependencies.map { get(it.txp)!! })
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
//                    println("next = ${next.key.toList()}")
                    if(isTxsKey(next.key)) {
                        tx = Transaction.decode(next.value)
                        break
                    }
                }
//                println("found: $tx")
            }
        }
    }


    override fun add(block: Block) {
        currentBatch.put(toBlockKey(numberOfBlocks), block.encode())
        numberOfBlocks++
        latestHash = block.getHeaderHash()
    }
    override fun muteratorFrom(index: Int): BlockChainStorageIterator {
        var iterator = index-1
        return object : BlockChainStorageIterator {
            override fun hasNext() = iterator < numberOfBlocks-1
            override fun next(): Block {
                iterator++
                val blockRaw = levelDBStore[toBlockKey(iterator)]
                return Block(blockRaw)
            }
            override fun set(element: Block) {
                currentBatch.put(toBlockKey(iterator), element.encode())
                if(iterator == numberOfBlocks-1)
                    latestHash = element.getHeaderHash() // this is ok, because unless the application crashes latestHash will become permanent here
            }
        }
    }

    override fun set(key: TransactionHash, value: Transaction)  {
        if(value.blockId < 0) throw IllegalStateException("attempt to persist tx with illegal block id")
        currentBatch.put(toTxsKey(key), value.encode())
    }
    override fun replace(oldKey: TransactionHash, newKey: TransactionHash, newValue: Transaction) {
        currentBatch.delete(toTxsKey(oldKey))
        currentBatch.put(toTxsKey(newKey), newValue.encode())
    }
    override fun remove(oldHash: TransactionHash) {
        currentBatch.delete(oldHash.getHash())
    }



    override fun commit() {
        levelDBStore.write(currentBatch)
        currentBatch = levelDBStore.createWriteBatch()
    }




    val transformer = LITypeToBytesTransformer()
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