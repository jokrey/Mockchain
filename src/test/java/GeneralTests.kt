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
        val chain = Chain(EmptyApplication())

        chain.commitToMemPool(tx0)
        chain.commitToMemPool(tx1)
        chain.commitToMemPool(tx2)
        chain.commitToMemPool(tx3)
        chain.commitToMemPool(tx4)

        chain.performConsensusRound(true)
        assertEquals(5, chain.getPersistedTransactions().asSequence().toList().size)
    }

    @Test
    fun testSimpleBuildUponDependenciesWork() {
        val chain = Chain(EmptyApplication())

        chain.commitToMemPool(tx0)
        chain.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.BUILDS_UPON)))
        chain.commitToMemPool(Transaction(tx2.content, Dependency(tx1.hash, DependencyType.BUILDS_UPON)))
        chain.commitToMemPool(Transaction(tx3.content, Dependency(tx2.hash, DependencyType.BUILDS_UPON)))
        chain.commitToMemPool(Transaction(tx4.content, Dependency(tx3.hash, DependencyType.BUILDS_UPON)))

        chain.performConsensusRound(true)
        assertEquals(5, chain.getPersistedTransactions().asSequence().toList().size) //as with build upon they don't actually remove anything, but just cause a call to the application to alter the newer tx
    }

    @Test
    fun testDoubleDependenciesDenied() {
        val chain = Chain(EmptyApplication())

        chain.commitToMemPool(tx0)
        chain.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.BUILDS_UPON), Dependency(tx0.hash, DependencyType.BUILDS_UPON)))

        chain.performConsensusRound(true)
        assertEquals(1, chain.getPersistedTransactions().asSequence().toList().size)
    }

    @Test
    fun testSimpleReplaceDependenciesWork() {
        val chain = Chain(EmptyApplication())

        chain.commitToMemPool(tx0)
        chain.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.REPLACES)))
        chain.commitToMemPool(Transaction(tx2.content, Dependency(tx1.hash, DependencyType.REPLACES)))
        chain.commitToMemPool(Transaction(tx3.content, Dependency(tx2.hash, DependencyType.REPLACES)))
        chain.commitToMemPool(Transaction(tx4.content, Dependency(tx3.hash, DependencyType.REPLACES)))

        chain.performConsensusRound(true)
        assertEquals(1, chain.getPersistedTransactions().asSequence().toList().size)
        assertArrayEquals(tx4.content, chain.getPersistedTransactions().asSequence().toList().first().content)
    }


    @Test
    fun testSimpleSequenceDependenciesWork() {
        val chain = Chain(EmptyApplication())

        chain.commitToMemPool(tx0)
        chain.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.SEQUENCE_PART)))
        chain.commitToMemPool(Transaction(tx2.content, Dependency(tx1.hash, DependencyType.SEQUENCE_PART)))

        chain.performConsensusRound(false)
        assertEquals(3, chain.getPersistedTransactions().asSequence().toList().size)

        chain.commitToMemPool(Transaction(tx3.content, Dependency(tx2.hash, DependencyType.SEQUENCE_PART)))
        chain.commitToMemPool(Transaction(tx4.content, Dependency(tx3.hash, DependencyType.SEQUENCE_END)))

        chain.performConsensusRound(true)
        assertEquals(1, chain.getPersistedTransactions().asSequence().toList().size)
    }

    @Test
    fun testUnresolvedDependencyCausesDenial() {
        val chain = Chain(EmptyApplication())

        chain.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.REPLACES)))

        chain.performConsensusRound()
        assertTrue(chain.getPersistedTransactions().asSequence().toList().isEmpty())
    }

    @Test
    fun testChangeReintroductionToChainMaintainsCorrectHashChain() {
        val chain = Chain(EmptyApplication())
        chain.commitToMemPool(Transaction(contentIsArbitrary()))
        chain.commitToMemPool(tx0)
        chain.performConsensusRound()
        chain.commitToMemPool(Transaction(contentIsArbitrary()))
        chain.performConsensusRound()
        chain.commitToMemPool(Transaction(contentIsArbitrary()))
        chain.performConsensusRound()
        chain.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.BUILDS_UPON), Dependency(tx0.hash, DependencyType.REPLACES)))
        chain.performConsensusRound()
        chain.commitToMemPool(Transaction(tx2.content, Dependency(tx1.hash, DependencyType.BUILDS_UPON), Dependency(tx1.hash, DependencyType.REPLACES)))
        chain.commitToMemPool(Transaction(contentIsArbitrary()))
        chain.performConsensusRound()
        chain.commitToMemPool(Transaction(contentIsArbitrary()))
        chain.performConsensusRound()
        chain.commitToMemPool(Transaction(contentIsArbitrary()))
        chain.performConsensusRound()
        chain.commitToMemPool(Transaction(tx3.content, Dependency(tx2.hash, DependencyType.BUILDS_UPON), Dependency(tx2.hash, DependencyType.REPLACES)))
        chain.performConsensusRound()
        chain.commitToMemPool(Transaction(tx4.content, Dependency(tx3.hash, DependencyType.BUILDS_UPON), Dependency(tx3.hash, DependencyType.REPLACES)))
        chain.performConsensusRound()

        val previous = chain.getLatestHash()

        chain.performConsensusRound(true)

        assertNotEquals(previous, chain.getLatestHash())
        assertTrue(chain.validateHashChain())
    }

    @Test
    fun testChangeReintroductionToChainMaintainsCorrectHashChain_Persistent() {
        val chain = Chain(EmptyApplication(), store = PersistentStorage(File(System.getProperty("user.home") + "/Desktop/blockStoreTest"), true))
        chain.commitToMemPool(Transaction(contentIsArbitrary()))
        chain.commitToMemPool(tx0)
        chain.performConsensusRound()
        chain.commitToMemPool(Transaction(contentIsArbitrary()))
        chain.performConsensusRound()
        chain.commitToMemPool(Transaction(contentIsArbitrary()))
        chain.performConsensusRound()
        chain.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.BUILDS_UPON), Dependency(tx0.hash, DependencyType.REPLACES)))
        chain.performConsensusRound()
        chain.commitToMemPool(Transaction(tx2.content, Dependency(tx1.hash, DependencyType.BUILDS_UPON), Dependency(tx1.hash, DependencyType.REPLACES)))
        chain.commitToMemPool(Transaction(contentIsArbitrary()))
        chain.performConsensusRound()
        chain.commitToMemPool(Transaction(contentIsArbitrary()))
        chain.performConsensusRound()
        chain.commitToMemPool(Transaction(contentIsArbitrary()))
        chain.performConsensusRound()
        chain.commitToMemPool(Transaction(tx3.content, Dependency(tx2.hash, DependencyType.BUILDS_UPON), Dependency(tx2.hash, DependencyType.REPLACES)))
        chain.performConsensusRound()
        chain.commitToMemPool(Transaction(tx4.content, Dependency(tx3.hash, DependencyType.BUILDS_UPON), Dependency(tx3.hash, DependencyType.REPLACES)))
        chain.performConsensusRound()

        val previous = chain.getLatestHash()

        chain.performConsensusRound(true)

        assertNotEquals(previous, chain.getLatestHash())
        assertTrue(chain.validateHashChain())
    }

    @Test
    fun testReflexiveDependencyIsBlocked() {
        val chain = Chain(EmptyApplication())

        chain.commitToMemPool(Transaction(tx1.content, Dependency(tx1.hash, DependencyType.REPLACES)))

        chain.performConsensusRound()
        assertTrue(chain.getPersistedTransactions().asSequence().toList().isEmpty())
    }

    @Test
    fun testSimpleDependencyLoopIsBlocked() {
        val chain = Chain(EmptyApplication())

        chain.commitToMemPool(Transaction(tx1.content, Dependency(tx2.hash, DependencyType.REPLACES)))
        chain.commitToMemPool(Transaction(tx2.content, Dependency(tx1.hash, DependencyType.REPLACES)))

        chain.performConsensusRound()
        assertTrue(chain.getPersistedTransactions().asSequence().toList().isEmpty())
    }

    @Test
    fun testComplexDependencyLoopIsBlocked() {
        val chain = Chain(EmptyApplication())

        chain.commitToMemPool(Transaction(tx1.content, Dependency(tx4.hash, DependencyType.REPLACES)))
        chain.commitToMemPool(Transaction(tx2.content, Dependency(tx1.hash, DependencyType.REPLACES)))
        chain.commitToMemPool(Transaction(tx3.content, Dependency(tx2.hash, DependencyType.REPLACES)))
        chain.commitToMemPool(Transaction(tx4.content, Dependency(tx3.hash, DependencyType.REPLACES)))
        chain.commitToMemPool(Transaction(tx0.content, Dependency(tx2.hash, DependencyType.REPLACES))) //a dependency on a dependency loop is rightfully denied

        chain.performConsensusRound()
        assertTrue(chain.getPersistedTransactions().asSequence().toList().isEmpty())
    }

    @Test
    fun testDependencyLoopDoesNotEliminateLegalTx() {
        val chain = Chain(EmptyApplication())

        chain.commitToMemPool(Transaction(tx0.content))

        //The following is required to NOT cause tx0 to be denied, otherwise this would be an exploit to get any tx in the mem pool denied
        chain.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.REPLACES), Dependency(tx2.hash, DependencyType.REPLACES)))
        chain.commitToMemPool(Transaction(tx2.content, Dependency(tx1.hash, DependencyType.REPLACES)))

        chain.performConsensusRound()
        assertEquals(tx0, chain.getPersistedTransactions().asSequence().toList().first())
        assertEquals(1, chain.getPersistedTransactions().asSequence().toList().size)
    }

    @Test
    fun testDoubleReplaceDenied() {
        val chain = Chain(EmptyApplication())

        chain.commitToMemPool(tx0)
        chain.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.REPLACES)))
        chain.commitToMemPool(Transaction(tx2.content, Dependency(tx0.hash, DependencyType.REPLACES)))

        chain.performConsensusRound(true)
        assertEquals(1, chain.getPersistedTransactions().asSequence().toList().size)
        assertTrue( tx1 == chain.getPersistedTransactions().asSequence().toList().first() ||
                    tx2 == chain.getPersistedTransactions().asSequence().toList().first())
    }

    @Test
    fun doubleHashBlocked() {
        val chain = Chain(EmptyApplication())

        chain.commitToMemPool(tx0)

        assertThrows(IllegalArgumentException::class.java) {
            chain.commitToMemPool(tx0)
        }
    }

    @Test
    fun doubleHashAlsoBlockedIfOriginatesInSquash() {
        val chain = Chain(object: EmptyApplication() {
            override fun getBuildUponSquashHandler(): BuildUponSquashHandler = {_, _ -> tx0.content}
        })

        chain.commitToMemPool(tx0)
        chain.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.BUILDS_UPON)))

        chain.performConsensusRound()

        assertEquals(1, chain.getPersistedTransactions().asSequence().toList().size)

        chain.performConsensusRound(true)
    }

    @Test
    fun doubleHashAlsoBlockedIfOriginatesInSquashAndDependencies() {
        val tx0 = Transaction(byteArrayOf(0,0,0,0))
        val tx1 = Transaction(byteArrayOf(1,1,1,1))

        val chain = Chain(object: EmptyApplication() {
            override fun getBuildUponSquashHandler(): BuildUponSquashHandler = {_, _ -> tx0.content}
        })

        chain.commitToMemPool(tx0)
        chain.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.BUILDS_UPON)))
        chain.commitToMemPool(Transaction(tx2.content, Dependency(tx1.hash, DependencyType.REPLACES_PARTIAL)))

        chain.performConsensusRound()

        assertEquals(1, chain.getPersistedTransactions().asSequence().toList().size)

        chain.performConsensusRound(true)
    }

    @Test
    fun doubleHashAlsoBlockedIfOriginatesInSquashAndPartlyPersisted() {
        val chain = Chain(object: EmptyApplication() {
            override fun getBuildUponSquashHandler(): BuildUponSquashHandler = {_, _ -> tx0.content}
        })

        chain.commitToMemPool(tx0)
        chain.performConsensusRound()
        assertEquals(1, chain.getPersistedTransactions().asSequence().toList().size)

        chain.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.BUILDS_UPON)))

        chain.performConsensusRound()

        assertEquals(1, chain.getPersistedTransactions().asSequence().toList().size)
    }

    @Test
    fun testIllegalSequencesNto1() {
        val chain = Chain(EmptyApplication())

        chain.commitToMemPool(tx0)
        chain.commitToMemPool(tx1)
        chain.commitToMemPool(Transaction(tx2.content, Dependency(tx1.hash, DependencyType.SEQUENCE_PART), Dependency(tx0.hash, DependencyType.SEQUENCE_PART)))

        chain.performConsensusRound()
        assertEquals(2, chain.getPersistedTransactions().asSequence().toList().size)
    }

    @Test
    fun testIllegalSequences1toN() {
        val chain = Chain(EmptyApplication())

        chain.commitToMemPool(tx0)
        chain.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.SEQUENCE_PART)))
        chain.commitToMemPool(Transaction(tx2.content, Dependency(tx0.hash, DependencyType.SEQUENCE_PART)))
        chain.commitToMemPool(Transaction(tx3.content, Dependency(tx1.hash, DependencyType.SEQUENCE_END)))
        chain.commitToMemPool(Transaction(tx4.content, Dependency(tx2.hash, DependencyType.SEQUENCE_END)))

        chain.performConsensusRound()
        assertEquals(3, chain.getPersistedTransactions().asSequence().toList().size)
        chain.performConsensusRound(true)
        assertEquals(1, chain.getPersistedTransactions().asSequence().toList().size)
    }

    @Test
    fun testReplaceSequencePartIsIllegal() {
        val chain = Chain(EmptyApplication())

        chain.commitToMemPool(tx0)
        chain.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.SEQUENCE_PART)))
        chain.commitToMemPool(Transaction(tx2.content, Dependency(tx1.hash, DependencyType.REPLACES)))
        chain.commitToMemPool(Transaction(tx3.content, Dependency(tx1.hash, DependencyType.SEQUENCE_END)))

        chain.performConsensusRound()

        println("chain.getPersistedTransactions().asSequence().toList().toList() = ${chain.getPersistedTransactions().asSequence().toList().toList()}")

        assertEquals(3, chain.getPersistedTransactions().asSequence().toList().size)
        chain.performConsensusRound(true)
        println("chain.getPersistedTransactions().asSequence().toList().toList() = ${chain.getPersistedTransactions().asSequence().toList().toList()}")
        assertEquals(1, chain.getPersistedTransactions().asSequence().toList().size)
    }

    @Test
    fun testIntermediateEqualHashWithinTxIsIgnored() {

        val chainCreator = { Chain(object: EmptyApplication() {
            override fun getBuildUponSquashHandler(): BuildUponSquashHandler = {_, c ->
                val new = c.clone()
                new[0]=123
                new
            }
        })}

        var chain = chainCreator()

        val tx0 = Transaction(byteArrayOf(1,2,3,4,5))
        val tx1 = Transaction(byteArrayOf(2,2,3,4,5))
        val tx2 = Transaction(byteArrayOf(3,2,3,4,5))
        val willBeTx1 = Transaction(byteArrayOf(123,2,3,4,5))
        chain.commitToMemPool(tx0)

        //tx0 becomes (1,2,3,4) - tx1 becomes (123,2,3,4,5)
        chain.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.BUILDS_UPON), Dependency(tx0.hash, DependencyType.REPLACES_PARTIAL)))

        //tx1 becomes (123,2,3,4) - tx2 becomes (123,2,3,4,5) [[[WHICH IS WHAT TX1 WAS - but no longer is, by the end of this ]]]
        chain.commitToMemPool(Transaction(tx2.content, Dependency(tx1.hash, DependencyType.BUILDS_UPON), Dependency(tx1.hash, DependencyType.REPLACES_PARTIAL)))

        chain.performConsensusRound()
        assertEquals(3, chain.getPersistedTransactions().asSequence().toList().size)
        chain.performConsensusRound(true)
        assertEquals(3, chain.getPersistedTransactions().asSequence().toList().size) //squash does changes, as checked in the following, but it does not replace a tx
