import jokrey.mockchain.Mockchain
import jokrey.mockchain.consensus.ManualConsensusAlgorithm
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import jokrey.mockchain.squash.BuildUponSquashHandler
import jokrey.mockchain.squash.PartialReplaceSquashHandler
import jokrey.mockchain.storage_classes.*
import jokrey.mockchain.visualization.util.EmptyApplication
import jokrey.mockchain.visualization.util.contentIsArbitrary
import java.io.File
import kotlin.test.assertNotEquals


class GeneralTests {
    private val tx0 = Transaction(contentIsArbitrary())
    private val tx1 = Transaction(contentIsArbitrary())
    private val tx2 = Transaction(contentIsArbitrary())
    private val tx3 = Transaction(contentIsArbitrary())
    private val tx4 = Transaction(contentIsArbitrary())

    @Test
    fun testSimpleWorks() {
        val instance = Mockchain(EmptyApplication())
        instance.consensus as ManualConsensusAlgorithm

        instance.commitToMemPool(tx0)
        instance.commitToMemPool(tx1)
        instance.commitToMemPool(tx2)
        instance.commitToMemPool(tx3)
        instance.commitToMemPool(tx4)

        instance.consensus.performConsensusRound(true)
        assertEquals(5, instance.chain.persistedTxCount())
    }

