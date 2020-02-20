package jokrey.mockchain.consensus

import jokrey.mockchain.Mockchain
import jokrey.mockchain.visualization.InteractivelyCreatableClass

/**
 * Workaround to allow users to infuse their own consensus without themselves creating chain, mempool, etc. (i.e. their own instance).
 * @author jokrey
 */
interface ConsensusAlgorithmCreator : InteractivelyCreatableClass {
    fun create(instance: Mockchain) : ConsensusAlgorithm

    override fun getEqualFreshCreator() : ()-> ConsensusAlgorithmCreator
    override fun createNewInstance(vararg params: String) : ConsensusAlgorithmCreator
}