//        assertEquals(123, chain.getPersistedTransactions().asSequence().toList()[1].content[0])
//        assertEquals(4, chain.getPersistedTransactions().asSequence().toList()[0].content.size)
//        assertEquals(4, chain.getPersistedTransactions().asSequence().toList()[1].content.size)
//        assertTrue(chain.getPersistedTransactions().asSequence().toList()[0].content.size < chain.getPersistedTransactions().asSequence().toList()[2].content.size)



        //assert that the result is the same if we squash in between - i.e. the incremental behaviour of the squash algorithm is given
        chain = chainCreator()


        chain.commitToMemPool(tx0)

        //tx0 becomes (1,2,3,4) - tx1 becomes (123,2,3,4,5)
        chain.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.BUILDS_UPON), Dependency(tx0.hash, DependencyType.REPLACES_PARTIAL)))

        chain.performConsensusRound(true)
        //tx1 becomes (123,2,3,4) - tx2 becomes (123,2,3,4,5)
        chain.commitToMemPool(Transaction(tx2.content, Dependency(willBeTx1.hash, DependencyType.BUILDS_UPON), Dependency(willBeTx1.hash, DependencyType.REPLACES_PARTIAL)))

        chain.performConsensusRound()
        assertEquals(3, chain.getPersistedTransactions().asSequence().toList().size)
        println("persisted prior to squash (hashes) : "+chain.getPersistedTransactions().asSequence().toList().map { it.hash })
        println("persisted prior to squash : "+chain.getPersistedTransactions().asSequence().toList())
        chain.performConsensusRound(true)
        println("persisted AFTER to squash (hashes) : "+chain.getPersistedTransactions().asSequence().toList().map { it.hash })
        println("persisted AFTER to squash : "+chain.getPersistedTransactions().asSequence().toList())
        assertEquals(3, chain.getPersistedTransactions().asSequence().toList().size) //squash does changes, as checked in the following, but it does not replace a tx