    @Test
    fun testSimpleBuildUponDependenciesWork() {
        val instance = Mockchain(EmptyApplication())
        instance.consensus as ManualConsensusAlgorithm

        instance.commitToMemPool(tx0)
        instance.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.BUILDS_UPON)))
        instance.commitToMemPool(Transaction(tx2.content, Dependency(tx1.hash, DependencyType.BUILDS_UPON)))
        instance.commitToMemPool(Transaction(tx3.content, Dependency(tx2.hash, DependencyType.BUILDS_UPON)))
        instance.commitToMemPool(Transaction(tx4.content, Dependency(tx3.hash, DependencyType.BUILDS_UPON)))

        instance.consensus.performConsensusRound(true)
        assertEquals(5, instance.chain.persistedTxCount()) //as with build upon they don't actually remove anything, but just cause a call to the application to alter the newer tx
    }

    @Test
    fun testDoubleDependenciesDenied() {
        val instance = Mockchain(EmptyApplication())
        instance.consensus as ManualConsensusAlgorithm

        instance.commitToMemPool(tx0)
        instance.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.BUILDS_UPON), Dependency(tx0.hash, DependencyType.BUILDS_UPON)))

        instance.consensus.performConsensusRound(true)
        assertEquals(1, instance.chain.persistedTxCount())
    }

    @Test
    fun testSimpleReplaceDependenciesWork() {
        val instance = Mockchain(EmptyApplication())
        instance.consensus as ManualConsensusAlgorithm

        instance.commitToMemPool(tx0)
        instance.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.REPLACES)))
        instance.commitToMemPool(Transaction(tx2.content, Dependency(tx1.hash, DependencyType.REPLACES)))
        instance.commitToMemPool(Transaction(tx3.content, Dependency(tx2.hash, DependencyType.REPLACES)))
        instance.commitToMemPool(Transaction(tx4.content, Dependency(tx3.hash, DependencyType.REPLACES)))

        instance.consensus.performConsensusRound(true)
        assertEquals(1, instance.chain.persistedTxCount())
        assertArrayEquals(tx4.content, instance.chain.getPersistedTransactions().asSequence().toList().first().content)
    }


    @Test
    fun testSimpleSequenceDependenciesWork() {
        val instance = Mockchain(EmptyApplication())
        instance.consensus as ManualConsensusAlgorithm

        instance.commitToMemPool(tx0)
        instance.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.SEQUENCE_PART)))
        instance.commitToMemPool(Transaction(tx2.content, Dependency(tx1.hash, DependencyType.SEQUENCE_PART)))

        instance.consensus.performConsensusRound(false)
        assertEquals(3, instance.chain.persistedTxCount())

        instance.commitToMemPool(Transaction(tx3.content, Dependency(tx2.hash, DependencyType.SEQUENCE_PART)))
        instance.commitToMemPool(Transaction(tx4.content, Dependency(tx3.hash, DependencyType.SEQUENCE_END)))

        instance.consensus.performConsensusRound(true)
        assertEquals(1, instance.chain.persistedTxCount())
    }

    @Test
    fun testUnresolvedDependencyCausesDenial() {
        val instance = Mockchain(EmptyApplication())
        instance.consensus as ManualConsensusAlgorithm

        instance.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.REPLACES)))

        instance.consensus.performConsensusRound(false)
        assertTrue(instance.chain.persistedTxCount() == 0)
    }

    @Test
    fun testChangeReintroductionToChainMaintainsCorrectHashChain_nonPersistent() {
        val instance = Mockchain(EmptyApplication())
        instance.consensus as ManualConsensusAlgorithm
        instance.commitToMemPool(Transaction(contentIsArbitrary()))
        instance.commitToMemPool(tx0)
        instance.consensus.performConsensusRound(false)
        instance.commitToMemPool(Transaction(contentIsArbitrary()))
        instance.consensus.performConsensusRound(false)
        instance.commitToMemPool(Transaction(contentIsArbitrary()))
        instance.consensus.performConsensusRound(false)
        instance.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.BUILDS_UPON), Dependency(tx0.hash, DependencyType.REPLACES)))
        instance.consensus.performConsensusRound(false)
        instance.commitToMemPool(Transaction(tx2.content, Dependency(tx1.hash, DependencyType.BUILDS_UPON), Dependency(tx1.hash, DependencyType.REPLACES)))
        instance.commitToMemPool(Transaction(contentIsArbitrary()))
        instance.consensus.performConsensusRound(false)
        instance.commitToMemPool(Transaction(contentIsArbitrary()))
        instance.consensus.performConsensusRound(false)
        instance.commitToMemPool(Transaction(contentIsArbitrary()))
        instance.consensus.performConsensusRound(false)
        instance.commitToMemPool(Transaction(tx3.content, Dependency(tx2.hash, DependencyType.BUILDS_UPON), Dependency(tx2.hash, DependencyType.REPLACES)))
        instance.consensus.performConsensusRound(false)
        instance.commitToMemPool(Transaction(tx4.content, Dependency(tx3.hash, DependencyType.BUILDS_UPON), Dependency(tx3.hash, DependencyType.REPLACES)))
        instance.consensus.performConsensusRound(false)

        val previous = instance.chain.getLatestHash()

        instance.consensus.performConsensusRound(true)

        assertNotEquals(previous, instance.chain.getLatestHash())
        assertTrue(instance.chain.validateHashChain())
    }

    @Test
    fun testChangeReintroductionToChainMaintainsCorrectHashChain_Persistent() {
        val instance = Mockchain(EmptyApplication(), store = PersistentStorage(File(System.getProperty("user.home") + "/Desktop/maintains_Persistent_testDir"), true))
        instance.consensus as ManualConsensusAlgorithm
        instance.commitToMemPool(Transaction(contentIsArbitrary()))
        instance.commitToMemPool(tx0)
        instance.consensus.performConsensusRound(false)
        instance.commitToMemPool(Transaction(contentIsArbitrary()))
        instance.consensus.performConsensusRound(false)
        instance.commitToMemPool(Transaction(contentIsArbitrary()))
        instance.consensus.performConsensusRound(false)
        instance.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.BUILDS_UPON), Dependency(tx0.hash, DependencyType.REPLACES)))
        instance.consensus.performConsensusRound(false)
        instance.commitToMemPool(Transaction(tx2.content, Dependency(tx1.hash, DependencyType.BUILDS_UPON), Dependency(tx1.hash, DependencyType.REPLACES)))
        instance.commitToMemPool(Transaction(contentIsArbitrary()))
        instance.consensus.performConsensusRound(false)
        instance.commitToMemPool(Transaction(contentIsArbitrary()))
        instance.consensus.performConsensusRound(false)
        instance.commitToMemPool(Transaction(contentIsArbitrary()))
        instance.consensus.performConsensusRound(false)
        instance.commitToMemPool(Transaction(tx3.content, Dependency(tx2.hash, DependencyType.BUILDS_UPON), Dependency(tx2.hash, DependencyType.REPLACES)))
        instance.consensus.performConsensusRound(false)
        instance.commitToMemPool(Transaction(tx4.content, Dependency(tx3.hash, DependencyType.BUILDS_UPON), Dependency(tx3.hash, DependencyType.REPLACES)))
        instance.consensus.performConsensusRound(false)

        val previous = instance.chain.getLatestHash()

        instance.consensus.performConsensusRound(true)

        assertNotEquals(previous, instance.chain.getLatestHash())
        assertTrue(instance.chain.validateHashChain())
    }

    @Test
    fun testReflexiveDependencyIsBlocked() {
        val instance = Mockchain(EmptyApplication())
        instance.consensus as ManualConsensusAlgorithm

        instance.commitToMemPool(Transaction(tx1.content, Dependency(tx1.hash, DependencyType.REPLACES)))

        instance.consensus.performConsensusRound(false)
        assertTrue(instance.chain.persistedTxCount() == 0)
    }

    @Test
    fun testSimpleDependencyLoopIsBlocked() {
        val instance = Mockchain(EmptyApplication())
        instance.consensus as ManualConsensusAlgorithm

        instance.commitToMemPool(Transaction(tx1.content, Dependency(tx2.hash, DependencyType.REPLACES)))
        instance.commitToMemPool(Transaction(tx2.content, Dependency(tx1.hash, DependencyType.REPLACES)))

        instance.consensus.performConsensusRound(false)
        assertTrue(instance.chain.persistedTxCount() == 0)
    }

    @Test
    fun testComplexDependencyLoopIsBlocked() {
        val instance = Mockchain(EmptyApplication())
        instance.consensus as ManualConsensusAlgorithm

        instance.commitToMemPool(Transaction(tx1.content, Dependency(tx4.hash, DependencyType.REPLACES)))
        instance.commitToMemPool(Transaction(tx2.content, Dependency(tx1.hash, DependencyType.REPLACES)))
        instance.commitToMemPool(Transaction(tx3.content, Dependency(tx2.hash, DependencyType.REPLACES)))
        instance.commitToMemPool(Transaction(tx4.content, Dependency(tx3.hash, DependencyType.REPLACES)))
        instance.commitToMemPool(Transaction(tx0.content, Dependency(tx2.hash, DependencyType.REPLACES))) //a dependency on a dependency loop is rightfully denied

        instance.consensus.performConsensusRound(false)
        assertTrue(instance.chain.persistedTxCount() == 0)
    }

    @Test
    fun testDependencyLoopDoesNotEliminateLegalTx() {
        val instance = Mockchain(EmptyApplication())
        instance.consensus as ManualConsensusAlgorithm

        instance.commitToMemPool(Transaction(tx0.content))

        //The following is required to NOT cause tx0 to be denied, otherwise this would be an exploit to get any tx in the mem pool denied
        instance.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.REPLACES), Dependency(tx2.hash, DependencyType.REPLACES)))
        instance.commitToMemPool(Transaction(tx2.content, Dependency(tx1.hash, DependencyType.REPLACES)))

        instance.consensus.performConsensusRound(false)
        assertEquals(tx0, instance.chain.getPersistedTransactions().asSequence().toList().first())
        assertEquals(1, instance.chain.persistedTxCount())
    }

    @Test
    fun testDoubleReplaceDenied() {
        val instance = Mockchain(EmptyApplication())
        instance.consensus as ManualConsensusAlgorithm

        instance.commitToMemPool(tx0)
        instance.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.REPLACES)))
        instance.commitToMemPool(Transaction(tx2.content, Dependency(tx0.hash, DependencyType.REPLACES)))

        instance.consensus.performConsensusRound(true)
        assertEquals(1, instance.chain.persistedTxCount())
        assertTrue( tx1 == instance.chain.getPersistedTransactions().asSequence().toList().first() ||
                    tx2 == instance.chain.getPersistedTransactions().asSequence().toList().first())
    }

    @Test
    fun doubleHashBlocked() {
        val instance = Mockchain(EmptyApplication())

        instance.commitToMemPool(tx0)

        assertThrows(IllegalArgumentException::class.java) {
            instance.commitToMemPool(tx0)
        }
    }

    @Test
    fun doubleHashAlsoBlockedIfOriginatesInSquash() {
        val instance = Mockchain(object: EmptyApplication() {
            override fun getBuildUponSquashHandler(): BuildUponSquashHandler = {_, _ -> tx0.content}
        })
        instance.consensus as ManualConsensusAlgorithm

        instance.commitToMemPool(tx0)
        instance.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.BUILDS_UPON)))

        instance.consensus.performConsensusRound(false)

        assertEquals(1, instance.chain.persistedTxCount())

        instance.consensus.performConsensusRound(true)
    }

    @Test
    fun doubleHashAlsoBlockedIfOriginatesInSquashAndDependencies() {
        val tx0 = Transaction(byteArrayOf(0,0,0,0))
        val tx1 = Transaction(byteArrayOf(1,1,1,1))

        val instance = Mockchain(object: EmptyApplication() {
            override fun getBuildUponSquashHandler(): BuildUponSquashHandler = {_, _ -> tx0.content}
        })
        instance.consensus as ManualConsensusAlgorithm

        instance.commitToMemPool(tx0)
        instance.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.BUILDS_UPON)))
        instance.commitToMemPool(Transaction(tx2.content, Dependency(tx1.hash, DependencyType.REPLACES_PARTIAL)))

        instance.consensus.performConsensusRound(false)

        assertEquals(1, instance.chain.persistedTxCount())

        instance.consensus.performConsensusRound(true)
    }

    @Test
    fun doubleHashAlsoBlockedIfOriginatesInSquashAndPartlyPersisted() {
        val instance = Mockchain(object: EmptyApplication() {
            override fun getBuildUponSquashHandler(): BuildUponSquashHandler = {_, _ -> tx0.content}
        })
        instance.consensus as ManualConsensusAlgorithm

        instance.commitToMemPool(tx0)
        instance.consensus.performConsensusRound(false)
        assertEquals(1, instance.chain.persistedTxCount())

        instance.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.BUILDS_UPON)))

        instance.consensus.performConsensusRound(false)

        assertEquals(1, instance.chain.persistedTxCount())
    }

    @Test
    fun testIllegalSequencesNto1() {
        val instance = Mockchain(EmptyApplication())    
        instance.consensus as ManualConsensusAlgorithm

        instance.commitToMemPool(tx0)
        instance.commitToMemPool(tx1)
        instance.commitToMemPool(Transaction(tx2.content, Dependency(tx1.hash, DependencyType.SEQUENCE_PART), Dependency(tx0.hash, DependencyType.SEQUENCE_PART)))

        instance.consensus.performConsensusRound(false)
        assertEquals(2, instance.chain.persistedTxCount())
    }

    @Test
    fun testIllegalSequences1toN() {
        val instance = Mockchain(EmptyApplication())
        instance.consensus as ManualConsensusAlgorithm

        instance.commitToMemPool(tx0)
        instance.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.SEQUENCE_PART)))
        instance.commitToMemPool(Transaction(tx2.content, Dependency(tx0.hash, DependencyType.SEQUENCE_PART)))
        instance.commitToMemPool(Transaction(tx3.content, Dependency(tx1.hash, DependencyType.SEQUENCE_END)))
        instance.commitToMemPool(Transaction(tx4.content, Dependency(tx2.hash, DependencyType.SEQUENCE_END)))

        instance.consensus.performConsensusRound(false)
        assertEquals(3, instance.chain.persistedTxCount())
        instance.consensus.performConsensusRound(true)
        assertEquals(1, instance.chain.persistedTxCount())
    }

    @Test
    fun testReplaceSequencePartIsIllegal() {
        val instance = Mockchain(EmptyApplication())
        instance.consensus as ManualConsensusAlgorithm

        instance.commitToMemPool(tx0)
        instance.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.SEQUENCE_PART)))
        instance.commitToMemPool(Transaction(tx2.content, Dependency(tx1.hash, DependencyType.REPLACES)))
        instance.commitToMemPool(Transaction(tx3.content, Dependency(tx1.hash, DependencyType.SEQUENCE_END)))

        instance.consensus.performConsensusRound(false)

        println("instance.chain.getPersistedTransactions().asSequence().toList().toList() = ${instance.chain.getPersistedTransactions().asSequence().toList().toList()}")

        assertEquals(3, instance.chain.persistedTxCount())
        instance.consensus.performConsensusRound(true)
        println("instance.chain.getPersistedTransactions().asSequence().toList().toList() = ${instance.chain.getPersistedTransactions().asSequence().toList().toList()}")
        assertEquals(1, instance.chain.persistedTxCount())
    }

    @Test
    fun testIntermediateEqualHashWithinTxIsIgnored() {

        val chainCreator = { Mockchain(object: EmptyApplication() {
            override fun getBuildUponSquashHandler(): BuildUponSquashHandler = {_, c ->
                val new = c.clone()
                new[0]=123
                new
            }
        })}

        val tx0 = Transaction(byteArrayOf(1, 2, 3, 4, 5))
        val tx1 = Transaction(byteArrayOf(2, 2, 3, 4, 5))
        val tx2 = Transaction(byteArrayOf(3, 2, 3, 4, 5))
        val willBeTx1 = Transaction(byteArrayOf(123, 2, 3, 4, 5))

        run {
            val instance = chainCreator()
            instance.consensus as ManualConsensusAlgorithm

            instance.commitToMemPool(tx0)

            //tx0 becomes (1,2,3,4) - tx1 becomes (123,2,3,4,5)
            instance.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.BUILDS_UPON), Dependency(tx0.hash, DependencyType.REPLACES_PARTIAL)))

            //tx1 becomes (123,2,3,4) - tx2 becomes (123,2,3,4,5) [[[WHICH IS WHAT TX1 WAS - but no longer is, by the end of this ]]]
            instance.commitToMemPool(Transaction(tx2.content, Dependency(tx1.hash, DependencyType.BUILDS_UPON), Dependency(tx1.hash, DependencyType.REPLACES_PARTIAL)))

            instance.consensus.performConsensusRound(false)
            assertEquals(3, instance.chain.persistedTxCount())
            instance.consensus.performConsensusRound(true)
            assertEquals(3, instance.chain.persistedTxCount()) //squash does changes, as checked in the following, but it does not replace a tx
//        assertEquals(123, instance.chain.getPersistedTransactions().asSequence().toList()[1].content[0])
//        assertEquals(4, instance.chain.getPersistedTransactions().asSequence().toList()[0].content.size)
//        assertEquals(4, instance.chain.getPersistedTransactions().asSequence().toList()[1].content.size)
//        assertTrue(instance.chain.getPersistedTransactions().asSequence().toList()[0].content.size < instance.chain.getPersistedTransactions().asSequence().toList()[2].content.size)
        }


        //assert that the result is the same if we squash in between - i.e. the incremental behaviour of the squash algorithm is given
        run {
            val instance = chainCreator()
            instance.consensus as ManualConsensusAlgorithm


            instance.commitToMemPool(tx0)

            //tx0 becomes (1,2,3,4) - tx1 becomes (123,2,3,4,5)
            instance.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.BUILDS_UPON), Dependency(tx0.hash, DependencyType.REPLACES_PARTIAL)))

            instance.consensus.performConsensusRound(true)
            //tx1 becomes (123,2,3,4) - tx2 becomes (123,2,3,4,5)
            instance.commitToMemPool(Transaction(tx2.content, Dependency(willBeTx1.hash, DependencyType.BUILDS_UPON), Dependency(willBeTx1.hash, DependencyType.REPLACES_PARTIAL)))

            instance.consensus.performConsensusRound(false)
            assertEquals(3, instance.chain.persistedTxCount())
            println("persisted prior to squash (hashes) : " + instance.chain.getPersistedTransactions().asSequence().toList().map { it.hash })
            println("persisted prior to squash : " + instance.chain.getPersistedTransactions().asSequence().toList())
            instance.consensus.performConsensusRound(true)
            println("persisted AFTER to squash (hashes) : " + instance.chain.getPersistedTransactions().asSequence().toList().map { it.hash })
            println("persisted AFTER to squash : " + instance.chain.getPersistedTransactions().asSequence().toList())
            assertEquals(3, instance.chain.persistedTxCount()) //squash does changes, as checked in the following, but it does not replace a tx