//        assertEquals(123, chain.getPersistedTransactions().asSequence().toList()[1].content[0])
//        assertEquals(4, chain.getPersistedTransactions().asSequence().toList()[0].content.size)
//        assertEquals(4, chain.getPersistedTransactions().asSequence().toList()[1].content.size)
//        assertTrue(chain.getPersistedTransactions().asSequence().toList()[0].content.size < chain.getPersistedTransactions().asSequence().toList()[2].content.size)
    }

    @Test
    fun testTxMakesTwoChangesThatCauseTheSameHashAreRejected() {
        val chain = Chain(object: EmptyApplication() {
            override fun getBuildUponSquashHandler(): BuildUponSquashHandler {
                return {_, _ -> byteArrayOf(1,2,3)}
            }
        })

        val tx0 = Transaction(byteArrayOf(1,2,3,5))
        val tx1 = Transaction(byteArrayOf(1,2,3,6))

        chain.commitToMemPool(tx0)
        chain.commitToMemPool(tx1)

        chain.commitToMemPool(Transaction(tx2.content, Dependency(tx0.hash, DependencyType.REPLACES_PARTIAL), Dependency(tx1.hash, DependencyType.REPLACES_PARTIAL)))
        println("chain.getPersistedTransactions().asSequence().toList().map { it.content.toList() } = ${chain.getPersistedTransactions().asSequence().toList().map { it.content.toList() }}")

        chain.performConsensusRound()
        println("chain.getPersistedTransactions().asSequence().toList().map { it.content.toList() } = ${chain.getPersistedTransactions().asSequence().toList().map { it.content.toList() }}")
        chain.performConsensusRound(true)

        println("chain.getPersistedTransactions().asSequence().toList().map { it.content.toList() } = ${chain.getPersistedTransactions().asSequence().toList().map { it.content.toList() }}")
    }

    @Test
    fun testIntermediateEqualHashCausesFail() {
        var chain = Chain(EmptyApplication())

        val tx0 = Transaction(byteArrayOf(1,2,3,4,5))
        val tx1 = Transaction(byteArrayOf(1,2,3,4,6))
        val tx2 = Transaction(byteArrayOf(1,2,3,4,7))
        chain.commitToMemPool(tx0)

        //tx0 becomes (1,2,3,4) - tx1 becomes (1,2,3,4,6)
        chain.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.REPLACES_PARTIAL)))

        //tx1 becomes (1,2,3,4) WHICH IS WHAT TX0 IS - should fail
        chain.commitToMemPool(Transaction(tx2.content, Dependency(tx1.hash, DependencyType.REPLACES_PARTIAL)))

        chain.performConsensusRound()