//        assertEquals(123, instance.chain.getPersistedTransactions().asSequence().toList()[1].content[0])
//        assertEquals(4, instance.chain.getPersistedTransactions().asSequence().toList()[0].content.size)
//        assertEquals(4, instance.chain.getPersistedTransactions().asSequence().toList()[1].content.size)
//        assertTrue(instance.chain.getPersistedTransactions().asSequence().toList()[0].content.size < instance.chain.getPersistedTransactions().asSequence().toList()[2].content.size)
        }
    }

    @Test
    fun testTxMakesTwoChangesThatCauseTheSameHashAreRejected() {
        val instance = Mockchain(object: EmptyApplication() {
            override fun getBuildUponSquashHandler(): BuildUponSquashHandler {
                return {_, _ -> byteArrayOf(1,2,3)}
            }
        })
        instance.consensus as ManualConsensusAlgorithm

        val tx0 = Transaction(byteArrayOf(1,2,3,5))
        val tx1 = Transaction(byteArrayOf(1,2,3,6))

        instance.commitToMemPool(tx0)
        instance.commitToMemPool(tx1)

        instance.commitToMemPool(Transaction(tx2.content, Dependency(tx0.hash, DependencyType.REPLACES_PARTIAL), Dependency(tx1.hash, DependencyType.REPLACES_PARTIAL)))
        println("instance.chain.getPersistedTransactions().asSequence().toList().map { it.content.toList() } = ${instance.chain.getPersistedTransactions().asSequence().toList().map { it.content.toList() }}")

        instance.consensus.performConsensusRound(false)
        println("instance.chain.getPersistedTransactions().asSequence().toList().map { it.content.toList() } = ${instance.chain.getPersistedTransactions().asSequence().toList().map { it.content.toList() }}")
        instance.consensus.performConsensusRound(true)

        println("instance.chain.getPersistedTransactions().asSequence().toList().map { it.content.toList() } = ${instance.chain.getPersistedTransactions().asSequence().toList().map { it.content.toList() }}")
    }

    @Test
    fun testIntermediateEqualHashCausesFail() {
        val tx0 = Transaction(byteArrayOf(1, 2, 3, 4, 5))
        val tx1 = Transaction(byteArrayOf(1, 2, 3, 4, 6))
        val tx2 = Transaction(byteArrayOf(1, 2, 3, 4, 7))
        run {
            val instance = Mockchain(EmptyApplication())
            instance.consensus as ManualConsensusAlgorithm

            instance.commitToMemPool(tx0)

            //tx0 becomes (1,2,3,4) - tx1 becomes (1,2,3,4,6)
            instance.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.REPLACES_PARTIAL)))

            //tx1 becomes (1,2,3,4) WHICH IS WHAT TX0 IS - should fail
            instance.commitToMemPool(Transaction(tx2.content, Dependency(tx1.hash, DependencyType.REPLACES_PARTIAL)))

            instance.consensus.performConsensusRound(false)
//        assertEquals(2, instance.chain.persistedTxCount())
            instance.consensus.performConsensusRound(true)
            assertEquals(2, instance.chain.persistedTxCount())
        }

        run {
            val instance = Mockchain(EmptyApplication())
            instance.consensus as ManualConsensusAlgorithm

            instance.commitToMemPool(tx0)

            //tx0 becomes (1,2,3,4) - tx1 becomes (1,2,3,4,6)
            instance.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.REPLACES_PARTIAL)))

            instance.consensus.performConsensusRound(true)
            //tx1 becomes (1,2,3,4) WHICH IS WHAT TX0 IS - should fail
            instance.commitToMemPool(Transaction(tx2.content, Dependency(tx1.hash, DependencyType.REPLACES_PARTIAL)))

            instance.consensus.performConsensusRound(false)
            assertEquals(2, instance.chain.persistedTxCount())
            instance.consensus.performConsensusRound(true)
            assertEquals(2, instance.chain.persistedTxCount())
        }
    }

    @Test
    fun testHighlyFringeCaseInWhichPartialRejectionCausesValidationToBeDifferentFromSquash() {
        val instance = Mockchain(EmptyApplication())
        instance.consensus as ManualConsensusAlgorithm

        instance.commitToMemPool(tx0)
        instance.commitToMemPool(tx1)
        instance.commitToMemPool(Transaction(tx2.content, Dependency(tx0.hash, DependencyType.REPLACES), Dependency(tx4.hash, DependencyType.REPLACES)))
        instance.commitToMemPool(Transaction(tx3.content, Dependency(tx0.hash, DependencyType.REPLACES))) //valid, because tx2 should be rejected, because it's dependency tx4 does not exist

        instance.consensus.performConsensusRound(false)
        assertEquals(3, instance.chain.persistedTxCount())
        instance.consensus.performConsensusRound(true)
        assertEquals(2, instance.chain.persistedTxCount())
    }

    //TODO:: test MORE interactions between dependencies - prohibit those that make no sense

    // BUILD-UPON + REPLACE
    @Test
    fun testReplacingBuildUponDependenciesWork() {
        val instance = Mockchain(EmptyApplication())
        instance.consensus as ManualConsensusAlgorithm

        instance.commitToMemPool(tx0)
        instance.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.BUILDS_UPON), Dependency(tx0.hash, DependencyType.REPLACES)))
        instance.commitToMemPool(Transaction(tx2.content, Dependency(tx1.hash, DependencyType.BUILDS_UPON), Dependency(tx1.hash, DependencyType.REPLACES)))

        instance.consensus.performConsensusRound(true)
        assertEquals(1, instance.chain.persistedTxCount())
    }