//        assertEquals(2, chain.getPersistedTransactions().asSequence().toList().size)
        chain.performConsensusRound(true)
        assertEquals(2, chain.getPersistedTransactions().asSequence().toList().size)


        chain = Chain(EmptyApplication())

        chain.commitToMemPool(tx0)

        //tx0 becomes (1,2,3,4) - tx1 becomes (1,2,3,4,6)
        chain.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.REPLACES_PARTIAL)))

        chain.performConsensusRound(true)
        //tx1 becomes (1,2,3,4) WHICH IS WHAT TX0 IS - should fail
        chain.commitToMemPool(Transaction(tx2.content, Dependency(tx1.hash, DependencyType.REPLACES_PARTIAL)))

        chain.performConsensusRound()
        assertEquals(2, chain.getPersistedTransactions().asSequence().toList().size)
        chain.performConsensusRound(true)
        assertEquals(2, chain.getPersistedTransactions().asSequence().toList().size)
    }

    @Test
    fun testHighlyFringeCaseInWhichPartialRejectionCausesValidationToBeDifferentFromSquash() {
        val chain = Chain(EmptyApplication())

        chain.commitToMemPool(tx0)
        chain.commitToMemPool(tx1)
        chain.commitToMemPool(Transaction(tx2.content, Dependency(tx0.hash, DependencyType.REPLACES), Dependency(tx4.hash, DependencyType.REPLACES)))
        chain.commitToMemPool(Transaction(tx3.content, Dependency(tx0.hash, DependencyType.REPLACES))) //valid, because tx2 should be rejected, because it's dependency tx4 does not exist

        chain.performConsensusRound()
        assertEquals(3, chain.getPersistedTransactions().asSequence().toList().size)
        chain.performConsensusRound(true)
        assertEquals(2, chain.getPersistedTransactions().asSequence().toList().size)
    }

    //TODO:: test MORE interactions between dependencies - prohibit those that make no sense

    // BUILD-UPON + REPLACE
    @Test
    fun testReplacingBuildUponDependenciesWork() {
        val chain = Chain(EmptyApplication())

        chain.commitToMemPool(tx0)
        chain.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.BUILDS_UPON), Dependency(tx0.hash, DependencyType.REPLACES)))
        chain.commitToMemPool(Transaction(tx2.content, Dependency(tx1.hash, DependencyType.BUILDS_UPON), Dependency(tx1.hash, DependencyType.REPLACES)))

        chain.performConsensusRound(true)
        assertEquals(1, chain.getPersistedTransactions().asSequence().toList().size)
    }