//    BUILD-UPON + PARTIAL_REPLACE

    @Test
    fun testPartialReplacingBuildUponDependenciesWork() {
        val instance = Mockchain(object: EmptyApplication() {
            override fun getBuildUponSquashHandler(): BuildUponSquashHandler = {_, c ->
                val new = c.clone()
                new[0]=123
                new
            }
        })
        instance.consensus as ManualConsensusAlgorithm

        val tx0 = Transaction(byteArrayOf(1,1,3,4,5))
        val tx1 = Transaction(byteArrayOf(2,2,3,4,5))
        val tx2 = Transaction(byteArrayOf(3,3,3,4,5))
        instance.commitToMemPool(tx0)
        instance.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.BUILDS_UPON), Dependency(tx0.hash, DependencyType.REPLACES_PARTIAL)))//because app does complete replace
        instance.commitToMemPool(Transaction(tx2.content, Dependency(tx1.hash, DependencyType.BUILDS_UPON), Dependency(tx1.hash, DependencyType.REPLACES_PARTIAL)))//because app does complete replace

        instance.consensus.performConsensusRound(false)
        assertEquals(3, instance.chain.persistedTxCount())
        instance.consensus.performConsensusRound(true)
        assertEquals(3, instance.chain.persistedTxCount())
        println("res = "+instance.chain.getPersistedTransactions().asSequence().toList().map { it.content.toList() })
        assertTrue(instance.chain.getPersistedTransactions().asSequence().toList().map { it.content }.any { it.contentEquals(byteArrayOf(1,1,3,4))})//what tx0 becomes
        assertTrue(instance.chain.getPersistedTransactions().asSequence().toList().map { it.content }.any { it.contentEquals(byteArrayOf(123,2,3,4))})//what tx1 becomes
        assertTrue(instance.chain.getPersistedTransactions().asSequence().toList().map { it.content }.any { it.contentEquals(byteArrayOf(123,3,3,4,5))})//what tx2 becomes
    }

    @Test
    fun testReplaceBeforeSequenceWorks() {
        val instance = Mockchain(EmptyApplication())
        instance.consensus as ManualConsensusAlgorithm

        instance.commitToMemPool(tx0)
        instance.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.REPLACES)))
        instance.commitToMemPool(Transaction(tx2.content, Dependency(tx1.hash, DependencyType.SEQUENCE_PART)))
        instance.commitToMemPool(Transaction(tx3.content, Dependency(tx2.hash, DependencyType.SEQUENCE_END)))

        instance.consensus.performConsensusRound(false)
        assertEquals(4, instance.chain.persistedTxCount())
        instance.consensus.performConsensusRound(true)
        assertEquals(1, instance.chain.persistedTxCount())
    }

    @Test
    fun testBuildUponSequencePartWorks() {
        val instance = Mockchain(EmptyApplication())
        instance.consensus as ManualConsensusAlgorithm

        instance.commitToMemPool(tx0)
        instance.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.SEQUENCE_PART)))
        instance.commitToMemPool(Transaction(tx2.content, Dependency(tx1.hash, DependencyType.BUILDS_UPON)))
        instance.consensus.performConsensusRound(false)//otherwise order is undefined and tx3 might be evaluated before tx2
        instance.commitToMemPool(Transaction(tx3.content, Dependency(tx1.hash, DependencyType.SEQUENCE_END)))

        instance.consensus.performConsensusRound(false)
        assertEquals(4, instance.chain.persistedTxCount())
        instance.consensus.performConsensusRound(true)
        assertEquals(2, instance.chain.persistedTxCount())
    }

    @Test
    fun testSequencePartDependencyAlteredWorks() {
        val instance = Mockchain(EmptyApplication())
        instance.consensus as ManualConsensusAlgorithm

        instance.commitToMemPool(tx4)
        instance.commitToMemPool(Transaction(tx0.content, Dependency(tx4.hash, DependencyType.SEQUENCE_END)))
        instance.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.SEQUENCE_PART)))

        instance.consensus.performConsensusRound(true) //here for the next line to work tx1's dependency will need to be automatically altered so it points to the new tx0 hash

        instance.commitToMemPool(Transaction(tx3.content, Dependency(tx1.hash, DependencyType.SEQUENCE_END)))

        instance.consensus.performConsensusRound(false)
        assertEquals(3, instance.chain.persistedTxCount())
        println("instance.chain.getPersistedTransactions().asSequence().toList().map { it.hash } = ${instance.chain.getPersistedTransactions().asSequence().toList().map { it.hash }}")
        println("instance.chain.getPersistedTransactions().asSequence().toList() = ${instance.chain.getPersistedTransactions().asSequence().toList()}")
        instance.consensus.performConsensusRound(true)
        println("instance.chain.getPersistedTransactions().asSequence().toList().map { it.hash } = ${instance.chain.getPersistedTransactions().asSequence().toList().map { it.hash }}")
        println("instance.chain.getPersistedTransactions().asSequence().toList() = ${instance.chain.getPersistedTransactions().asSequence().toList()}")
        assertEquals(1, instance.chain.persistedTxCount())
    }

    @Test
    fun testBuildUponSequenceEndWorks() {
        val instance = Mockchain(EmptyApplication())
        instance.consensus as ManualConsensusAlgorithm

        instance.commitToMemPool(tx0)
        instance.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.SEQUENCE_PART)))
        instance.commitToMemPool(Transaction(tx2.content, Dependency(tx1.hash, DependencyType.SEQUENCE_END)))
        instance.commitToMemPool(Transaction(tx3.content, Dependency(tx2.hash, DependencyType.BUILDS_UPON)))


        instance.consensus.performConsensusRound(false)
        assertEquals(4, instance.chain.persistedTxCount())
        instance.consensus.performConsensusRound(true)
        assertEquals(2, instance.chain.persistedTxCount())
    }

    @Test
    fun testSequenceOfSequencesWorks() {
        val instance = Mockchain(EmptyApplication())
        instance.consensus as ManualConsensusAlgorithm

        instance.commitToMemPool(tx0)
        instance.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.SEQUENCE_PART)))
        instance.commitToMemPool(Transaction(tx2.content, Dependency(tx1.hash, DependencyType.SEQUENCE_END)))
        instance.commitToMemPool(Transaction(tx3.content, Dependency(tx2.hash, DependencyType.SEQUENCE_PART)))
        instance.commitToMemPool(Transaction(tx4.content, Dependency(tx3.hash, DependencyType.SEQUENCE_END)))


        instance.consensus.performConsensusRound(false)
        assertEquals(5, instance.chain.persistedTxCount())
        instance.consensus.performConsensusRound(true)
        assertEquals(1, instance.chain.persistedTxCount())
    }

    //: Build-upon without replace partial or replace, does not make sense - OR DOES IT?????
    //    if it does not- then it never makes sense: If one builds upon, then one can directly build the new tx - but that would go against the idea of transactions changing the state
    //    because then the new tx would have to directly encode the state - with build upon it can then incrementally take on the new state (in the example of calculator)



    @Test
    fun testDoubleReplaceSecondTxOnlyDenied() {
        val app = EmptyApplication()
        val instance = Mockchain(app)

        instance.commitToMemPool(tx0)
        instance.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.REPLACES)))
        instance.commitToMemPool(Transaction(tx2.content, Dependency(tx0.hash, DependencyType.REPLACES)))

        val (_, denied) = jokrey.mockchain.squash.findChangesAndDeniedTransactions(instance.chain, app.getPartialReplaceSquashHandler(), app.getBuildUponSquashHandler(), app.getSequenceSquashHandler(), instance.memPool.getTransactions())

        println("denied = ${denied}")

        assertEquals(1, denied.size)
        assertTrue(tx1.content.contentEquals(denied[0].first.content) ||  tx2.content.contentEquals(denied[0].first.content)) //order is not strictly defined
    }

    @Test
    fun unfoundDependencyRelationOnPartialDelayedSquash_noLongerFails() {
        val app = EmptyApplication()
        val instance = Mockchain(app)
        instance.consensus as ManualConsensusAlgorithm

        //this emulates a more efficient chain consensus-squash algorithm that is then shown to not work
        //    the real chain algorithm DOES NOT work this way - this is just supposed to show why


        instance.commitToMemPool(tx0)
        instance.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.REPLACES)))
        instance.consensus.performConsensusRound(false) //if the squash is forced here (which is it when we use verifySubset, then this functions perfectly

        instance.commitToMemPool(Transaction(tx2.content, Dependency(tx0.hash, DependencyType.REPLACES)))

        //if we used verifyAll here this would work (i.e. correctly deny tx1
        val (_, denied) = jokrey.mockchain.squash.findChangesAndDeniedTransactions(instance.chain, app.getPartialReplaceSquashHandler(), app.getBuildUponSquashHandler(), app.getSequenceSquashHandler(), instance.memPool.getTransactions())

        assertEquals(1, denied.size)
        assertEquals(Transaction(tx2.content, Dependency(tx0.hash, DependencyType.REPLACES)), denied[0].first)

        val state = jokrey.mockchain.squash.findChanges(instance.chain, app.getPartialReplaceSquashHandler(), app.getBuildUponSquashHandler(), app.getSequenceSquashHandler(), null, instance.memPool.getTransactions())
        assertTrue(state.rejections.isNotEmpty())

    }


    @Test
    fun unfoundDependencyRelationOnPartialDelayedSquashWithSequences_noLongerFails() {
        run {
            val app = EmptyApplication()
            val instance = Mockchain(app)
            instance.consensus as ManualConsensusAlgorithm

            instance.commitToMemPool(tx0)
            instance.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.SEQUENCE_PART)))
            instance.consensus.performConsensusRound(true) //no squash occurs

            instance.commitToMemPool(Transaction(tx2.content, Dependency(tx0.hash, DependencyType.SEQUENCE_END)))

            val (_, denied) = jokrey.mockchain.squash.findChangesAndDeniedTransactions(instance.chain, app.getPartialReplaceSquashHandler(), app.getBuildUponSquashHandler(), app.getSequenceSquashHandler(), instance.memPool.getTransactions())


            assertEquals(1, denied.size)
            assertEquals(Transaction(tx2.content, Dependency(tx0.hash, DependencyType.SEQUENCE_END)), denied[0].first)

            val state = jokrey.mockchain.squash.findChanges(instance.chain, app.getPartialReplaceSquashHandler(), app.getBuildUponSquashHandler(), app.getSequenceSquashHandler(), null, instance.memPool.getTransactions())
            assertTrue(state.rejections.isNotEmpty())
        }




        run {
            val instance = Mockchain(EmptyApplication())
            instance.consensus as ManualConsensusAlgorithm

            //this emulates a more efficient chain consensus-squash algorithm that is then shown to not work
            //    the real chain algorithm DOES NOT work this way - this is just supposed to show why


            instance.consensus.squashEveryNRounds = 1

            instance.commitToMemPool(tx0)
            instance.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.SEQUENCE_PART)))
            instance.consensus.performConsensusRound(true)

            assertEquals(2, instance.chain.persistedTxCount())

            instance.commitToMemPool(Transaction(tx2.content, Dependency(tx0.hash, DependencyType.SEQUENCE_END)))

            instance.consensus.performConsensusRound(true)

            assertEquals(2, instance.chain.persistedTxCount()) //still correct, but now tx1(A PERSISTED TX) has a missing dependency

            println(instance.chain.getPersistedTransactions().asSequence().toList().toList())
            instance.chain.getPersistedTransactions().asSequence().toList().forEach { persistedTransaction ->
                persistedTransaction.bDependencies.forEach { dep ->
                    if (!instance.chain.contains(dep.txp))
                        println("tx($persistedTransaction)'s dependency(${dep.txp}) could not be found in the chain")
                    assertTrue(instance.chain.contains(dep.txp))
                }
            }
        }
    }


    @Test
    fun canReuseDeletedTransactionsHash() {

        run {
            val instance = Mockchain(object : EmptyApplication() {
                override fun getBuildUponSquashHandler(): BuildUponSquashHandler {
                    return { _, _ -> byteArrayOf(1, 2, 3) }
                }
            })
            instance.consensus as ManualConsensusAlgorithm

            val tx0 = Transaction(byteArrayOf(1, 2, 3))
            val tx1 = Transaction(byteArrayOf(1, 2, 3, 6))
            val tx2 = Transaction(byteArrayOf(1, 2, 3, 7))

            instance.commitToMemPool(tx0)
            //tx0 is removed, tx1 becomes (1,2,3)
            instance.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.REPLACES), Dependency(tx0.hash, DependencyType.BUILDS_UPON))) //build upon lets tx1 change to tx0 content - but has previously replaced tx

            //tx1 becomes (1,2) - tx2 becomes (1,2,3)
            instance.commitToMemPool(Transaction(tx2.content, Dependency(tx1.hash, DependencyType.REPLACES_PARTIAL), Dependency(tx1.hash, DependencyType.BUILDS_UPON)))

            println("mem: "+instance.memPool.getTransactionHashes())

            instance.consensus.performConsensusRound(true)

            println(instance.chain.getPersistedTransactions().asSequence().toList().map { it.hash })
            println(instance.chain.getPersistedTransactions().asSequence().toList())

            assertEquals(2, instance.chain.persistedTxCount())
            assertTrue(instance.chain.getPersistedTransactions().asSequence().toList().contains(Transaction(byteArrayOf(1, 2, 3))))
            assertTrue(instance.chain.getPersistedTransactions().asSequence().toList().contains(Transaction(byteArrayOf(1, 2))))
        }


        run {
            val instance = Mockchain(object : EmptyApplication() {
                override fun getBuildUponSquashHandler(): BuildUponSquashHandler {
                    return { _, _ -> byteArrayOf(1, 2, 3) }
                }
            })
            instance.consensus as ManualConsensusAlgorithm

            val tx0 = Transaction(byteArrayOf(1, 2, 3))
            val tx1 = Transaction(byteArrayOf(1, 2, 3, 6))
            val tx2 = Transaction(byteArrayOf(1, 2, 3, 7))

            instance.commitToMemPool(tx0)
            instance.consensus.performConsensusRound(true)
            instance.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.REPLACES), Dependency(tx0.hash, DependencyType.BUILDS_UPON))) //build upon lets tx1 change to tx0 content - but has previously replaced tx

            instance.consensus.performConsensusRound(true)
            //logically the dependencies here are to tx1, but tx1 has since changed to tx0 - within the squash algorithm this is handled, but here we are the application and have to handle that ourselves
            instance.commitToMemPool(Transaction(tx2.content, Dependency(tx0.hash, DependencyType.REPLACES_PARTIAL), Dependency(tx0.hash, DependencyType.BUILDS_UPON)))
            instance.consensus.performConsensusRound(true)

            println("mem: "+instance.memPool.getTransactionHashes())


            println(instance.chain.getPersistedTransactions().asSequence().toList().map { it.hash })
            println(instance.chain.getPersistedTransactions().asSequence().toList())

            assertTrue(instance.chain.persistedTxCount() == 2)
            assertTrue(instance.chain.getPersistedTransactions().asSequence().toList().contains(Transaction(byteArrayOf(1, 2, 3))))
            assertTrue(instance.chain.getPersistedTransactions().asSequence().toList().contains(Transaction(byteArrayOf(1, 2))))
        }

    }

    @Test
    fun flipHashIsDenied() {
        //situation in which hashes become


        val instance = Mockchain(object : EmptyApplication() {
            override fun getPartialReplaceSquashHandler(): PartialReplaceSquashHandler {
                return { _, _ -> byteArrayOf(1, 2, 3) }
            }
            override fun getBuildUponSquashHandler(): BuildUponSquashHandler {
                return { _, _ -> byteArrayOf(1, 2, 3, 4) }
            }
        })
        instance.consensus as ManualConsensusAlgorithm

        val tx0 = Transaction(byteArrayOf(1, 2, 3, 4))
        val tx1 = Transaction(byteArrayOf(1, 2, 3),
                Dependency(tx0.hash, DependencyType.REPLACES_PARTIAL), //so tx0 becomes 1,2,3,4
                Dependency(tx0.hash, DependencyType.BUILDS_UPON))      //so tx1 becomes 1,2,3

        instance.commitToMemPool(tx0)
        instance.commitToMemPool(tx1) //build upon lets tx1 change to tx0 content - but has previously replaced tx

        println("mem: "+instance.memPool.getTransactionHashes())

        instance.consensus.performConsensusRound(false)

        assertEquals(1, instance.chain.persistedTxCount())
        assertArrayEquals(byteArrayOf(1,2,3,4), instance.chain.getPersistedTransactions().asSequence().toList().toList()[0].content)
    }

    @Test
    fun flipHashIsDenied_Persistent() {
        //situation in which hashes become


        val instance = Mockchain(object : EmptyApplication() {
            override fun getPartialReplaceSquashHandler(): PartialReplaceSquashHandler {
                return { _, _ -> byteArrayOf(1, 2, 3) }
            }
            override fun getBuildUponSquashHandler(): BuildUponSquashHandler {
                return { _, _ -> byteArrayOf(1, 2, 3, 4) }
            }
        }, store = PersistentStorage(File(System.getProperty("user.home") + "/Desktop/flipHashIsDenied_Persistent_testDir"), true))
        instance.consensus as ManualConsensusAlgorithm

        val tx0 = Transaction(byteArrayOf(1, 2, 3, 4))
        val tx1 = Transaction(byteArrayOf(1, 2, 3),
                Dependency(tx0.hash, DependencyType.REPLACES_PARTIAL), //so tx0 becomes 1,2,3,4
                Dependency(tx0.hash, DependencyType.BUILDS_UPON))      //so tx1 becomes 1,2,3

        instance.commitToMemPool(tx0)
        instance.commitToMemPool(tx1) //build upon lets tx1 change to tx0 content - but has previously replaced tx

        println("mem: "+instance.memPool.getTransactionHashes())

        instance.consensus.performConsensusRound(false)

        assertEquals(1, instance.chain.persistedTxCount())
        assertArrayEquals(byteArrayOf(1,2,3,4), instance.chain.getPersistedTransactions().asSequence().toList()[0].content)
    }

    @Test
    fun canReuseHashThatHasJustBeenChangedWithinThisTransaction() {

        run {
            val instance = Mockchain(object : EmptyApplication() {
//                override fun getBuildUponSquashHandler(): BuildUponSquashHandler {
//                    return { _, _ -> byteArrayOf(1, 2, 3) }
//                }
//                override fun getPartialReplaceSquashHandler(): PartialReplaceSquashHandler {
//                    return { _, _ -> byteArrayOf(1, 2) }
//                }
            })
            instance.consensus as ManualConsensusAlgorithm

            val tx0 = Transaction(byteArrayOf(1, 2, 3))
            val tx1 = Transaction(byteArrayOf(1, 2, 3, 4))
            val tx2 = Transaction(byteArrayOf(1, 2, 3, 7))

            instance.commitToMemPool(tx0)
            instance.commitToMemPool(tx1)
            instance.consensus.performConsensusRound(false)

            //problem can (or did once) occur when replace-partial free'd a hash that is calculated by(the results of) a previous partial-replace
            //here: tx1(1,2,3,4) -> (1, 2, 3) and tx0(1,2,3) -> (1,2) (by partial)
            instance.commitToMemPool(Transaction(tx2.content, Dependency(tx1.hash, DependencyType.REPLACES_PARTIAL), Dependency(tx0.hash, DependencyType.REPLACES_PARTIAL)))

            println("mem: "+instance.memPool.getTransactionHashes())

            instance.consensus.performConsensusRound(true)
            println("pers after: "+instance.chain.getPersistedTransactions().asSequence().toList().map { it.hash })
            println("pers after: "+instance.chain.getPersistedTransactions().asSequence().toList())

            assertEquals(3, instance.chain.persistedTxCount())
            assertTrue(instance.chain.getPersistedTransactions().asSequence().toList().map { it.content }.any { it.contentEquals(byteArrayOf(1,2,3))})
            assertTrue(instance.chain.getPersistedTransactions().asSequence().toList().map { it.content }.any { it.contentEquals(byteArrayOf(1,2))})
            assertTrue(instance.chain.getPersistedTransactions().asSequence().toList().map { it.content }.any { it.contentEquals(byteArrayOf(1,2, 3, 7))})


        }

    }