//    BUILD-UPON + PARTIAL_REPLACE

    @Test
    fun testPartialReplacingBuildUponDependenciesWork() {
        val chain = Chain(object: EmptyApplication() {
            override fun getBuildUponSquashHandler(): BuildUponSquashHandler = {_, c ->
                val new = c.clone()
                new[0]=123
                new
            }
        })

        val tx0 = Transaction(byteArrayOf(1,1,3,4,5))
        val tx1 = Transaction(byteArrayOf(2,2,3,4,5))
        val tx2 = Transaction(byteArrayOf(3,3,3,4,5))
        chain.commitToMemPool(tx0)
        chain.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.BUILDS_UPON), Dependency(tx0.hash, DependencyType.REPLACES_PARTIAL)))//because app does complete replace
        chain.commitToMemPool(Transaction(tx2.content, Dependency(tx1.hash, DependencyType.BUILDS_UPON), Dependency(tx1.hash, DependencyType.REPLACES_PARTIAL)))//because app does complete replace

        chain.performConsensusRound()
        assertEquals(3, chain.getPersistedTransactions().asSequence().toList().size)
        chain.performConsensusRound(true)
        assertEquals(3, chain.getPersistedTransactions().asSequence().toList().size)
        println("res = "+chain.getPersistedTransactions().asSequence().toList().map { it.content.toList() })
        assertTrue(chain.getPersistedTransactions().asSequence().toList().map { it.content }.any { it.contentEquals(byteArrayOf(1,1,3,4))})//what tx0 becomes
        assertTrue(chain.getPersistedTransactions().asSequence().toList().map { it.content }.any { it.contentEquals(byteArrayOf(123,2,3,4))})//what tx1 becomes
        assertTrue(chain.getPersistedTransactions().asSequence().toList().map { it.content }.any { it.contentEquals(byteArrayOf(123,3,3,4,5))})//what tx2 becomes
    }

    @Test
    fun testReplaceBeforeSequenceWorks() {
        val chain = Chain(EmptyApplication())

        chain.commitToMemPool(tx0)
        chain.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.REPLACES)))
        chain.commitToMemPool(Transaction(tx2.content, Dependency(tx1.hash, DependencyType.SEQUENCE_PART)))
        chain.commitToMemPool(Transaction(tx3.content, Dependency(tx2.hash, DependencyType.SEQUENCE_END)))

        chain.performConsensusRound()
        assertEquals(4, chain.getPersistedTransactions().asSequence().toList().size)
        chain.performConsensusRound(true)
        assertEquals(1, chain.getPersistedTransactions().asSequence().toList().size)
    }

    @Test
    fun testBuildUponSequencePartWorks() {
        val chain = Chain(EmptyApplication())

        chain.commitToMemPool(tx0)
        chain.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.SEQUENCE_PART)))
        chain.commitToMemPool(Transaction(tx2.content, Dependency(tx1.hash, DependencyType.BUILDS_UPON)))
        chain.performConsensusRound()//otherwise order is undefined and tx3 might be evaluated before tx2
        chain.commitToMemPool(Transaction(tx3.content, Dependency(tx1.hash, DependencyType.SEQUENCE_END)))

        chain.performConsensusRound()
        assertEquals(4, chain.getPersistedTransactions().asSequence().toList().size)
        chain.performConsensusRound(true)
        assertEquals(2, chain.getPersistedTransactions().asSequence().toList().size)
    }

    @Test
    fun testSequencePartDependencyAlteredWorks() {
        val chain = Chain(EmptyApplication())

        chain.commitToMemPool(tx4)
        chain.commitToMemPool(Transaction(tx0.content, Dependency(tx4.hash, DependencyType.SEQUENCE_END)))
        chain.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.SEQUENCE_PART)))

        chain.performConsensusRound(true) //here for the next line to work tx1's dependency will need to be automatically altered so it points to the new tx0 hash

        chain.commitToMemPool(Transaction(tx3.content, Dependency(tx1.hash, DependencyType.SEQUENCE_END)))

        chain.performConsensusRound()
        assertEquals(3, chain.getPersistedTransactions().asSequence().toList().size)
        println("chain.getPersistedTransactions().asSequence().toList().map { it.hash } = ${chain.getPersistedTransactions().asSequence().toList().map { it.hash }}")
        println("chain.getPersistedTransactions().asSequence().toList() = ${chain.getPersistedTransactions().asSequence().toList()}")
        chain.performConsensusRound(true)
        println("chain.getPersistedTransactions().asSequence().toList().map { it.hash } = ${chain.getPersistedTransactions().asSequence().toList().map { it.hash }}")
        println("chain.getPersistedTransactions().asSequence().toList() = ${chain.getPersistedTransactions().asSequence().toList()}")
        assertEquals(1, chain.getPersistedTransactions().asSequence().toList().size)
    }

    @Test
    fun testBuildUponSequenceEndWorks() {
        val chain = Chain(EmptyApplication())

        chain.commitToMemPool(tx0)
        chain.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.SEQUENCE_PART)))
        chain.commitToMemPool(Transaction(tx2.content, Dependency(tx1.hash, DependencyType.SEQUENCE_END)))
        chain.commitToMemPool(Transaction(tx3.content, Dependency(tx2.hash, DependencyType.BUILDS_UPON)))


        chain.performConsensusRound()
        assertEquals(4, chain.getPersistedTransactions().asSequence().toList().size)
        chain.performConsensusRound(true)
        assertEquals(2, chain.getPersistedTransactions().asSequence().toList().size)
    }

    @Test
    fun testSequenceOfSequencesWorks() {
        val chain = Chain(EmptyApplication())

        chain.commitToMemPool(tx0)
        chain.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.SEQUENCE_PART)))
        chain.commitToMemPool(Transaction(tx2.content, Dependency(tx1.hash, DependencyType.SEQUENCE_END)))
        chain.commitToMemPool(Transaction(tx3.content, Dependency(tx2.hash, DependencyType.SEQUENCE_PART)))
        chain.commitToMemPool(Transaction(tx4.content, Dependency(tx3.hash, DependencyType.SEQUENCE_END)))


        chain.performConsensusRound()
        assertEquals(5, chain.getPersistedTransactions().asSequence().toList().size)
        chain.performConsensusRound(true)
        assertEquals(1, chain.getPersistedTransactions().asSequence().toList().size)
    }

    //: Build-upon without replace partial or replace, does not make sense - OR DOES IT?????
    //    if it does not- then it never makes sense: If one builds upon, then one can directly build the new tx - but that would go against the idea of transactions changing the state
    //    because then the new tx would have to directly encode the state - with build upon it can then incrementally take on the new state (in the example of calculator)



    @Test
    fun testDoubleReplaceSecondTxOnlyDenied() {
        val app = EmptyApplication()
        val chain = Chain(app)

        chain.commitToMemPool(tx0)
        chain.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.REPLACES)))
        chain.commitToMemPool(Transaction(tx2.content, Dependency(tx0.hash, DependencyType.REPLACES)))

        val (_, denied) = jokrey.mockchain.squash.findChangesAndDeniedTransactions(chain, app.getPartialReplaceSquashHandler(), app.getBuildUponSquashHandler(), app.getSequenceSquashHandler(), chain.getMemPoolContent())

        println("denied = ${denied}")

        assertEquals(1, denied.size)
        assertTrue(tx1.content.contentEquals(denied[0].first.content) ||  tx2.content.contentEquals(denied[0].first.content)) //order is not strictly defined
    }

    @Test
    fun unfoundDependencyRelationOnPartialDelayedSquash_noLongerFails() {
        val app = EmptyApplication()
        val chain = Chain(app)

        //this emulates a more efficient chain consensus-squash algorithm that is then shown to not work
        //    the real chain algorithm DOES NOT work this way - this is just supposed to show why


        chain.commitToMemPool(tx0)
        chain.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.REPLACES)))
        chain.performConsensusRound(false) //if the squash is forced here (which is it when we use verifySubset, then this functions perfectly

        chain.commitToMemPool(Transaction(tx2.content, Dependency(tx0.hash, DependencyType.REPLACES)))

        //if we used verifyAll here this would work (i.e. correctly deny tx1
        val (_, denied) = jokrey.mockchain.squash.findChangesAndDeniedTransactions(chain, app.getPartialReplaceSquashHandler(), app.getBuildUponSquashHandler(), app.getSequenceSquashHandler(), chain.getMemPoolContent())

        assertEquals(1, denied.size)
        assertEquals(Transaction(tx2.content, Dependency(tx0.hash, DependencyType.REPLACES)), denied[0].first)

        val state = jokrey.mockchain.squash.findChanges(chain, app.getPartialReplaceSquashHandler(), app.getBuildUponSquashHandler(), app.getSequenceSquashHandler(), null, chain.getMemPoolContent())
        assertTrue(state.deniedTransactions.isNotEmpty())

    }


    @Test
    fun unfoundDependencyRelationOnPartialDelayedSquashWithSequences_noLongerFails() {
        val app = EmptyApplication()
        var chain = Chain(app)

        chain.commitToMemPool(tx0)
        chain.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.SEQUENCE_PART)))
        chain.performConsensusRound(true) //no squash occurs

        chain.commitToMemPool(Transaction(tx2.content, Dependency(tx0.hash, DependencyType.SEQUENCE_END)))

        val (_, denied) = jokrey.mockchain.squash.findChangesAndDeniedTransactions(chain, app.getPartialReplaceSquashHandler(), app.getBuildUponSquashHandler(), app.getSequenceSquashHandler(), chain.getMemPoolContent())


        assertEquals(1, denied.size)
        assertEquals(Transaction(tx2.content, Dependency(tx0.hash, DependencyType.SEQUENCE_END)), denied[0].first)

        val state = jokrey.mockchain.squash.findChanges(chain, app.getPartialReplaceSquashHandler(), app.getBuildUponSquashHandler(), app.getSequenceSquashHandler(), null, chain.getMemPoolContent())
        assertTrue(state.deniedTransactions.isNotEmpty())




        chain = Chain(EmptyApplication())

        //this emulates a more efficient chain consensus-squash algorithm that is then shown to not work
        //    the real chain algorithm DOES NOT work this way - this is just supposed to show why


        chain.squashEveryNRounds = 1

        chain.commitToMemPool(tx0)
        chain.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.SEQUENCE_PART)))
        chain.performConsensusRound(true)

        assertEquals(2, chain.getPersistedTransactions().asSequence().toList().size)

        chain.commitToMemPool(Transaction(tx2.content, Dependency(tx0.hash, DependencyType.SEQUENCE_END)))

        chain.performConsensusRound(true)

        assertEquals(2, chain.getPersistedTransactions().asSequence().toList().size) //still correct, but now tx1(A PERSISTED TX) has a missing dependency

        println(chain.getPersistedTransactions().asSequence().toList().toList())
        chain.getPersistedTransactions().asSequence().toList().forEach { persistedTransaction ->
            persistedTransaction.bDependencies.forEach { dep ->
                if(!chain.contains(dep.txp))
                    println("tx($persistedTransaction)'s dependency(${dep.txp}) could not be found in the chain")
                assertTrue(chain.contains(dep.txp))
            }
        }
    }


    @Test
    fun canReuseDeletedTransactionsHash() {

        run {
            val chain = Chain(object : EmptyApplication() {
                override fun getBuildUponSquashHandler(): BuildUponSquashHandler {
                    return { _, _ -> byteArrayOf(1, 2, 3) }
                }
            })

            val tx0 = Transaction(byteArrayOf(1, 2, 3))
            val tx1 = Transaction(byteArrayOf(1, 2, 3, 6))
            val tx2 = Transaction(byteArrayOf(1, 2, 3, 7))

            chain.commitToMemPool(tx0)
            //tx0 is removed, tx1 becomes (1,2,3)
            chain.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.REPLACES), Dependency(tx0.hash, DependencyType.BUILDS_UPON))) //build upon lets tx1 change to tx0 content - but has previously replaced tx

            //tx1 becomes (1,2) - tx2 becomes (1,2,3)
            chain.commitToMemPool(Transaction(tx2.content, Dependency(tx1.hash, DependencyType.REPLACES_PARTIAL), Dependency(tx1.hash, DependencyType.BUILDS_UPON)))

            println("mem: "+chain.getMemPoolContent().map { it.hash })

            chain.performConsensusRound(true)

            println(chain.getPersistedTransactions().asSequence().toList().map { it.hash })
            println(chain.getPersistedTransactions().asSequence().toList())

            assertEquals(2, chain.getPersistedTransactions().asSequence().toList().size)
            assertTrue(chain.getPersistedTransactions().asSequence().toList().contains(Transaction(byteArrayOf(1, 2, 3))))
            assertTrue(chain.getPersistedTransactions().asSequence().toList().contains(Transaction(byteArrayOf(1, 2))))
        }


        run {
            val chain = Chain(object : EmptyApplication() {
                override fun getBuildUponSquashHandler(): BuildUponSquashHandler {
                    return { _, _ -> byteArrayOf(1, 2, 3) }
                }
            })

            val tx0 = Transaction(byteArrayOf(1, 2, 3))
            val tx1 = Transaction(byteArrayOf(1, 2, 3, 6))
            val tx2 = Transaction(byteArrayOf(1, 2, 3, 7))

            chain.commitToMemPool(tx0)
            chain.performConsensusRound(true)
            chain.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.REPLACES), Dependency(tx0.hash, DependencyType.BUILDS_UPON))) //build upon lets tx1 change to tx0 content - but has previously replaced tx

            chain.performConsensusRound(true)
            //logically the dependencies here are to tx1, but tx1 has since changed to tx0 - within the squash algorithm this is handled, but here we are the application and have to handle that ourselves
            chain.commitToMemPool(Transaction(tx2.content, Dependency(tx0.hash, DependencyType.REPLACES_PARTIAL), Dependency(tx0.hash, DependencyType.BUILDS_UPON)))
            chain.performConsensusRound(true)

            println(chain.getMemPoolContent().map { it.hash })


            println(chain.getPersistedTransactions().asSequence().toList().map { it.hash })
            println(chain.getPersistedTransactions().asSequence().toList())

            assertTrue(chain.getPersistedTransactions().asSequence().toList().size == 2)
            assertTrue(chain.getPersistedTransactions().asSequence().toList().contains(Transaction(byteArrayOf(1, 2, 3))))
            assertTrue(chain.getPersistedTransactions().asSequence().toList().contains(Transaction(byteArrayOf(1, 2))))
        }

    }

    @Test
    fun flipHashIsDenied() {
        //situation in which hashes become


        val chain = Chain(object : EmptyApplication() {
            override fun getPartialReplaceSquashHandler(): PartialReplaceSquashHandler {
                return { _, _ -> byteArrayOf(1, 2, 3) }
            }
            override fun getBuildUponSquashHandler(): BuildUponSquashHandler {
                return { _, _ -> byteArrayOf(1, 2, 3, 4) }
            }
        })

        val tx0 = Transaction(byteArrayOf(1, 2, 3, 4))
        val tx1 = Transaction(byteArrayOf(1, 2, 3),
                Dependency(tx0.hash, DependencyType.REPLACES_PARTIAL), //so tx0 becomes 1,2,3,4
                Dependency(tx0.hash, DependencyType.BUILDS_UPON))      //so tx1 becomes 1,2,3

        chain.commitToMemPool(tx0)
        chain.commitToMemPool(tx1) //build upon lets tx1 change to tx0 content - but has previously replaced tx

        println("mem: "+chain.getMemPoolContent().map { it.hash })

        chain.performConsensusRound()

        assertEquals(1, chain.getPersistedTransactions().asSequence().toList().size)
        assertArrayEquals(byteArrayOf(1,2,3,4), chain.getPersistedTransactions().asSequence().toList().toList()[0].content)
    }

    @Test
    fun flipHashIsDenied_Persistent() {
        //situation in which hashes become


        val chain = Chain(object : EmptyApplication() {
            override fun getPartialReplaceSquashHandler(): PartialReplaceSquashHandler {
                return { _, _ -> byteArrayOf(1, 2, 3) }
            }
            override fun getBuildUponSquashHandler(): BuildUponSquashHandler {
                return { _, _ -> byteArrayOf(1, 2, 3, 4) }
            }
        }, store = PersistentStorage(File(System.getProperty("user.home") + "/Desktop/blockStoreTest"), true))

        val tx0 = Transaction(byteArrayOf(1, 2, 3, 4))
        val tx1 = Transaction(byteArrayOf(1, 2, 3),
                Dependency(tx0.hash, DependencyType.REPLACES_PARTIAL), //so tx0 becomes 1,2,3,4
                Dependency(tx0.hash, DependencyType.BUILDS_UPON))      //so tx1 becomes 1,2,3

        chain.commitToMemPool(tx0)
        chain.commitToMemPool(tx1) //build upon lets tx1 change to tx0 content - but has previously replaced tx

        println("mem: "+chain.getMemPoolContent().map { it.hash })

        chain.performConsensusRound()

        assertEquals(1, chain.getPersistedTransactions().asSequence().toList().size)
        assertArrayEquals(byteArrayOf(1,2,3,4), chain.getPersistedTransactions().asSequence().toList()[0].content)
    }

    @Test
    fun canReuseHashThatHasJustBeenChangedWithinThisTransaction() {

        run {
            val chain = Chain(object : EmptyApplication() {
//                override fun getBuildUponSquashHandler(): BuildUponSquashHandler {
//                    return { _, _ -> byteArrayOf(1, 2, 3) }
//                }
//                override fun getPartialReplaceSquashHandler(): PartialReplaceSquashHandler {
//                    return { _, _ -> byteArrayOf(1, 2) }
//                }
            })

            val tx0 = Transaction(byteArrayOf(1, 2, 3))
            val tx1 = Transaction(byteArrayOf(1, 2, 3, 4))
            val tx2 = Transaction(byteArrayOf(1, 2, 3, 7))

            chain.commitToMemPool(tx0)
            chain.commitToMemPool(tx1)
            chain.performConsensusRound()

            //problem can (or did once) occur when replace-partial free'd a hash that is calculated by(the results of) a previous partial-replace
            //here: tx1(1,2,3,4) -> (1, 2, 3) and tx0(1,2,3) -> (1,2) (by partial)
            chain.commitToMemPool(Transaction(tx2.content, Dependency(tx1.hash, DependencyType.REPLACES_PARTIAL), Dependency(tx0.hash, DependencyType.REPLACES_PARTIAL)))

            println("mem: "+chain.getMemPoolContent().map { it.hash })

            chain.performConsensusRound(true)
            println("pers after: "+chain.getPersistedTransactions().asSequence().toList().map { it.hash })
            println("pers after: "+chain.getPersistedTransactions().asSequence().toList())

            assertEquals(3, chain.getPersistedTransactions().asSequence().toList().size)
            assertTrue(chain.getPersistedTransactions().asSequence().toList().map { it.content }.any { it.contentEquals(byteArrayOf(1,2,3))})
            assertTrue(chain.getPersistedTransactions().asSequence().toList().map { it.content }.any { it.contentEquals(byteArrayOf(1,2))})
            assertTrue(chain.getPersistedTransactions().asSequence().toList().map { it.content }.any { it.contentEquals(byteArrayOf(1,2, 3, 7))})


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
//        val chain = Chain(app)
//
//
//        chain.commitToMemPool(tx0)
//        chain.performConsensusRound(true)
//
//        chain.commitToMemPool(Transaction(tx2.content))
//        chain.commitToMemPool(Transaction(tx3.content, Dependency(tx2.hash, DependencyType.BUILDS_UPON), Dependency(tx2.hash, DependencyType.REPLACES_PARTIAL)))
//        chain.commitToMemPool(Transaction(tx4.content, Dependency(tx3.hash, DependencyType.BUILDS_UPON), Dependency(tx3.hash, DependencyType.REPLACES_PARTIAL)))
//
//        chain.commitToMemPool(Transaction(tx1.content, Dependency(tx0.hash, DependencyType.BUILDS_UPON), Dependency(tx0.hash, DependencyType.REPLACES_PARTIAL),
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