//    @Test
//    fun testWrongInternalTxLevelCalculationsCauseNoIssues() {
//        //the problem is within the internal dependency level sort:
//        //    with partial it is possible that a tx0 is in block1, but is only depended on by a single tx1
//        //      then a tx2 and tx3 may be in block2, but tx2 be depended on by tx3 and tx1 in turn on tx3 (tx1 -> tx3 -> tx2)
//        //      tx0 is technically before tx2, but tx2 has a higher level and is therefore before tx0 in the sorting algorithm
//        //   ::  IS THIS AN ISSUE THOUGH??
//        //           tx1 and tx3 have no relation - except over their very parent - but all calculations that the parent requires have been done
//        //
//        //   This problem does NOT exist for squash with all tx
//
//        //the algorithm that solves this is simple, but very, very inefficient since it requires looking at all blocks
//        //this is supposed to prove that it is NOT required to solve this, since it has not practical implications
//        //  it is especially irrelevant, because the handlers are idempotent - i.e. do not have a state - do the order in which they are called is not relevant
//
//
//        val app = EmptyApplication()
//        val instance = Mockchain(app)
//
//
//        instance.commitToMemPool(tx0)
//        instance.consensus.performConsensusRound(true)
//
//        instance.commitToMemPool(Transaction(tx2.content))
//        instance.commitToMemPool(Transaction(tx3.content, Dependency(tx2.hash, DependencyType.BUILDS_UPON), Dependency(tx2.hash, DependencyType.REPLACES_PARTIAL)))
//        instance.commitToMemPool(Transaction(tx4.content, Dependency(tx3.hash, DependencyType.BUILDS_UPON), Dependency(tx3.hash, DependencyType.REPLACES_PARTIAL)))
//
//        instance.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.BUILDS_UPON), Dependency(tx0.hash, DependencyType.REPLACES_PARTIAL),
//                                                       Dependency(tx4.hash, DependencyType.BUILDS_UPON), Dependency(tx4.hash, DependencyType.REPLACES_PARTIAL)))
//
//        val denied = squash.verifySubset(chain, app.getPartialReplaceSquashHandler(), app.getBuildUponSquashHandler(), app.getSequenceSquashHandler(), *chain.getMemPoolContent())
//
//        assertTrue(denied.isEmpty())
//
//        val new = squash.squashCompleteSubset(chain, app.getPartialReplaceSquashHandler(), app.getBuildUponSquashHandler(), app.getSequenceSquashHandler(), *chain.getMemPoolContent())
//
//        assertTrue(new.size >= 5)
//    }
}